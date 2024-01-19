/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpMerger
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File

abstract class KotlinKlibExtractSupportedTargetsAbiTask : DefaultTask() {
    @get:Internal
    internal val projectName = project.name

    @InputFile
    lateinit var inputAbiFile: File

    @OutputFile
    lateinit var outputAbiFile: File

    @get:Input
    lateinit var targets: Provider<Set<String>>

    @Input
    var strictValidation: Boolean = false

    @TaskAction
    fun generate() {
        val dump = KlibAbiDumpMerger().apply { loadMergedDump(inputAbiFile) }
        val enabledTargets = targets.get()
        val targetsToRemove = dump.targets.filter { it.name !in enabledTargets }
        if (targetsToRemove.isNotEmpty() && strictValidation) {
            throw IllegalStateException(
                "Validation could not be performed as some targets are not available " +
                        "and the strictValidation mode was enabled"
            )
        }
        for (target in targetsToRemove) {
            dump.remove(target)
        }
        outputAbiFile.bufferedWriter().use { dump.dump(it) }
    }
}
