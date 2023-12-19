/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

open class ApiValidationExtension {

    /**
     * Disables API validation checks completely.
     */
    public var validationDisabled = false

    /**
     * Fully qualified package names that are not consider public API.
     * For example, it could be `kotlinx.coroutines.internal` or `kotlinx.serialization.implementation`.
     */
    public var ignoredPackages: MutableSet<String> = HashSet()

    /**
     * Projects that are ignored by the API check.
     */
    public var ignoredProjects: MutableSet<String> = HashSet()

    /**
     * Fully qualified names of annotations that effectively exclude declarations from being public.
     * Example of such annotation could be `kotlinx.coroutines.InternalCoroutinesApi`.
     */
    public var nonPublicMarkers: MutableSet<String> = HashSet()

    /**
     * Fully qualified names of classes that are ignored by the API check.
     * Example of such a class could be `com.package.android.BuildConfig`.
     */
    public var ignoredClasses: MutableSet<String> = HashSet()

    /**
     * Fully qualified names of annotations that can be used to explicitly mark public declarations. 
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public. 
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    public var publicMarkers: MutableSet<String> = HashSet()

    /**
     * Fully qualified package names that contain public declarations. 
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public. 
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    public var publicPackages: MutableSet<String> = HashSet()

    /**
     * Fully qualified names of public classes.
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public.
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    public var publicClasses: MutableSet<String> = HashSet()

    /**
     * Non-default Gradle SourceSet names that should be validated.
     * By default, only the `main` source set is checked.
     */
    public var additionalSourceSets: MutableSet<String> = HashSet()

    /**
     * KLIB ABI validation settings.
     *
     * @see KlibValidationSettings
     */
    public val klib: KlibValidationSettings = KlibValidationSettings()

    /**
     * Configure KLIB AVI validation settings.
     */
    public inline fun klib(block: KlibValidationSettings.() -> Unit) {
        block(this.klib)
    }
}

/**
 * Settings affecting KLIB ABI validation.
 */
open class KlibValidationSettings {
    /**
     * Enables KLIB ABI validation checks.
     */
    public var enabled: Boolean = false
    /**
     * Specify which version of signature KLIB ABI dump should contain.
     */
    public var signatureVersion: Int = 2
    /**
     * TODO: rewrite
     * Allow KLIB ABI dump substitution.
     *
     * **This option aimed to ease the multiplatform development on hosts where not all native targets are
     * supported. It should not be considered as a replacement of validation on a host supporting all required
     * native targets.**
     *
     * If the host compiler does not support a particular target compilation, it would be impossible to update or
     * validate KLIB ABI dump for such a target. However, if the unsupported target does not have target-specific
     * sources then it could be optimistically assumed that the ABI for such a target should be the same as the ABI
     * dumped for the targets having exactly the same source sets. Such assumption will not always hold, so the
     * validation may fail on a host supporting a target whose dump was previously substituted.
     */
    // public var substituteUnsupportedTargets: Boolean = false
    /**
     * Allow inexact KLIB ABI dump substitution.
     *
     * **This option aimed to ease the multiplatform development on hosts where not all native targets are
     * supported. It should not be considered as a replacement of validation on a host supporting all required
     * native targets.**
     *
     * This option extends [substituteUnsupportedTargets] behaviour by substituting the dump of a target not supported
     * by the host compiler with a dump of target whose source sets intersects with the unsupported target source sets
     * at most.
     *
     * To use this option, [substituteUnsupportedTargets] should be first enabled.
     */
    public var allowInexactDumpSubstitution: Boolean = false
    /**
     * Print a warning instead of failing Klib ABI dump and validation tasks when host compiler does not support
     * one of the compilation targets.
     *
     * Klib ABI dumps for unsupported target will not be used to update the merged dump (i.e. for these targets ABI
     * will remain the same as before executing update task) and during the validation these targets will be skipped.
     *
     */
    public var ignoreUnsupportedTargets: Boolean = false
}
