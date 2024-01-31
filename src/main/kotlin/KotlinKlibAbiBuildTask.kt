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

/**
 * Generates a text file with a ABI dump for a single klib.
 */
public abstract class KotlinKlibAbiBuildTask : BuildTaskBase() {

    @InputFiles
    public lateinit var klibFile: FileCollection

    @InputFiles
    public lateinit var compilationDependencies: FileCollection

    @Optional
    @get:Input
    public var signatureVersion: Int? = null

    @Input
    public lateinit var target: String

    @ExperimentalStdlibApi
    @ExperimentalLibraryAbiReader
    @TaskAction
    internal fun generate() {
        outputApiFile.delete()
        outputApiFile.parentFile.mkdirs()

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
            throw IllegalStateException("Can't read a KLib: ${klibFile.singleFile}", e)
        }

        val supportedVersions = parsedAbi.signatureVersions.asSequence()
        val sigVersion = if (signatureVersion != null) {
            val versionNumbers = supportedVersions.map { it.versionNumber }.toSortedSet()
            if (signatureVersion !in versionNumbers) {
                throw IllegalArgumentException(
                    "Unsupported KLib signature version '$signatureVersion'. " +
                            "Supported versions are: $versionNumbers"
                )
            }
            AbiSignatureVersion.resolveByVersionNumber(signatureVersion!!)
        } else {
            supportedVersions.maxByOrNull(AbiSignatureVersion::versionNumber)
                ?: throw IllegalStateException("Can't choose abiSignatureVersion")
        }

        outputApiFile.bufferedWriter().use {
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
