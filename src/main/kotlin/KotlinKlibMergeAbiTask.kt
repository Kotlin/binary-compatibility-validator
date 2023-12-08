/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibDumpFileBuilder
import kotlinx.validation.klib.LinesProvider
import kotlinx.validation.klib.Target
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    private val targetToFile_ = mutableMapOf<String, File>()

    @get:Internal
    internal val projectName = project.name

    @get:InputFiles
    val inputFiles: Collection<File>
        get() = targetToFile_.values

    @get:Input
    val targets: Set<String>
        get() = targetToFile_.keys

    @OutputDirectory
    lateinit var mergedFile: File

    @InputFiles
    lateinit var inputImageDir: File

    @Input
    var updateImage: Boolean = true

    fun addInput(target: String, file: File) {
        targetToFile_[target] = file
    }

    @TaskAction
    fun merge() {
        val filename = "$projectName.abi"
        val builder = KlibDumpFileBuilder()
        if (updateImage) {
            val inputImage = inputImageDir.resolve(filename)
            if (inputImage.exists()) {
                Files.lines(inputImage.toPath()).use {
                    builder.mergeFile(emptySet(), LinesProvider(it.iterator()))
                }
            }
        }

        targets.forEach { targetName ->
            val target = Target(targetName)
            if (updateImage) {
                builder.remove(target)
            }
            Files.lines(targetToFile_[targetName]!!.resolve(filename).toPath()).use {
                builder.mergeFile(setOf(target), LinesProvider(it.iterator()))
            }
        }
        FileWriter(mergedFile.resolve(filename)).use { builder.dump(it) }
    }
}
