/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.utils.keysToMap
import java.io.File
import java.nio.file.*

/**
 * Task copies the ABI dump generated for one of the compilation targets supported by the host compiler
 * into the directory for (supposedly unsupported) target [unsupportedTarget].
 *
 * A dump made for some supported target could be used as a substitution for [unsupportedTarget]'s dump if
 * both targets have the same non-empty source sets (i.e. source sets consisting of at least one file).
 */
abstract class KotlinKlibReuseSharedAbiTask : DefaultTask() {
    @get:Internal
    internal val projectName = project.name

    @Input
    var allowInexactDumpSubstitution: Boolean = false

    @Input
    lateinit var unsupportedTarget: String

    @OutputDirectory
    lateinit var outputApiDir: File

    @Input
    lateinit var targetSourcesProvider: Provider<Map<String, FileCollection>>

    @TaskAction
    fun generate() {
        val target2SourceSets = targetSourcesProvider.get().asSequence().map {
            it.key to it.value.files.asSequence().map { it.absolutePath }.toSet()
        }.toMap(mutableMapOf())
        val target2outDir = target2SourceSets.keys.keysToMap {
            outputApiDir.parentFile.resolve(it)
        }

        val thisSourceSets = target2SourceSets.remove(unsupportedTarget)!!
        val matchingTargets = target2SourceSets.filter {
            it.value == thisSourceSets
        }.keys
        val matchingTarget = if (matchingTargets.isEmpty()) {
            if (!allowInexactDumpSubstitution) {
                throw IllegalStateException(
                    "There are no targets sharing the same source sets with $unsupportedTarget."
                )
            }
            val similarTargets = target2SourceSets.mapValues { it.value.intersect(thisSourceSets).size }.toList()
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                    .thenComparing { it: Pair<String, Int> -> it.first })
                .map { it.first }

            if (similarTargets.isEmpty()) {
                throw IllegalStateException(
                    "The target $unsupportedTarget is not supported by the host compiler " +
                            "and there are no other enabled targets to steal a dump form."
                )
            }

            similarTargets.first()
        } else {
            logger.info(
                "Following compilation targets supported by the host compiler have the same source sets " +
                        "as the $unsupportedTarget target: [${matchingTargets.joinToString(", ")}]"
            )
            matchingTargets.first()
        }
        logger.info(
            "Using a dump for the target $matchingTarget as a dump for unsupported " +
                    "compilation target $unsupportedTarget"
        )

        val srcDir = target2outDir[matchingTarget]!!
        val fileName = "$projectName.abi"
        Files.copy(
            srcDir.resolve(fileName).toPath(), outputApiDir.resolve(fileName).toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES
        )
        logger.warn(
            "An ABI dump for target $unsupportedTarget was copied from the target $matchingTarget " +
                    "as the former target is not supported by the host compiler. Copied dump may not reflect actual ABI " +
                    "for the target $unsupportedTarget. It is recommended to regenerate the dump on the host supporting " +
                    "all required compilation target."
        )
    }
}
