/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.tests

import kotlinx.validation.api.*
import org.junit.*
import org.junit.rules.TestName
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

class PrecompiledCasesTest {

    companion object {
        val baseOutputPath = File("src/test/resources/precompiled")
    }

    @Rule
    @JvmField
    val testName = TestName()

    @Test fun parcelable() { snapshotAPIAndCompare(testName.methodName) }

    @OptIn(ExperimentalPathApi::class)
    private fun snapshotAPIAndCompare(testClassRelativePath: String, nonPublicMarkers: Set<String> = emptySet()) {
        val testClasses = baseOutputPath.toPath().walk().map(Path::toFile).toList()
        check(testClasses.isNotEmpty()) { "No class files are found in path: $baseOutputPath" }

        val testClassStreams = testClasses.asSequence().filter { it.name.endsWith(".class") }.map { it.inputStream() }
        val classes = testClassStreams.loadApiFromJvmClasses()
        val additionalPackages = classes.extractAnnotatedPackages(nonPublicMarkers)
        val api = classes.filterOutNonPublic(nonPublicPackages = additionalPackages).filterOutAnnotated(nonPublicMarkers)
        val target = baseOutputPath.resolve(testClassRelativePath).resolve(testName.methodName + ".txt")
        api.dumpAndCompareWith(target)
    }
}
