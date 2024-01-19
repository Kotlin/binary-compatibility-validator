/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.Target
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import org.jetbrains.kotlin.utils.keysToMap
import java.io.File

private val targetsHierarchy: Map<String, String> = mutableMapOf(
    "js" to "common",
    "native" to "common",

    "androidNative" to "native",
    "apple" to "native",
    "mingw" to "native",
    "linux" to "native",

    "androidNativeX64" to "androidNative",
    "androidNativeX86" to "androidNative",
    "androidNativeArm32" to "androidNative",
    "androidNativeArm64" to "androidNative",

    "ios" to "apple",
    "macos" to "apple",
    "tvos" to "apple",
    "watchos" to "apple",

    "iosX64" to "ios",
    "iosArm64" to "ios",
    "iosSimulatorArm64" to "ios",

    "macosX64" to "macos",
    "macosArm64" to "macos",

    "tvosX64" to "tvos",
    "tvosArm64" to "tvos",
    "tvosSimulatorArm64" to "tvos",

    "watchosX64" to "watchos",
    "watchosArm32" to "watchos",
    "watchosArm64" to "watchos",
    "watchosSimulatorArm64" to "watchos",
    "watchosDeviceArm64" to "watchos",

    "mingwX64" to "mingw",

    "linuxX64" to "linux",
    "linuxArm64" to "linux"
)

/**
 * Task copies the ABI dump generated for one of the compilation targets supported by the host compiler
 * into the directory for (supposedly unsupported) target [unsupportedTarget].
 *
 * A dump made for some supported target could be used as a substitution for [unsupportedTarget]'s dump if
 * both targets have the same non-empty source sets (i.e. source sets consisting of at least one file).
 */
abstract class KotlinKlibInferAbiForUnsupportedTargetTask : DefaultTask() {
    @get:Internal
    internal val projectName = project.name

    @Input
    lateinit var unsupportedTarget: String

    @InputFiles
    lateinit var outputApiDir: String

    @Input
    lateinit var supportedTargets: Set<String>

    @InputFiles
    lateinit var inputImageFile: File

    @Input
    lateinit var dumpFileName: String

    @OutputFile
    lateinit var outputFile: File

    @TaskAction
    fun generate() {
        // find a set of supported targets that are closer to unsupported target in the hierarchy
        val matchingTargets = findMatchingTargets()
        val target2outFile = supportedTargets.keysToMap {
            File(outputApiDir).parentFile.resolve(it).resolve(dumpFileName)
        }

        // given a set of similar targets, combine their ABI files into a single merged dump and consider it
        // a common ABI that should be shared by the unsupported target as well
        val commonDump = KlibAbiDumpMerger()
        for (target in matchingTargets) {
            commonDump.addIndividualDump(Target(target), target2outFile[target]!!)
        }
        commonDump.retainCommon()

        // load and old dump (that may contain the dump for the unsupported target) and remove all but the declarations
        // specific to the unsupported target
        val image = KlibAbiDumpMerger()
        if (inputImageFile.exists()) {
            if (inputImageFile.length() > 0L) {
                image.loadMergedDump(inputImageFile)
                image.retainSpecific(Target(unsupportedTarget))
                // merge common ABI with target-specific ABI
                commonDump.mergeTargetSpecific(image)
            } else {
                logger.warn(
                    "Project's ABI file exists, but empty: $inputImageFile. " +
                            "The file will be ignored during ABI dump inference for the unsupported target " +
                            unsupportedTarget
                )
            }
        }
        commonDump.overrideTargets(setOf(Target(unsupportedTarget)))

        outputFile.bufferedWriter().use {
            commonDump.dump(it, false)
        }

        logger.warn(
            "An ABI dump for target $unsupportedTarget was inferred from the ABI generated for target " +
                    "[${matchingTargets.joinToString(",")}] " +
                    "as the former target is not supported by the host compiler. " +
                    "Inferred dump may not reflect actual ABI for the target $unsupportedTarget. " +
                    "It is recommended to regenerate the dump on the host supporting all required compilation target."
        )
    }

    // TODO: test
    private fun findMatchingTargets(): Set<String> {
        var currentGroup = unsupportedTarget
        var groupMembers = setOf(unsupportedTarget)
        while (true) {
            val targets = groupMembers.intersect(supportedTargets)
            if (targets.isNotEmpty()) {
                return targets
            }
            currentGroup = targetsHierarchy[currentGroup] ?: throw IllegalStateException(
                "The target $unsupportedTarget is not supported by the host compiler " +
                        "and there are no targets similar to linuxArm64 to infer a dump from it."
            )
            groupMembers = collectGroupMembers(currentGroup)
        }
    }

    private fun collectGroupMembers(groupName: String): Set<String> {
        val result = mutableSetOf<String>()
        val q = mutableListOf(groupName)
        while (q.isNotEmpty()) {
            val g = q.popLast()
            val members = targetsHierarchy.filter { it.value == g }.keys
            result.addAll(members)
            q.addAll(members)
        }
        return result
    }
}
