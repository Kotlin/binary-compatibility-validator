/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib

import kotlinx.validation.ExperimentalBCVApi
import kotlinx.validation.klib.KlibAbiDumpMerger
import java.io.File
import java.io.FileNotFoundException

@ExperimentalBCVApi
public class KlibDump {
    internal var merger: KlibAbiDumpMerger = KlibAbiDumpMerger()

    /**
     * Set of targets for which this dump contains declarations.
     */
    public val targets: Set<KlibTarget>
        get() = merger.targets


    /**
     * Load a text dump for a target and merge it into this dump.
     *
     * @throws IllegalArgumentException if the dump already has that target.
     * @throws FileNotFoundException if [dump] does not exist.
     */
    public fun merge(from: File, withTargetName: String? = null) {
        if (!from.exists()) throw FileNotFoundException(from.absolutePath)
        merger.merge(from, withTargetName)
    }

    /**
     * Merge [other] dump into this one.
     */
    public fun merge(other: KlibDump) {
        val intersection = targets.intersect(other.targets)
        require(intersection.isEmpty()) {
            "Cannot merge dump as this and other dumps share some targets: $intersection"
        }
        merger.merge(other.merger)
    }

    /**
     * Remove all declarations that do not belong to the specified targets.
     */
    public fun retain(targets: Iterable<KlibTarget>) {
        val toRemove = merger.targets.subtract(targets.toSet())
        remove(toRemove)
    }

    /**
     * Remove all declarations that do belong to the specified target.
     */
    public fun remove(targets: Iterable<KlibTarget>) {
        targets.forEach {
            merger.remove(it)
        }
    }

    public fun copy(): KlibDump = KlibDump().also { it.merge(this) }

    /**
     * Convert the dump back into a textual form.
     */
    public fun saveTo(to: Appendable) {
        merger.dump(to)
    }

    public companion object {
        public fun from(dumpFile: File, configurableTargetName: String? = null): KlibDump {
            check(dumpFile.exists()) { "File does not exist: ${dumpFile.absolutePath}" }
            return KlibDump().apply { merge(dumpFile, configurableTargetName) }
        }
        public fun fromKlib(
            klibFile: File,
            configurableTargetName: String? = null,
            filters: KLibDumpFilters = KLibDumpFilters.DEFAULT
        ): KlibDump {
            val dump = buildString {
                dumpTo(this, klibFile, filters)
            }
            return KlibDump().apply {
                merger.merge(dump.splitToSequence('\n').iterator(), configurableTargetName)
            }
        }
    }
}

/**
 * Infer a possible public ABI for [target] using old merged dump [oldDump]
 * and set of [generatedDumps] generated for other targets.
 */
@ExperimentalBCVApi
public fun inferAbi(
    unsupportedTarget: KlibTarget,
    supportedTargetDumps: Iterable<KlibDump>,
    oldMergedDump: KlibDump? = null
): KlibDump {

    val retainedDump = KlibDump().apply {
        if (oldMergedDump != null) {
            merge(oldMergedDump)
            merger.retainTargetSpecificAbi(unsupportedTarget)
        }
    }
    val commonDump = KlibDump().apply {
        supportedTargetDumps.forEach {
            merge(it)
        }
        merger.retainCommonAbi()
    }
    commonDump.merge(retainedDump)
    commonDump.merger.overrideTargets(setOf(unsupportedTarget))
    return commonDump
}

@ExperimentalBCVApi
public fun KlibDump.mergeKlib(
    klibFile: File, configurableTargetName: String? = null,
    filters: KLibDumpFilters = KLibDumpFilters.DEFAULT
) {
    this.merge(KlibDump.fromKlib(klibFile, configurableTargetName, filters))
}
