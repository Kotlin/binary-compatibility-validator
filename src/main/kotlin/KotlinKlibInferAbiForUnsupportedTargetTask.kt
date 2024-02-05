/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpFormat
import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.Target
import kotlinx.validation.klib.TargetHierarchy
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.utils.keysToMap
import java.io.File

/**
 * Task infers a possible KLib ABI dump for an unsupported target.
 * To infer a dump, tasks walk up the default targets hierarchy tree starting from the unsupported
 * target until it finds a node corresponding to a group of targets having at least one supported target.
 * After that, dumps generated for such supported targets are merged and declarations that are common to all
 * of them are considered as a common ABI that most likely will be shared by the unsupported target.
 * At the next step, if a project contains an old dump, declarations specific to the unsupported target are copied
 * from it and merged into the common ABI extracted previously.
 * The resulting dump is then used as an inferred dump for the unsupported target.
 */
public abstract class KotlinKlibInferAbiForUnsupportedTargetTask : DefaultTask() {
    @get:Internal
    internal val projectName = project.name

    /**
     * The name of a target to infer a dump for.
     */
    @Input
    public lateinit var unsupportedTarget: String

    /**
     * A root directory containing dumps successfully generated for each supported target.
     * It is assumed that this directory contains subdirectories named after targets.
     */
    @InputFiles
    public lateinit var outputApiDir: String

    /**
     * Set of all supported targets.
     */
    @Input
    public lateinit var supportedTargets: Set<String>

    /**
     * Previously generated merged ABI dump file, the golden image every dump should be verified against.
     */
    @InputFiles
    public lateinit var inputImageFile: File

    /**
     * The name of a dump file.
     */
    @Input
    public lateinit var dumpFileName: String

    /**
     * A path to an inferred dump file.
     */
    @OutputFile
    public lateinit var outputFile: File

    @TaskAction
    internal fun generate() {
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
        commonDump.retainCommonAbi()

        // load and old dump (that may contain the dump for the unsupported target) and remove all but the declarations
        // specific to the unsupported target
        val image = KlibAbiDumpMerger()
        if (inputImageFile.exists()) {
            if (inputImageFile.length() > 0L) {
                image.loadMergedDump(inputImageFile)
                image.retainTargetSpecificAbi(Target(unsupportedTarget))
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
            commonDump.dump(it, KlibAbiDumpFormat(includeTargets = false))
        }

        logger.warn(
            "An ABI dump for target $unsupportedTarget was inferred from the ABI generated for the following targets " +
                    "as the former target is not supported by the host compiler: " +
                    "[${matchingTargets.joinToString(",")}]. " +
                    "Inferred dump may not reflect an actual ABI for the target $unsupportedTarget. " +
                    "It is recommended to regenerate the dump on the host supporting all required compilation target."
        )
    }

    private fun findMatchingTargets(): Set<String> {
        var currentGroup: String? = unsupportedTarget
        while (currentGroup != null) {
            // If a current group has some supported targets, use them.
            val groupTargets = TargetHierarchy.targets(currentGroup).intersect(supportedTargets)
            if (groupTargets.isNotEmpty()) {
                return groupTargets
            }
            // Otherwise, walk up the target hierarchy.
            currentGroup = TargetHierarchy.parent(currentGroup)
        }
        throw IllegalStateException(
            "The target $unsupportedTarget is not supported by the host compiler " +
                    "and there are no targets similar to $unsupportedTarget to infer a dump from it."
        )
    }
}
