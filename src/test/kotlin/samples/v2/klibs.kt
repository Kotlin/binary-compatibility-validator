/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package samples.v2

import kotlinx.validation.api.klib.KLibDumpFilters
import kotlinx.validation.api.klib.KLibTarget
import kotlinx.validation.api.klib.MergedKLibDumpFormat
import kotlinx.validation.api.klib.v2.*
import java.io.File

class MergedKlibDumpSamples {

    fun mergeMultipleKlibs(klibs: Map<KLibTarget, File>, dest: File) {
        klibs.asSequence().fold(EmptyKlibDump()) { dump, (target, file) ->
            dump.merge(SingleKlibDump(target, file))
        }.dumpTo(dest)
    }

    fun updateDump(mergedDump: File, target: KLibTarget, klib: File) {
        MergedKlibDump(mergedDump)
            .remove(target)
            .merge(SingleKlibDump(target, klib, KLibDumpFilters.DEFAULT))
            .dumpTo(mergedDump)
    }

    fun extract(mergedDump: File, target: KLibTarget, outputFile: File) {
        MergedKlibDump(mergedDump)
            .retain(target)
            .dumpTo(outputFile, MergedKLibDumpFormat {
                saveAsMerged = false
            })
    }
}
