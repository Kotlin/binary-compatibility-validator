/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

import kotlinx.validation.klib.KlibAbiDumpFormat
import kotlinx.validation.klib.KlibAbiDumpMerger
import kotlinx.validation.klib.Target
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random
import kotlin.test.*

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
            klib.dump(it, KlibAbiDumpFormat(
                includeTargets = !singleTargetDump,
                useGroupAliases = useAliases
            ))
        }
        return file
    }

    @Test
    fun testTargetNames() {
        assertEquals("a.b", Target("a", "b").toString())
        assertEquals("a", Target("a").toString())
        assertEquals("a", Target("a", "a").toString())

        assertFailsWith<IllegalStateException> { Target.parse("a.b.c") }
        assertFailsWith<IllegalStateException> { Target.parse("a.") }
        assertFailsWith<IllegalStateException> { Target.parse(".a") }

        Target.parse("a.b").also {
            assertEquals("a", it.name)
            assertEquals("b", it.underlyingTarget)
        }

        Target.parse("a.a").also {
            assertEquals("a", it.name)
            assertEquals("a", it.underlyingTarget)
        }

        Target.parse("a").also {
            assertEquals("a", it.name)
            assertEquals("a", it.underlyingTarget)
        }
    }

    @Test
    fun identicalDumpFiles() {
        val klib = KlibAbiDumpMerger()
        klib.addIndividualDump(file("/merge/identical/dump_macos_arm64.abi"))
        klib.addIndividualDump(file("/merge/identical/dump_linux_x64.abi"))
        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/identical/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun identicalDumpFilesWithAliases() {
        val klib = KlibAbiDumpMerger()
        klib.addIndividualDump(file("/merge/identical/dump_macos_arm64.abi"))
        klib.addIndividualDump(file("/merge/identical/dump_linux_x64.abi"))
        val merged = dumpToFile(klib, useAliases = true)

        // there are no groups other than "all", so no aliases will be added
        assertContentEquals(
            lines("/merge/identical/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun divergingDumpFiles() {
        val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
        val random = Random(42)
        for (i in 0 until 10) {
            val klib = KlibAbiDumpMerger()
            targets.shuffle(random)
            targets.forEach {
                klib.addIndividualDump(file("/merge/diverging/$it.api"))
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
                klib.addIndividualDump(file("/merge/diverging/$it.api"))
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
            "linuxArm64",
            file("/merge/header-mismatch/v1.abi")
        )

        assertFailsWith<IllegalStateException> {
            klib.addIndividualDump(
                "linuxX64",
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
            klib.remove(Target(target))
            klib.addIndividualDump(file("/merge/diverging/$target.api"))
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

        klib.remove(Target("linuxArm64"))
        val written1 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxArm64.abi"),
            Files.readAllLines(written1.toPath()).asSequence()
        )

        klib.remove(Target("linuxX64"))
        val written2 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxAll.abi"),
            Files.readAllLines(written2.toPath()).asSequence()
        )
    }

    @Test
    fun guessAbi() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/guess/merged.api"))
        klib.retainTargetSpecificAbi(Target("linuxArm64"))

        val retainedLinuxAbiDump = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/guess/linuxArm64Specific.api"),
            Files.readAllLines(retainedLinuxAbiDump.toPath()).asSequence()
        )

        val commonAbi = KlibAbiDumpMerger()
        commonAbi.loadMergedDump(file("/merge/guess/merged.api"))
        commonAbi.remove(Target("linuxArm64"))
        commonAbi.retainCommonAbi()

        val commonAbiDump = dumpToFile(commonAbi)
        assertContentEquals(
            lines("/merge/guess/common.api"),
            Files.readAllLines(commonAbiDump.toPath()).asSequence()
        )

        commonAbi.mergeTargetSpecific(klib)
        commonAbi.overrideTargets(setOf(Target("linuxArm64")))

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
                "linuxX64", file("/merge/illegalFiles/emptyFile.txt")
            )
        }

        assertFails {
            KlibAbiDumpMerger().addIndividualDump(
                "linuxX64", file("/merge/illegalFiles/nonDumpFile.txt")
            )
        }

        assertFails {
            // Not a single-target dump
            KlibAbiDumpMerger().addIndividualDump(
                "linuxX64", file("/merge/diverging/merged.api")
            )
        }
    }

    @Test
    fun webTargets() {
        val klib = KlibAbiDumpMerger()
        klib.addIndividualDump(file("/merge/webTargets/js.abi"))
        klib.addIndividualDump("wasmWasi", file("/merge/webTargets/wasmWasi.abi"))
        klib.addIndividualDump("wasmJs", file("/merge/webTargets/wasmJs.abi"))

        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/webTargets/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence()
        )
    }

    @Test
    fun unqualifiedWasmTarget() {
        // currently, there's no way to distinguish wasmWasi from wasmJs
        assertFailsWith<IllegalStateException> {
            KlibAbiDumpMerger().addIndividualDump(file("/merge/webTargets/wasmWasi.abi"))
        }
    }

    @Test
    fun customTargetNames() {
        val lib = KlibAbiDumpMerger().apply {
            addIndividualDump("android", file("/merge/diverging/androidNativeArm64.api"))
            addIndividualDump("linux", file("/merge/diverging/linuxArm64.api"))
            addIndividualDump(file("/merge/diverging/linuxX64.api"))
            addIndividualDump(file("/merge/diverging/tvOsX64.api"))
        }

        val dump = dumpToFile(lib, useAliases = true)
        assertContentEquals(
            lines("/merge/diverging/merged_with_aliases_and_custom_names.abi"),
            Files.readAllLines(dump.toPath()).asSequence()
        )
    }

    @Test
    fun customTargetExtraction() {
        val lib = KlibAbiDumpMerger().apply {
            loadMergedDump(file("/merge/diverging/merged_with_aliases_and_custom_names.abi"))
        }
        val targets = lib.targets.filter { it.underlyingTarget != "linuxArm64" }
        targets.forEach { lib.remove(it) }
        println(buildString {
            lib.dump(this, KlibAbiDumpFormat(
                useGroupAliases = false,
                includeTargets = false
            ))
        })
    }
}
