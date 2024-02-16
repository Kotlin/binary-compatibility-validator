/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib

import org.jetbrains.kotlin.library.abi.*
import java.io.File
import java.io.FileNotFoundException

/**
 * Represent KLib ABI signature version.
 *
 * The version is a positive integer value, like `1` or `2`.
 * The actual set of signature version a particular klib supports may vary depending on a compiler version used to
 * produce the klib.
 *
 * Use [SignatureVersion.LATEST] to use the latest version provided by a klib and supported by a reader,
 * whatever version it is.
 */
public class SignatureVersion(public val version: Int) {
    init {
        require(version >= 1) {
            "Invalid version number: $version. Positive integer value was expected."
        }
    }
    public companion object {
        /**
         * Special [SignatureVersion] value representing the latest
         * ABI signature version provided by a klib and supported by a reader.
         */
        public val LATEST: SignatureVersion = SignatureVersion(Int.MAX_VALUE)
    }
}

/**
 * Filters affecting how the klib ABI will be represented in a dump.
 */
public class KLibDumpFilters internal constructor(
    /**
     * Names of packages that should be excluded from a dump.
     * If a package is listed here, none of its declarations will be included in a dump.
     */
    public val ignoredPackages: Set<String>,
    /**
     * Names of classes that should be excluded from a dump.
     */
    public val ignoredClasses: Set<String>,
    /**
     * Names of annotations marking non-public declarations.
     * Such declarations will be excluded from a dump.
     */
    public val nonPublicMarkers: Set<String>,
    /**
     * KLib ABI signature version to include in a dump.
     */
    public val signatureVersion: SignatureVersion
) {

    public class Builder @PublishedApi internal constructor() {
        /**
         * Names of packages that should be excluded from a dump.
         * If a package is listed here, none of its declarations will be included in a dump.
         *
         * By default, there are no ignored packages.
         */
        public val ignoredPackages: MutableSet<String> = mutableSetOf()
        /**
         * Names of classes that should be excluded from a dump.
         *
         * By default, there are no ignored classes.
         */
        public val ignoredClasses: MutableSet<String> = mutableSetOf()
        /**
         * Names of annotations marking non-public declarations.
         * Such declarations will be excluded from a dump.
         *
         * By default, a set of non-public markers is empty.
         */
        public val nonPublicMarkers: MutableSet<String> = mutableSetOf()
        /**
         * KLib ABI signature version to include in a dump.
         *
         * By default, the latest ABI signature version provided by a klib
         * and supported by a reader will be used.
         */
        public var signatureVersion: SignatureVersion = SignatureVersion.LATEST

        @PublishedApi
        internal fun build(): KLibDumpFilters {
            return KLibDumpFilters(ignoredPackages, ignoredClasses, nonPublicMarkers, signatureVersion)
        }
    }

    public companion object {
        /**
         * Default KLib ABI dump filters which declares no filters
         * and uses the latest KLib ABI signature version available.
         */
        public val DEFAULT: KLibDumpFilters = KLibDumpFilters {}
    }
}

/**
 * Builds a new [KLibDumpFilters] instance by invoking a [builderAction] on a temporary
 * [KLibDumpFilters.Builder] instance and then converting it into filters.
 *
 * Supplied [KLibDumpFilters.Builder] is valid only during the scope of [builderAction] execution.
 */
public fun KLibDumpFilters(builderAction: KLibDumpFilters.Builder.() -> Unit): KLibDumpFilters {
    val builder = KLibDumpFilters.Builder()
    builderAction(builder)
    return builder.build()
}

/**
 * Reads a klib represented by [this] file, filters it contents in accordance to supplied [filters]
 * and writes a resulting dump to [to].
 *
 * @param to an appendable to write a dump to
 * @param filters a set of filters controlling what will be included to a resulting dump
 *
 * @throws FileNotFoundException if a file represented by [this] does not exist
 */
@OptIn(ExperimentalLibraryAbiReader::class)
public fun File.dumpKlib(to: Appendable, filters: KLibDumpFilters = KLibDumpFilters.DEFAULT) {
    if (!exists()) throw FileNotFoundException("File does not exist: $this")

    val library = LibraryAbiReader.readAbiInfo(this, filters.toAbiReaderFilters())

    LibraryAbiRenderer.render(
        library, to,
        AbiRenderingSettings(renderedSignatureVersion = library.resolveSignatureVersion(filters.signatureVersion))
    )
}

/**
 * Reads a klib represented by [this] file, filters it contents in accordance to supplied [filters]
 * and writes a resulting dump to [to].
 *
 * @param to a file to write a dump to
 * @param filters a set of filters controlling what will be included to a resulting dump
 *
 * @throws FileNotFoundException if a file represented by [this] does not exist
 */
public fun File.dumpKlib(to: File, filters: KLibDumpFilters = KLibDumpFilters.DEFAULT) {
    to.bufferedWriter().use {
        dumpKlib(it, filters)
    }
}

@OptIn(ExperimentalLibraryAbiReader::class, ExperimentalStdlibApi::class)
private fun KLibDumpFilters.toAbiReaderFilters(): List<AbiReadingFilter> = buildList {
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

@OptIn(ExperimentalLibraryAbiReader::class)
private fun LibraryAbi.resolveSignatureVersion(signatureVersion: SignatureVersion): AbiSignatureVersion {
    if (signatureVersion == SignatureVersion.LATEST) {
        return signatureVersions.asSequence()
            .filter { it.isSupportedByAbiReader }
            .maxByOrNull { it.versionNumber }
            ?: throw IllegalStateException("Can not resolve last ABI signature version")
    }
    val version = signatureVersions.asSequence()
        .filter { it.isSupportedByAbiReader }
        .firstOrNull { it.versionNumber == signatureVersion.version }

    if (version != null) return version

    val supportedVersionsString = signatureVersions
        .filter { it.isSupportedByAbiReader }
        .map { it.versionNumber }
        .joinToString(prefix = "[", postfix = "]", separator = ", ")
    throw IllegalArgumentException("Selected KLib ABI signature version '${signatureVersion.version}' is unsupported." +
            " Supported versions are: $supportedVersionsString")
}
