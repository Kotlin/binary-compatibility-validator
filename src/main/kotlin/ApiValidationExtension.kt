/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

public open class ApiValidationExtension {

    /**
     * Disables API validation checks completely.
     */
    public var validationDisabled: Boolean = false

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
     * A path to a directory containing an API dump.
     * The path should be relative to the project's root directory and should resolve to its subdirectory.
     * By default, it's `api`.
     */
    public var apiDumpDirectory: String = "api"

    /**
     * KLib ABI validation settings.
     *
     * @see KlibValidationSettings
     */
    public val klib: KlibValidationSettings = KlibValidationSettings()

    /**
     * Configure KLib AVI validation settings.
     */
    public inline fun klib(block: KlibValidationSettings.() -> Unit) {
        block(this.klib)
    }
}

/**
 * Settings affecting KLib ABI validation.
 */
open class KlibValidationSettings {
    /**
     * Enables KLib ABI validation checks.
     */
    public var enabled: Boolean = false
    /**
     * Specify which version of signature KLib ABI dump should contain.
     */
    public var signatureVersion: Int = 2
    /**
     * Fail validation if some build targets are not supported by the host compiler.
     * By default, ABI dumped only for supported files will be validated. This option makes validation behavior
     * more strict and treat having unsupported targets as an error.
     */
    public var strictValidation: Boolean = false
    /**
     * For declarations available only on some targets, replace a comment listing all targets
     * with a short alias.
     *
     * Group aliases are based on [the default hierarchy template](https://kotlinlang.org/docs/multiplatform-hierarchy.html#see-the-full-hierarchy-template)
     * and enabled by default.
     *
     * Grouping will not be applied if a project has a target with a custom name that clashes
     * with one of the group names.
     */
    public var useTargetGroupAliases: Boolean = true
}
