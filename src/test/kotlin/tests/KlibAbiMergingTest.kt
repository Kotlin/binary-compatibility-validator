/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

import kotlinx.validation.klib.LinesProvider
import kotlinx.validation.klib.KlibDumpFileBuilder
import kotlinx.validation.klib.Target
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class KlibAbiMergingTest {
    @JvmField
    @Rule
    val tempDir = TemporaryFolder()

    private fun file(name: String): LinesProvider {
        val res = KlibAbiMergingTest::class.java.getResourceAsStream(name)
            ?: throw IllegalStateException("Resource not found: $name")
        return LinesProvider(InputStreamReader(res).readLines().iterator())
    }

    private fun dumpToFile(klib: KlibDumpFileBuilder): File {
        val file = tempDir.newFile()
        FileWriter(file).use { klib.dump(it) }
        return file
    }

    @Test
    fun testIdenticalDumpFiles() {
        val klib = KlibDumpFileBuilder()
        klib.mergeFile(setOf(Target("macosArm64"), Target("linuxX64")),
            file("/merge/identical/dump.abi"))
        val merged = dumpToFile(klib)

        assertContentEquals(
            file("/merge/identical/merged.abi").asSequence(),
            Files.readAllLines(merged.toPath()).asSequence())
    }

    @Test
    fun testDivergingDumpFiles() {
        val klib = KlibDumpFileBuilder()
        val random = Random(42)
        for (i in 0 until 10) {
            val targets = mutableListOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
            targets.shuffle(random)
            targets.forEach {
                klib.mergeFile(setOf(Target(it)), file("/merge/diverging/$it.api"))
            }
            val merged = dumpToFile(klib)
            assertContentEquals(
                file("/merge/diverging/merged.abi").asSequence(),
                Files.readAllLines(merged.toPath()).asSequence(),
                merged.readText()
            )
        }
    }

    @Test
    fun testMergeDumpsWithDivergedHeaders() {
        val klib = KlibDumpFileBuilder()
        klib.mergeFile(setOf(Target("linuxArm64")),
            file("/merge/header-mismatch/v1.abi"))

        assertFailsWith<IllegalStateException> {
            klib.mergeFile(setOf(Target("linuxX64")),
                file("/merge/header-mismatch/v2.abi"))
        }
    }

    @Test
    @Ignore
    fun testProjectSingleTargetFromMergedDump() {
        val klib = KlibDumpFileBuilder()
        klib.mergeFile(emptySet(), file("/merge/diverging/merged.abi"))

        val targets = listOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
        targets.forEach { target ->
            val projectionFile = tempDir.newFile()
            FileWriter(projectionFile).use {
                klib.project(Target(target), it)
            }

            assertContentEquals(
                file("/merge/diverging/$target.api").asSequence(),
                Files.readAllLines(projectionFile.toPath()).asSequence()
            )
        }
    }

    @Test
    fun overwriteAll() {
        val klib = KlibDumpFileBuilder()
        klib.mergeFile(emptySet(), file("/merge/diverging/merged.abi"))

        val targets = listOf("androidNativeArm64", "linuxArm64", "linuxX64", "tvOsX64")
        targets.forEach { target ->
            klib.remove(Target(target))
            klib.mergeFile(setOf(Target(target)), file("/merge/diverging/$target.api"))
        }

        val merged = dumpToFile(klib)

        assertContentEquals(
            file("/merge/diverging/merged.abi").asSequence(),
            Files.readAllLines(merged.toPath()).asSequence())
    }

    @Test
    fun read() {
        val klib = KlibDumpFileBuilder()
        klib.mergeFile(emptySet(), file("/merge/idempotent/bcv-klib-test.abi"))

        val written = dumpToFile(klib)
        assertContentEquals(
            file("/merge/idempotent/bcv-klib-test.abi").asSequence(),
            Files.readAllLines(written.toPath()).asSequence())
    }

}
