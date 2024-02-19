/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

import kotlinx.validation.api.klib.KLibTarget
import kotlinx.validation.api.klib.MergedKLibDumpFormat
import kotlinx.validation.klib.KlibAbiDumpMerger
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class KlibAbiMergingTest {
    @JvmField
    @Rule
    val tempDir = TemporaryFolder()

    private fun file(name: String): File {
        val res = KlibAbiMergingTest::class.java.getResourceAsStream(name)
            ?: throw IllegalStateException("Resource not found: $name")
        val tempFile = File(tempDir.root, UUID.randomUUID().toString())
        Files.copy(res, tempFile.toPath())
        return tempFile
    }

    private fun lines(name: String): Sequence<String> {
        val res = KlibAbiMergingTest::class.java.getResourceAsStream(name)
            ?: throw IllegalStateException("Resource not found: $name")
        return res.bufferedReader().lineSequence()
    }

    private fun dumpToFile(klib: KlibAbiDumpMerger,
                           singleTargetDump: Boolean = false,
                           useAliases: Boolean = false): File {
        val file = tempDir.newFile()
        FileWriter(file).use {
            klib.dump(it, MergedKLibDumpFormat {
                saveAsMerged = !singleTargetDump
                groupTargetNames = useAliases
            })
        }
        return file
    }

    @Test
    fun identicalDumpFiles() {
        val klib = KlibAbiDumpMerger()
        listOf(KLibTarget("macosArm64"), KLibTarget("linuxX64")).forEach {
            klib.addIndividualDump(it, file("/merge/identical/dump.abi"))
        }
        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/identical/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun identicalDumpFilesWithAliases() {
        val klib = KlibAbiDumpMerger()
        listOf(KLibTarget("macosArm64"), KLibTarget("linuxX64")).forEach {
            klib.addIndividualDump(it, file("/merge/identical/dump.abi"))
        }
        val merged = dumpToFile(klib, useAliases = true)

        // there are no groups other than "all", so no aliases will be added
        assertContentEquals(
            lines("/merge/identical/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun divergingDumpFiles() {
        val klib = KlibAbiDumpMerger()
        val random = Random(42)
        for (i in 0 until 10) {
            val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
            targets.shuffle(random)
            targets.forEach {
                klib.addIndividualDump(KLibTarget(it), file("/merge/diverging/$it.api"))
            }
            val merged = dumpToFile(klib)
            assertContentEquals(
                lines("/merge/diverging/merged.abi"),
                Files.readAllLines(merged.toPath()).asSequence(),
                merged.readText()
            )
        }
    }

    @Test
    fun divergingDumpFilesWithAliases() {
        val klib = KlibAbiDumpMerger()
        val random = Random(42)
        for (i in 0 until 10) {
            val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
            targets.shuffle(random)
            targets.forEach {
                klib.addIndividualDump(KLibTarget(it), file("/merge/diverging/$it.api"))
            }
            val merged = dumpToFile(klib, useAliases = true)
            assertContentEquals(
                lines("/merge/diverging/merged_with_aliases.abi"),
                Files.readAllLines(merged.toPath()).asSequence()
            )
        }
    }

    @Test
    fun aliasedDumpParsing() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/diverging/merged_with_aliases.abi"))

        val withoutAliases = dumpToFile(klib, useAliases = false)
        assertContentEquals(
            lines("/merge/diverging/merged.abi"),
            Files.readAllLines(withoutAliases.toPath()).asSequence()
        )
    }

    @Test
    fun mergeDumpsWithDivergedHeaders() {
        val klib = KlibAbiDumpMerger()
        klib.addIndividualDump(
            KLibTarget("linuxArm64"),
            file("/merge/header-mismatch/v1.abi")
        )

        assertFailsWith<IllegalStateException> {
            klib.addIndividualDump(
                KLibTarget("linuxX64"),
                file("/merge/header-mismatch/v2.abi")
            )
        }
    }

    @Test
    fun overwriteAll() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/diverging/merged.abi"))

        val targets = listOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
        targets.forEach { target ->
            klib.remove(KLibTarget(target))
            klib.addIndividualDump(KLibTarget(target), file("/merge/diverging/$target.api"))
        }

        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/diverging/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun read() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/idempotent/bcv-klib-test.abi"))

        val written = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/idempotent/bcv-klib-test.abi"),
            Files.readAllLines(written.toPath()).asSequence()
        )
    }

    @Test
    fun readDeclarationWithNarrowerChildrenDeclarations() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/parseNarrowChildrenDecls/merged.abi"))

        klib.remove(KLibTarget("linuxArm64"))
        val written1 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxArm64.abi"),
            Files.readAllLines(written1.toPath()).asSequence()
        )

        klib.remove(KLibTarget("linuxX64"))
        val written2 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxAll.abi"),
            Files.readAllLines(written2.toPath()).asSequence()
        )
    }

    @Test
    fun merge() {
        val random = Random(42)
        for (i in 0 until 10) {
            val klib = KlibAbiDumpMerger()
            val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
            targets.shuffle(random)
            targets.forEach {
                klib.merge(KlibAbiDumpMerger().apply {
                    addIndividualDump(KLibTarget(it), file("/merge/diverging/$it.api"))
                })
            }
            val merged = dumpToFile(klib)
            assertContentEquals(
                lines("/merge/diverging/merged.abi"),
                Files.readAllLines(merged.toPath()).asSequence(),
                merged.readText()
            )
        }
    }

    @Test
    fun guessAbi() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/guess/merged.api"))
        klib.retainTargetSpecificAbi(KLibTarget("linuxArm64"))

        val retainedLinuxAbiDump = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/guess/linuxArm64Specific.api"),
            Files.readAllLines(retainedLinuxAbiDump.toPath()).asSequence()
        )

        val commonAbi = KlibAbiDumpMerger()
        commonAbi.loadMergedDump(file("/merge/guess/merged.api"))
        commonAbi.remove(KLibTarget("linuxArm64"))
        commonAbi.retainCommonAbi()

        val commonAbiDump = dumpToFile(commonAbi)
        assertContentEquals(
            lines("/merge/guess/common.api"),
            Files.readAllLines(commonAbiDump.toPath()).asSequence()
        )

        commonAbi.mergeTargetSpecific(klib)
        commonAbi.overrideTargets(setOf(KLibTarget("linuxArm64")))

        val guessedAbiDump = dumpToFile(commonAbi, true)
        assertContentEquals(
            lines("/merge/guess/guessed.api"),
            Files.readAllLines(guessedAbiDump.toPath()).asSequence()
        )
    }

    @Test
    fun loadInvalidFile() {
        assertFails {
            KlibAbiDumpMerger().loadMergedDump(file("/merge/illegalFiles/emptyFile.txt"))
        }

        assertFails {
            KlibAbiDumpMerger().loadMergedDump(file("/merge/illegalFiles/nonDumpFile.txt"))
        }

        assertFails {
            // Not a merged dump
            KlibAbiDumpMerger().loadMergedDump(file("/merge/diverging/linuxArm64.api"))
        }

        assertFails {
            KlibAbiDumpMerger().addIndividualDump(
                KLibTarget("linuxX64"), file("/merge/illegalFiles/emptyFile.txt")
            )
        }

        assertFails {
            KlibAbiDumpMerger().addIndividualDump(
                KLibTarget("linuxX64"), file("/merge/illegalFiles/nonDumpFile.txt")
            )
        }

        assertFails {
            // Not a single-target dump
            KlibAbiDumpMerger().addIndividualDump(
                KLibTarget("linuxX64"), file("/merge/diverging/merged.api")
            )
        }
    }
}
