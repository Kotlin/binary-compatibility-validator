/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
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
    lateinit var targetInfoProvider: Provider<Map<String, Set<String>>>

    @TaskAction
    fun generate() {
        val target2outDir = collectOutputDirectories()
        val target2SourceSets = collectSourceSetsMapping(target2outDir)

        val thisSourceSets = target2SourceSets.remove(unsupportedTarget)!!
        val matchingTargets = target2SourceSets.filter {
            it.value == thisSourceSets
        }.keys
        val matchingTarget = if (matchingTargets.isEmpty()) {
            if (!allowInexactDumpSubstitution) {
                throw IllegalStateException("There are no targets sharing the same source sets with ${unsupportedTarget}.")
            }
            target2SourceSets.mapValues { it.value.intersect(thisSourceSets).size }.toList()
                .sortedByDescending { it.second }.map { it.first }.first()
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
        logger.warn("An ABI dump for target $unsupportedTarget was copied from the target $matchingTarget " +
                "as the former target is not supported by the host compiler. Copied dump may not reflect actual ABI " +
                "for the target $unsupportedTarget. It is recommended to regenerate the dump on the host supporting " +
                "all required compilation target.")
    }

    private fun collectSourceSetsMapping(target2outDir: MutableMap<String, File>): MutableMap<String, Set<String>> {
        val targetToSourceSets = mutableMapOf<String, Set<String>>()
        targetInfoProvider.get().forEach { targetName, allSourceSets ->
            if (target2outDir.containsKey(targetName) || targetName == unsupportedTarget) {
                targetToSourceSets[targetName] = allSourceSets
            }
        }
        return targetToSourceSets
    }

    private fun collectOutputDirectories(): MutableMap<String, File> {
        val target2outDir = mutableMapOf<String, File>()
        targetInfoProvider.get().keys.forEach {
            // TODO: fix the path
            target2outDir[it] = File(File(outputApiDir.parentFile.parentFile, it), it)
        }
        return target2outDir
    }
}
