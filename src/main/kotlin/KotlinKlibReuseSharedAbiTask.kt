/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

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
    lateinit var unsupportedTarget: String

    @OutputDirectory
    lateinit var outputApiDir: File

    @TaskAction
    fun generate() {
        val target2outDir = collectOutputDirectories()
        val target2SourceSets = collectSourceSetsMapping(target2outDir)

        val thisSourceSets = target2SourceSets.remove(unsupportedTarget)!!
        val matchingTargets = target2SourceSets.filter {
            it.value == thisSourceSets
        }.keys
        val matchingTarget = if (matchingTargets.isEmpty()) {
            if (!project.apiValidationExtensionOrNull!!.klib.allowInexactDumpSubstitution) {
                throw IllegalStateException("There are no targets sharing the same source sets with ${unsupportedTarget}.")
            }
            target2SourceSets.mapValues { it.value.intersect(thisSourceSets).size }.toList()
                .sortedByDescending { it.second }.map { it.first }.first()
        } else {
            project.logger.info(
                "Following compilation targets supported by the host compiler have the same source sets " +
                        "as the $unsupportedTarget target: [${matchingTargets.joinToString(", ")}]"
            )
            matchingTargets.first()
        }
        project.logger.info(
            "Using a dump for the target $matchingTarget as a dump for unsupported " +
                    "compilation target $unsupportedTarget"
        )

        val srcDir = target2outDir[matchingTarget]!!
        val fileName = "$projectName.abi"
        Files.copy(
            srcDir.resolve(fileName).toPath(), outputApiDir.resolve(fileName).toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES
        )
        project.logger.warn("An ABI dump for target $unsupportedTarget was copied from the target $matchingTarget " +
                "as the former target is not supported by the host compiler. Copied dump may not reflect actual ABI " +
                "for the target $unsupportedTarget. It is recommended to regenerate the dump on the host supporting " +
                "all required compilation target.")
    }

    private fun collectSourceSetsMapping(target2outDir: MutableMap<String, File>): MutableMap<String, Set<String>> {
        val targetToSourceSets = mutableMapOf<String, Set<String>>()
        project.kotlinExtension.targets.forEach {
            if (target2outDir.containsKey(it.name) || it.name == unsupportedTarget) {
                val sourceSets = mutableSetOf<String>()
                it.compilations.first { it.name == "main" }.allKotlinSourceSets.forEach {
                    it.collectNonEmpty(sourceSets)
                }
                targetToSourceSets[it.name] = sourceSets
            }
        }
        return targetToSourceSets
    }

    private fun collectOutputDirectories(): MutableMap<String, File> {
        val target2outDir = mutableMapOf<String, File>()
        this.dependsOn.forEach {
            if (it is TaskCollection<*>) {
                it.forEach { task ->
                    if (task is KotlinKlibAbiBuildTask) {
                        target2outDir[task.target] = task.outputApiDir
                    }
                }
            }
        }
        return target2outDir
    }
}

private fun KotlinSourceSet.collectNonEmpty(dst: MutableSet<String>) {
    val isNotEmpty = this.kotlin.srcDirs.any {
        if (!it.exists()) {
            return@any false
        }
        var hasFiles = false
        Files.walkFileTree(it.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                hasFiles = true
                return FileVisitResult.TERMINATE
            }
        })
        hasFiles
    }
    if (isNotEmpty) {
        dst.add(this.name)
    }
    dependsOn.forEach {
        it.collectNonEmpty(dst)
    }
}
