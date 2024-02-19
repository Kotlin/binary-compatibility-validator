/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package samples

import kotlinx.validation.api.klib.*
import java.io.File

class MergedKlibDumpSamples {

    fun mergeMultipleKlibs(klibs: Map<KLibTarget, File>, dest: File) {
        MergedKlibDump().apply {
            klibs.forEach { target, file -> load(target, file) }
            dumpTo(dest)
        }
    }

    fun updateDump(mergedDump: File, target: KLibTarget, klib: File) {
        MergedKlibDump().apply {
            loadMerged(mergedDump)
            remove(target)
            load(target, klib, KLibDumpFilters.DEFAULT)
            dumpTo(mergedDump)
        }
    }

    fun extract(mergedDump: File, target: KLibTarget, outputFile: File) {
        MergedKlibDump().apply {
            loadMerged(mergedDump)
            retain(target)
            dumpTo(outputFile, MergedKLibDumpFormat {
                saveAsMerged = false
            })
        }
    }
}
