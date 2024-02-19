/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KLibTarget
import kotlinx.validation.api.klib.MergedKLibDumpFormat
import kotlinx.validation.api.klib.MergedKlibDump
import kotlinx.validation.api.klib.dumpTo
import kotlinx.validation.klib.TargetHierarchy
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File

/**
 * Merges multiple individual KLib ABI dumps into a single merged dump.
 */
public abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    private val targetToFile = mutableMapOf<String, File>()

    @get:Internal
    internal val projectName = project.name

    /**
     * Set of targets whose dumps should be merged.
     */
    @get:Input
    public val targets: Set<String>
        get() = targetToFile.keys

    // Required to enforce task rerun on klibs update
    @Suppress("UNUSED")
    @get:InputFiles
    internal val inputDumps: Collection<File>
        get() = targetToFile.values

    /**
     * A path to a resulting merged dump.
     */
    @OutputFile
    public lateinit var mergedFile: File

    /**
     * The name of a dump file.
     */
    @Input
    public lateinit var dumpFileName: String

    /**
     * Refer to [KlibValidationSettings.useTargetGroupAliases] for details.
     */
    @Input
    public var groupTargetNames: Boolean = true

    internal fun addInput(target: String, file: File) {
        targetToFile[target] = file
    }

    @TaskAction
    internal fun merge() {
        MergedKlibDump().also {
            targets.forEach { targetName ->
                it.load(KLibTarget(targetName), targetToFile[targetName]!!.resolve(dumpFileName))
            }
        }.dumpTo(mergedFile, MergedKLibDumpFormat {
            groupTargetNames = canUseGroupAliases()
        })
    }

    private fun canUseGroupAliases(): Boolean {
        if (!groupTargetNames) return false
        val clashingTargets = targets.intersect(TargetHierarchy.nonLeafTargets())
        return clashingTargets.isEmpty()
    }
}
