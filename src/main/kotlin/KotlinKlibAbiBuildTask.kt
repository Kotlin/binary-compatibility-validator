/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KLibDumpFilters
import kotlinx.validation.api.klib.SignatureVersion
import kotlinx.validation.api.klib.dumpKlib
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.abi.*

/**
 * Generates a text file with a KLib ABI dump for a single klib.
 */
public abstract class KotlinKlibAbiBuildTask : BuildTaskBase() {

    /**
     * Path to a klib to dump.
     */
    @InputFiles
    public lateinit var klibFile: FileCollection

    /**
     * Bind this task with a klib compilation.
     */
    @InputFiles
    public lateinit var compilationDependencies: FileCollection

    /**
     * Refer to [KlibValidationSettings.signatureVersion] for details.
     */
    @Optional
    @get:Input
    public var signatureVersion: Int? = null

    /**
     * Name of a target [klibFile] was compiled for.
     */
    @Input
    public lateinit var target: String

    @ExperimentalStdlibApi
    @ExperimentalLibraryAbiReader
    @TaskAction
    internal fun generate() {
        outputApiFile.delete()
        outputApiFile.parentFile.mkdirs()

        klibFile.singleFile.dumpKlib(outputApiFile, KLibDumpFilters {
            ignoredClasses.addAll(this@KotlinKlibAbiBuildTask.ignoredClasses)
            ignoredPackages.addAll(this@KotlinKlibAbiBuildTask.ignoredPackages)
            nonPublicMarkers.addAll(this@KotlinKlibAbiBuildTask.nonPublicMarkers)

            signatureVersion = when (val ver = this@KotlinKlibAbiBuildTask.signatureVersion) {
                null -> SignatureVersion.LATEST
                else -> SignatureVersion(ver)
            }
        })
    }
}
