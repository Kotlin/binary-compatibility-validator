/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KlibDump
import kotlinx.validation.api.klib.saveTo
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Merges multiple individual KLib ABI dumps into a single merged dump.
 */
internal abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    @get:Nested
    val dumps: ListProperty<GeneratedDump> = project.objects.listProperty(GeneratedDump::class.java)

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


    @OptIn(ExperimentalBCVApi::class)
    @TaskAction
    internal fun merge() {
        KlibDump().apply {
            dumps.get().forEach { dump ->
                val dumpFile = dump.dumpFile.asFile.get()
                if (dumpFile.exists()) {
                    merge(dumpFile, dump.target.configurableName)
                }
            }
        }.saveTo(mergedFile)
    }
}
