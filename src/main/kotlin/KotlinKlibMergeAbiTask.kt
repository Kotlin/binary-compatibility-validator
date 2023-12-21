/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.Target
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File

abstract class KotlinKlibMergeAbiTask : DefaultTask() {
    private val targetToFile = mutableMapOf<String, File>()

    @get:Internal
    internal val projectName = project.name

    @get:InputFiles
    val inputFiles: Collection<File>
        get() = targetToFile.values

    @get:Input
    val targets: Set<String>
        get() = targetToFile.keys

    @OutputDirectory
    lateinit var mergedFile: File

    @InputFiles
    lateinit var inputImageDir: Provider<File>

    @Input
    var updateImage: Boolean = true

    fun addInput(target: String, file: File) {
        targetToFile[target] = file
    }

    @TaskAction
    fun merge() {
        val filename = "$projectName.abi"
        val builder = KlibAbiDumpMerger()
        if (updateImage) {
            val inputImage = inputImageDir.get().resolve(filename)
            if (inputImage.exists()) {
                if (inputImage.length() == 0L) {
                    logger.warn("merged dump file is empty: $inputImage")
                } else {
                    builder.loadMergedDump(inputImage)
                }
            }
        }

        targets.forEach { targetName ->
            val target = Target(targetName)
            if (updateImage) {
                builder.remove(target)
            }
            builder.addIndividualDump(target, targetToFile[targetName]!!.resolve(filename))
        }
        mergedFile.resolve(filename).bufferedWriter().use { builder.dump(it) }
    }
}
