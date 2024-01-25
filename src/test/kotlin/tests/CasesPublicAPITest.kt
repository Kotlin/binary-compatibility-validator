/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.tests

import kotlinx.validation.api.*
import org.junit.*
import org.junit.rules.TestName
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class CasesPublicAPITest {

    companion object {
        val baseClassPaths: List<File> =
            System.getProperty("testCasesClassesDirs")
                .let { requireNotNull(it) { "Specify testCasesClassesDirs with a system property"} }
                .split(File.pathSeparator)
                .map { File(it, "cases").canonicalFile }
        val baseOutputPath = File("src/test/kotlin/cases")
    }

    @Rule
    @JvmField
    val testName = TestName()

    @Test fun annotations() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun companions() { snapshotAPIAndCompare(testName.methodName, setOf("cases/companions/PrivateApi")) }

    @Test fun default() { snapshotAPIAndCompare(testName.methodName, setOf("cases/default/PrivateApi")) }

    @Test fun inline() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun interfaces() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun internal() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun java() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun localClasses() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun marker() { snapshotAPIAndCompare(testName.methodName, setOf("cases/marker/HiddenField", "cases/marker/HiddenProperty", "cases/marker/HiddenMethod")) }

    @Test fun nestedClasses() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun packageAnnotations() { snapshotAPIAndCompare(testName.methodName, setOf("cases/packageAnnotations/PrivateApi")) }

    @Test fun private() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun protected() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun public() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun special() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun whenMappings() { snapshotAPIAndCompare(testName.methodName) }

    @Test fun enums() { snapshotAPIAndCompare(testName.methodName) }

    private fun snapshotAPIAndCompare(testClassRelativePath: String, nonPublicMarkers: Set<String> = emptySet()) {
        val testClassPaths = baseClassPaths.map { it.resolve(testClassRelativePath) }
        val testClasses = testClassPaths.flatMap {
            if (!it.exists()) return@flatMap emptyList()
            val allFiles = mutableListOf<File>()
            Files.walkFileTree(it.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file != null) allFiles.add(file.toFile())
                    return FileVisitResult.CONTINUE
                }
            })
            allFiles
        }
        check(testClasses.isNotEmpty()) { "No class files are found in paths: $testClassPaths" }

        val testClassStreams = testClasses.asSequence().filter { it.name.endsWith(".class") }.map { it.inputStream() }
        val classes = testClassStreams.loadApiFromJvmClasses()
        val additionalPackages = classes.extractAnnotatedPackages(nonPublicMarkers)
        val api = classes.filterOutNonPublic(nonPublicPackages = additionalPackages).filterOutAnnotated(nonPublicMarkers)
        val target = baseOutputPath.resolve(testClassRelativePath).resolve(testName.methodName + ".txt")
        api.dumpAndCompareWith(target)
    }
}
