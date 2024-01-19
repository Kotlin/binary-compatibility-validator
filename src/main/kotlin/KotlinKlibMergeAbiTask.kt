/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.Target
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File

abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    private val targetToFile = mutableMapOf<String, File>()

    @get:Internal
    internal val projectName = project.name

    @get:Input
    val targets: Set<String>
        get() = targetToFile.keys

    @OutputFile
    lateinit var mergedFile: File

    @Input
    lateinit var dumpFileName: String

    fun addInput(target: String, file: File) {
        targetToFile[target] = file
    }

    @TaskAction
    fun merge() {
        val builder = KlibAbiDumpMerger()
        targets.forEach { targetName ->
            val target = Target(targetName)
            builder.addIndividualDump(target, targetToFile[targetName]!!.resolve(dumpFileName))
        }
        mergedFile.bufferedWriter().use { builder.dump(it) }
    }
}
