/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.abi.*

abstract class KotlinKlibAbiBuildTask : BuildTaskBase() {

    @InputFiles
    lateinit var klibFile: FileCollection

    @InputFiles
    lateinit var compilationDependencies: FileCollection

    @Optional
    @get:Input
    var signatureVersion: Int? = null

    @Input
    lateinit var target: String

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
                    generateQualifiedNames(it)
                }))
            }
            if (nonPublicMarkers.isNotEmpty()) {
                add(AbiReadingFilter.NonPublicMarkerAnnotations(nonPublicMarkers.flatMap {
                    generateQualifiedNames(it)
                }))
            }
        }

        val parsedAbi = try {
            LibraryAbiReader.readAbiInfo(klibFile.singleFile, filters)
        } catch (e: Exception) {
            throw IllegalStateException("Can't read a KLIB: ${klibFile.singleFile}", e)
        }

        val supportedVersions = parsedAbi.signatureVersions.asSequence()
        val sigVersion = if (signatureVersion != null) {
            val versionNumbers = supportedVersions.map { it.versionNumber }.toSortedSet()
            if (signatureVersion !in versionNumbers) {
                throw IllegalArgumentException(
                    "Unsupported signature version '$signatureVersion'. " +
                            "Supported versions are: $versionNumbers"
                )
            }
            AbiSignatureVersion.resolveByVersionNumber(signatureVersion!!)
        } else {
            supportedVersions.maxByOrNull(AbiSignatureVersion::versionNumber)
                ?: throw IllegalStateException("Can't choose abiSignatureVersion")
        }

        outputApiDir.resolve("$projectName.abi").bufferedWriter().use {
            LibraryAbiRenderer.render(parsedAbi, it, AbiRenderingSettings(sigVersion))
        }
    }
}

@ExperimentalStdlibApi
@ExperimentalLibraryAbiReader
internal fun generateQualifiedNames(name: String): List<AbiQualifiedName> {
    if (!name.contains('.')) {
        return listOf(AbiQualifiedName(AbiCompoundName(""), AbiCompoundName(name)))
    }
    val parts = name.split('.')
    return buildList {
        for (packageLength in parts.indices) {
            val packageName = AbiCompoundName(parts.subList(0, packageLength).joinToString("."))
            val className = AbiCompoundName(parts.subList(packageLength, parts.size).joinToString("."))
            add(AbiQualifiedName(packageName, className))
        }
    }
}
