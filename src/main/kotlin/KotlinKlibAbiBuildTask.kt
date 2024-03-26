/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KLibDumpFilters
import kotlinx.validation.api.klib.KlibDump
import kotlinx.validation.api.klib.KlibSignatureVersion
import kotlinx.validation.api.klib.saveTo
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

/**
 * Generates a text file with a KLib ABI dump for a single klib.
 */
internal abstract class KotlinKlibAbiBuildTask : BuildTaskBase() {

    /**
     * Collection consisting of a single path to compiled klib (either file, or directory).
     */
    @get:InputFiles
    val klibFile: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * Refer to [KlibValidationSettings.signatureVersion] for details.
     */
    @get:Input
    var signatureVersion: KlibSignatureVersion = KlibSignatureVersion.LATEST

    /**
     * Name of a target [klibFile] was compiled for.
     */
    @Input
    lateinit var target: String

    @OptIn(ExperimentalBCVApi::class)
    @TaskAction
    internal fun generate() {
        outputApiFile.delete()
        outputApiFile.parentFile.mkdirs()

        val dump = KlibDump.fromKlib(klibFile.singleFile, target, KLibDumpFilters {
            ignoredClasses.addAll(this@KotlinKlibAbiBuildTask.ignoredClasses)
            ignoredPackages.addAll(this@KotlinKlibAbiBuildTask.ignoredPackages)
            nonPublicMarkers.addAll(this@KotlinKlibAbiBuildTask.nonPublicMarkers)
            signatureVersion = this@KotlinKlibAbiBuildTask.signatureVersion
        })

        dump.saveTo(outputApiFile)
    }
}
