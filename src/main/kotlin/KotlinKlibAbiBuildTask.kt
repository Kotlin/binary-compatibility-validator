/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import kotlinx.validation.api.klib.KlibSignatureVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.abi.*
import java.io.Serializable

internal class SerializableSignatureVersion(val version: Int) : Serializable {
    constructor(version: KlibSignatureVersion) : this(version.version)

    fun toKlibSignatureVersion(): KlibSignatureVersion = KlibSignatureVersion(version)
}

/**
 * Generates a text file with a KLib ABI dump for a single klib.
 */
internal abstract class KotlinKlibAbiBuildTask : BuildTaskBase() {

    /**
     * Path to a klib to dump.
     */
    @InputFiles
    lateinit var klibFile: FileCollection

    /**
     * Bind this task with a klib compilation.
     */
    @InputFiles
    lateinit var compilationDependencies: FileCollection

    /**
     * Refer to [KlibValidationSettings.signatureVersion] for details.
     */
    @Optional
    @get:Input
    var signatureVersion: SerializableSignatureVersion = SerializableSignatureVersion(KlibSignatureVersion.LATEST)

    /**
     * Name of a target [klibFile] was compiled for.
     */
    @Input
    lateinit var target: String

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
            throw IllegalStateException("Can't read a klib: ${klibFile.singleFile}", e)
        }

        val supportedVersions = parsedAbi.signatureVersions.asSequence().filter { it.isSupportedByAbiReader }
        val sigVersion = if (signatureVersion.toKlibSignatureVersion() != KlibSignatureVersion.LATEST) {
            val versionNumbers = supportedVersions.map { it.versionNumber }.toSortedSet()
            if (signatureVersion.version !in versionNumbers) {
                throw IllegalArgumentException(
                    "Unsupported KLib signature version '${signatureVersion.version}'. " +
                            "Supported versions are: $versionNumbers"
                )
            }
            AbiSignatureVersion.resolveByVersionNumber(signatureVersion.version)
        } else {
            supportedVersions.filter { it.isSupportedByAbiReader }.maxByOrNull(AbiSignatureVersion::versionNumber)
                ?: throw IllegalStateException("Can't choose signatureVersion")
        }

        outputApiFile.bufferedWriter().use {
            LibraryAbiRenderer.render(parsedAbi, it, AbiRenderingSettings(sigVersion, renderManifest = true))
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
