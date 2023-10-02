/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.abi.*

abstract class KotlinKlibAbiBuildTask constructor(

) : BuildTaskBase() {

    @get:InputFiles
    abstract val klibFile: ConfigurableFileCollection

    // TODO: configure
    @Optional
    @get:Input
    var signatureVersion: Int? = null

    @ExperimentalStdlibApi
    @ExperimentalLibraryAbiReader
    @TaskAction
    fun generate() {
        outputApiDir.deleteRecursively()
        outputApiDir.mkdirs()

        val filters = buildList {
            if (ignoredPackages.isNotEmpty()) {
                add(AbiReadingFilter.ExcludedPackages(ignoredPackages.map { AbiCompoundName(it) }))
            }
            if (ignoredClasses.isNotEmpty()) {
                add(AbiReadingFilter.ExcludedClasses(ignoredClasses.flatMap {
                    generateQualifiedName(it)
                }))
            }
            if (nonPublicMarkers.isNotEmpty()) {
                add(AbiReadingFilter.NonPublicMarkerAnnotations(nonPublicMarkers.flatMap {
                    generateQualifiedName(it)
                }))
            }
        }

        // Ooops, publicPackages, publicClasses and publicMarkers are not supported

        val parsedAbi = LibraryAbiReader.readAbiInfo(klibFile.singleFile, filters)

        val sigVersion = if (signatureVersion != null) {
            AbiSignatureVersion.resolveByVersionNumber(signatureVersion!!)
        } else {
            parsedAbi.signatureVersions.maxByOrNull(AbiSignatureVersion::versionNumber)
                ?: throw IllegalStateException("Can't choose abiSignatureVersion")
        }

        outputApiDir.resolve("$projectName.api").bufferedWriter().use {
            LibraryAbiRenderer.render(parsedAbi, it, AbiRenderingSettings(sigVersion))
        }
    }

    @ExperimentalStdlibApi
    @ExperimentalLibraryAbiReader
    private fun generateQualifiedName(name: String) : List<AbiQualifiedName> {
        if (!name.contains('.')) {
            return listOf(AbiQualifiedName(AbiCompoundName(""), AbiCompoundName(name)))
        }
        val parts = name.split('.')
        return buildList {
            for (packageLength in 0 until parts.size - 1) {
                val packageName = AbiCompoundName(parts.subList(0, packageLength).joinToString("."))
                val className = AbiCompoundName(parts.subList(packageLength, parts.size).joinToString("."))
                add(AbiQualifiedName(packageName, className))
            }
        }
    }
}
