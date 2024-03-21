/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KlibDump
import kotlinx.validation.api.klib.saveTo
import org.gradle.api.DefaultTask
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.*
import java.io.File

/**
 * Merges multiple individual KLib ABI dumps into a single merged dump.
 */
internal abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    private val targetToFile = mutableMapOf<String, File>()

    @get:Internal
    internal val projectName = project.name

    /**
     * Set of targets whose dumps should be merged.
     */
    @get:Input
    val targets: Set<String>
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
    lateinit var mergedFile: File

    /**
     * The name of a dump file.
     */
    @Input
    lateinit var dumpFileName: String

    private var targetPredicate: Spec<String> = Spec<String> { true }

    internal fun addInput(target: String, file: File) {
        targetToFile[target] = file
    }

    internal fun filterTargets(filter: Spec<String>) {
        targetPredicate = filter
    }

    @OptIn(ExperimentalBCVApi::class)
    @TaskAction
    internal fun merge() {
        val filter = targetPredicate
        KlibDump().apply {
            targetToFile.forEach { (targetName, dumpDir) ->
                if (filter.isSatisfiedBy(targetName)) {
                    merge(dumpDir.resolve(dumpFileName), targetName)
                }
            }
        }.saveTo(mergedFile)
    }
}
