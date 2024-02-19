/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib

import kotlinx.validation.klib.KlibAbiDumpMerger
import java.io.File
import java.io.FileNotFoundException

public class MergedKlibDump {
    internal var merger: KlibAbiDumpMerger = KlibAbiDumpMerger()

    /**
     * Set of targets for which this dump contains declarations.
     */
    public val targets: Set<KLibTarget>
        get() = merger.targets

    /**
     * Load a text dump for a target and merge it into this dump.
     *
     * @throws IllegalArgumentException if the dump already has that target.
     * @throws FileNotFoundException if [dump] does not exist.
     */
    public fun load(target: KLibTarget, dump: File) {
        if (!dump.exists()) throw FileNotFoundException(dump.absolutePath)
        require(!KlibAbiDumpMerger().targets.contains(target)) {
            "Dump already contains target ${target.name}"
        }
        merger.addIndividualDump(target, dump)
    }

    /**
     * Loads a merged dump and merges it into this dump.
     *
     * @throws IllegalArgumentException if the dump already has some of the targets
     * loaded from [dump].
     * @throws FileNotFoundException if [dump] does not exist.
     */
    public fun loadMerged(dump: File) {
        if (!dump.exists()) throw FileNotFoundException(dump.absolutePath)

        // TODO: assert targets
        merger.loadMergedDump(dump)
    }

    /**
     * Merge [other] dump into this one. [other] remains untouched.
     *
     * @throws IllegalArgumentException if this dump's and [other]'s targets intersect.
     */
    public fun merge(other: MergedKlibDump) {
        merger.merge(other.merger)
    }

    /**
     * Remove all declarations that do not belong to the specified targets.
     */
    public fun retain(vararg targets: KLibTarget) {
        val toRemove = merger.targets.subtract(targets.toSet())
        toRemove.forEach {
            remove(it)
        }
    }

    /**
     * Remove all declarations that do belong to the specified target.
     */
    public fun remove(target: KLibTarget) {
        merger.remove(target)
    }

    /**
     * Convert the dump back into a textual form.
     */
    public fun dumpTo(to: Appendable, dumpFormat: MergedKLibDumpFormat = MergedKLibDumpFormat.DEFAULT) {
        merger.dump(to, dumpFormat)
    }
}

/**
 * Guess the possible public ABI for [target] using old merged dump [oldDump]
 * and set of [generatedDumps] generated for other targets.
 */
public fun guessAbi(target: KLibTarget, oldDump: File, generatedDumps: Map<KLibTarget, File>): MergedKlibDump {
    val retainedDump = MergedKlibDump().apply {
        if (oldDump.exists() && oldDump.length() > 0) {
            loadMerged(oldDump)
            merger.retainTargetSpecificAbi(target)
        }
    }
    val commonDump = MergedKlibDump().apply {
        generatedDumps.forEach { tgt, d ->
            load(tgt, d)
        }
        merger.retainCommonAbi()
    }
    commonDump.merge(retainedDump)
    commonDump.merger.overrideTargets(setOf(target))
    return commonDump
}

public fun MergedKlibDump.dumpTo(file: File, dumpFormat: MergedKLibDumpFormat = MergedKLibDumpFormat.DEFAULT) {
    file.bufferedWriter().use {
        dumpTo(it, dumpFormat)
    }
}

public fun MergedKlibDump.load(target: KLibTarget, klibFile: File, filters: KLibDumpFilters) {
    val dump = buildString {
        klibFile.dumpKlib(this, filters)
    }
    merger.addIndividualDump(target, dump.lines().iterator())
}
