/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpFormat
import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.TargetHierarchy
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File

/**
 * Extracts dump for targets supported by the host compiler from a merged API dump stored in a project.
 */
internal abstract class KotlinKlibExtractSupportedTargetsAbiTask : DefaultTask() {
    @get:Internal
    internal val projectName = project.name

    /**
     * Merged KLib dump that should be filtered by this task.
     */
    @InputFiles
    lateinit var inputAbiFile: File

    /**
     * A path to the resulting dump file.
     */
    @OutputFile
    lateinit var outputAbiFile: File

    /**
     * Provider returning targets supported by the host compiler.
     */
    @get:Input
    lateinit var targets: Provider<Set<String>>

    /**
     * Refer to [KlibValidationSettings.strictValidation] for details.
     */
    @Input
    var strictValidation: Boolean = false

    /**
     * Refer to [KlibValidationSettings.useTargetGroupAliases] for details.
     */
    @Input
    var groupTargetNames: Boolean = true

    @TaskAction
    internal fun generate() {
        if (inputAbiFile.length() == 0L) {
            error("Project ABI file $inputAbiFile is empty.")
        }
        val dump = KlibAbiDumpMerger().apply { loadMergedDump(inputAbiFile) }
        val enabledTargets = targets.get()
        val targetsToRemove = dump.targets.filter { it.name !in enabledTargets }
        if (targetsToRemove.isNotEmpty() && strictValidation) {
            throw IllegalStateException(
                "Validation could not be performed as some targets are not available " +
                        "and the strictValidation mode was enabled."
            )
        }
        for (target in targetsToRemove) {
            dump.remove(target)
        }
        outputAbiFile.bufferedWriter().use { dump.dump(it, KlibAbiDumpFormat(useGroupAliases = canUseGroupAliases())) }
    }

    private fun canUseGroupAliases(): Boolean {
        return groupTargetNames
    }
}
