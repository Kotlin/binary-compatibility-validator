/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib.v2

import kotlinx.validation.api.klib.KLibDumpFilters
import kotlinx.validation.api.klib.KLibTarget
import kotlinx.validation.api.klib.MergedKLibDumpFormat
import kotlinx.validation.api.klib.dumpKlib
import kotlinx.validation.klib.KlibAbiDumpMerger
import java.io.File
import java.io.FileNotFoundException

/**
 * Create an empty KLib dump.
 */
public fun EmptyKlibDump(): MergedKlibDump = MergedKlibDump()

/**
 * Loads a merged dump.
 *
 * @throws IllegalArgumentException if the dump already has some of the targets
 * loaded from [dump].
 * @throws FileNotFoundException if [dump] does not exist.
 */
public fun MergedKlibDump(dump: File): MergedKlibDump {
    return MergedKlibDump().also {
        it.merger.loadMergedDump(dump)
    }
}

/**
 * Load a text dump for a target.
 *
 * @throws IllegalArgumentException if the dump already has that target.
 * @throws FileNotFoundException if [dump] does not exist.
 */
public fun SingleKlibDump(target: KLibTarget, dump: File): MergedKlibDump {
    return MergedKlibDump().also {
        it.merger.addIndividualDump(target, dump)
    }
}

public class MergedKlibDump internal constructor(internal val merger: KlibAbiDumpMerger = KlibAbiDumpMerger()) {
    /**
     * Set of targets for which this dump contains declarations.
     */
    public val targets: Set<KLibTarget>
        get() = merger.targets


    /**
     * Returns a new dump consisting of this dump's declarations merged with [other]'s dump's declarations.
     *
     * @throws IllegalArgumentException if this dump's and [other]'s targets intersect.
     */
    public fun merge(other: MergedKlibDump): MergedKlibDump {
        return MergedKlibDump(merger.merge2(other.merger))
    }

    /**
     * Returns a new dump where all declarations from [targets] where removed.
     */
    public fun retain(vararg targets: KLibTarget): MergedKlibDump {
        val toRemove = merger.targets.subtract(targets.toSet())
        var dump = this
        toRemove.forEach {
            dump = this.remove(it)
        }
        return dump
    }

    /**
     * Remove all declarations that do belong to the specified target.
     */
    public fun remove(target: KLibTarget): MergedKlibDump {
        return MergedKlibDump(merger.remove2(target))
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
    val retainedDump = MergedKlibDump(oldDump).apply {
        if (oldDump.exists() && oldDump.length() > 0) {
            merger.retainTargetSpecificAbi(target)
        }
    }
    var commonDump = MergedKlibDump()
    generatedDumps.forEach { tgt, d ->
        commonDump = commonDump.merge(SingleKlibDump(tgt, d))
    }
    commonDump.merger.retainCommonAbi()
    commonDump = commonDump.merge(retainedDump)
    commonDump.merger.overrideTargets(setOf(target))
    return commonDump
}

public fun MergedKlibDump.dumpTo(file: File, dumpFormat: MergedKLibDumpFormat = MergedKLibDumpFormat.DEFAULT) {
    file.bufferedWriter().use {
        dumpTo(it, dumpFormat)
    }
}

public fun SingleKlibDump(target: KLibTarget, klibFile: File, filters: KLibDumpFilters): MergedKlibDump {
    val dump = buildString {
        klibFile.dumpKlib(this, filters)
    }
    return MergedKlibDump().also {
        it.merger.addIndividualDump(target, dump.lines().iterator())
    }
}
