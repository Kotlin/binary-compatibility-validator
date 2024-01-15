/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

    private fun dumpToFile(klib: KlibAbiDumpMerger): File {
        val file = tempDir.newFile()
        FileWriter(file).use { klib.dump(it) }
        return file
    }

    @Test
    fun identicalDumpFiles() {
        val klib = KlibAbiDumpMerger()
        listOf(Target("macosArm64"), Target("linuxX64")).forEach {
            klib.addIndividualDump(it, file("/merge/identical/dump.abi"))
        }
        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/identical/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence())
    }

    @Test
    fun divergingDumpFiles() {
        val klib = KlibAbiDumpMerger()
        val random = Random(42)
        for (i in 0 until 10) {
            val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
            targets.shuffle(random)
            targets.forEach {
                klib.addIndividualDump(Target(it), file("/merge/diverging/$it.api"))
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
    fun mergeDumpsWithDivergedHeaders() {
        val klib = KlibAbiDumpMerger()
        klib.addIndividualDump(Target("linuxArm64"),
            file("/merge/header-mismatch/v1.abi"))

        assertFailsWith<IllegalStateException> {
            klib.addIndividualDump(Target("linuxX64"),
                file("/merge/header-mismatch/v2.abi"))
        }
    }

    @Test
    fun overwriteAll() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/diverging/merged.abi"))

        val targets = listOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
        targets.forEach { target ->
            klib.remove(Target(target))
            klib.addIndividualDump(Target(target), file("/merge/diverging/$target.api"))
        }

        val merged = dumpToFile(klib)

        assertContentEquals(
            lines("/merge/diverging/merged.abi"),
            Files.readAllLines(merged.toPath()).asSequence())
    }

    @Test
    fun read() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/idempotent/bcv-klib-test.abi"))

        val written = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/idempotent/bcv-klib-test.abi"),
            Files.readAllLines(written.toPath()).asSequence())
    }

    @Test
    fun readDeclarationWithNarrowerChildrenDeclarations() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/parseNarrowChildrenDecls/merged.abi"))

        klib.remove(Target("linuxArm64"))
        val written1 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxArm64.abi"),
            Files.readAllLines(written1.toPath()).asSequence())

        klib.remove(Target("linuxX64"))
        val written2 = dumpToFile(klib)
        assertContentEquals(
            lines("/merge/parseNarrowChildrenDecls/withoutLinuxAll.abi"),
            Files.readAllLines(written2.toPath()).asSequence())
    }

    @Test
    fun removeAllButApple() {
        val klib = KlibAbiDumpMerger()
        klib.loadMergedDump(file("/merge/diverging/merged.abi"))
        klib.retainSpecific(Target("linuxArm64"))
        println(buildString {
            klib.dump(this)
        })

        val klib2 = KlibAbiDumpMerger()
        klib2.loadMergedDump(file("/merge/diverging/merged.abi"))
        klib2.retainCommon()
        klib2.remove(Target("linuxArm64"))
        println(buildString {
            klib2.dump(this)
        })

        klib2.mergeTargetSpecific(klib)
        println(buildString {
            klib2.dump(this)
        })
    }
}
