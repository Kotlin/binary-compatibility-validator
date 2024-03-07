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
                add(AbiReadingFilter.ExcludedClasses(ignoredClasses.toKlibNames()))
            }
            if (nonPublicMarkers.isNotEmpty()) {
                add(AbiReadingFilter.NonPublicMarkerAnnotations(nonPublicMarkers.toKlibNames()))
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

// We're assuming that all names are in valid binary form as it's described in JVMLS §13.1:
// https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.1
@OptIn(ExperimentalLibraryAbiReader::class)
private fun Collection<String>.toKlibNames(): List<AbiQualifiedName> =
    this.map(String::toAbiQualifiedName).filterNotNull()

@OptIn(ExperimentalLibraryAbiReader::class)
internal fun String.toAbiQualifiedName(): AbiQualifiedName? {
    if (this.isBlank() || this.contains('/')) return null
    // Easiest part: dissect package name from the class name
    val idx = this.lastIndexOf('.')
    if (idx == -1) {
        return AbiQualifiedName(AbiCompoundName(""), this.classNameToCompoundName())
    } else {
        val packageName = this.substring(0, idx)
        val className = this.substring(idx + 1)
        return AbiQualifiedName(AbiCompoundName(packageName), className.classNameToCompoundName())
    }
}

@OptIn(ExperimentalLibraryAbiReader::class)
private fun String.classNameToCompoundName(): AbiCompoundName {
    if (this.isEmpty()) return AbiCompoundName(this)

    val segments = mutableListOf<String>()
    val builder = StringBuilder()

    for (idx in this.indices) {
        val c = this[idx]
        // Don't treat a character as a separator if:
        // - it's not a '$'
        // - it's at the beginning of the segment
        // - it's the last character of the string
        if ( c != '$' || builder.isEmpty() || idx == this.length - 1) {
            builder.append(c)
            continue
        }
        check(c == '$')
        // class$$$susbclass -> class.$$subclass, were at second $ here.
        if (builder.last() == '$') {
            builder.append(c)
            continue
        }

        segments.add(builder.toString())
        builder.clear()
    }
    if (builder.isNotEmpty()) {
        segments.add(builder.toString())
    }
    return AbiCompoundName(segments.joinToString(separator = "."))
}
