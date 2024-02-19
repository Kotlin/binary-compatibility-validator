/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib


/**
 * Represents a set of options controlling how merged KLib API file will be dumped to a textual form.
 */
public class MergedKLibDumpFormat internal constructor(
    /**
     * If `true`, the dump will be converted into a merged dump file.
     * Otherwise, the dump will be converted into a regular KLib ABI dump file, but
     * that requires a dump consisting of declarations belonging to a single target only.
     */
    public val saveAsMerged: Boolean,
    /**
     * If `true`, target name lists will be replaced with aliases, where possible.
     */
    public val groupTargetNames: Boolean
) {
    public companion object {
        /**
         * Default merged KLib dumping setting.
         */
        public val DEFAULT: MergedKLibDumpFormat = MergedKLibDumpFormat {}
    }

    /**
     * Represents a set of options controlling how merged KLib API file will be dumped to a textual form.
     */
    public class Builder internal constructor() {
        /**
         * If `true`, the dump will be converted into a merged dump file.
         * Otherwise, the dump will be converted into a regular KLib ABI dump file, but
         * that requires a dump consisting of declarations belonging to a single target only.
         *
         * By default, a dump will be saved in a merged format.
         */
        public var saveAsMerged: Boolean = true

        /**
         * If `true`, target name lists will be replaced with aliases, where possible.
         *
         * By default, targets will be grouped as long as it's possible.
         */
        public var groupTargetNames: Boolean = true

        internal fun build(): MergedKLibDumpFormat = MergedKLibDumpFormat(saveAsMerged, groupTargetNames)
    }
}

/**
 * Builds a new [MergedKLibDumpFormat] instance by invoking a [builderAction] on a temporary
 * [MergedKLibDumpFormat.Builder] instance and then converting it into filters.
 *
 * Supplied [MergedKLibDumpFormat.Builder] is valid only during the scope of [builderAction] execution.
 */
public fun MergedKLibDumpFormat(builderAction: MergedKLibDumpFormat.Builder.() -> Unit): MergedKLibDumpFormat {
    val builder = MergedKLibDumpFormat.Builder()
    builderAction(builder)
    return builder.build()
}

/**
 * Represent single KLib target, such as `linuxX64` or `iosArm64`.
 */
public class KLibTarget(public val name: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KLibTarget

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return name
    }
}
