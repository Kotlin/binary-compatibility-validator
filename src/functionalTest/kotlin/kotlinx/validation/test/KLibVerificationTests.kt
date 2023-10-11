/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.test

import kotlinx.validation.api.*
import kotlinx.validation.api.buildGradleKts
import kotlinx.validation.api.resolve
import kotlinx.validation.api.test
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

internal class KLibVerificationTests : BaseKotlinGradleTest() {
    @Test
    fun `apiDump for native targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("examples/classes/TopLevelDeclarations.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/TopLevelDeclarations.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiCheck for native targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("examples/classes/TopLevelDeclarations.kt")
            }

            nativeTargets.forEach {
                abiFile(projectName = "testproject", target = it) {
                    resolve("examples/classes/TopLevelDeclarations.klib.dump")
                }
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    @Test
    fun `apiCheck for native targets should fail when a class is not in a dump`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("examples/classes/BuildConfig.kt")
            }

            nativeTargets.forEach {
                abiFile(projectName = "testproject", target = it) {}
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.buildAndFail().apply {
            val dumpOutput =
                "  @@ -1,1 +1,13 @@\n" +
                        "  +// Rendering settings:\n" +
                        "  +// - Signature version: 2\n" +
                        "  +// - Show manifest properties: false\n" +
                        "  +// - Show declarations: true\n" +
                        "   \n" +
                        "  +// Library unique name: <testproject>\n" +
                        "  +final class com.company/BuildConfig { // com.company/BuildConfig|null[0]\n" +
                        "  +    final val property // com.company/BuildConfig.property|{}property[0]\n" +
                        "  +        final fun <get-property>(): kotlin/Int // com.company/BuildConfig.property.<get-property>|<get-property>(){}[0]\n" +
                        "  +    constructor <init>() // com.company/BuildConfig.<init>|<init>(){}[0]\n" +
                        "  +    final fun function(): kotlin/Int // com.company/BuildConfig.function|function(){}[0]\n" +
                        "  +}\n" +
                        "  +"
            Assertions.assertThat(output).contains(dumpOutput)
            tasks.filter { it.path.endsWith("ApiCheck") }
                .forEach {
                    assertTaskFailure(it.path)
                }
        }
    }

    @Test
    fun `apiDump should include target-specific sources`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("examples/classes/AnotherBuildConfig.kt")
            }
            kotlin("AnotherBuildConfigLinuxArm64.kt", "linuxArm64Main") {
                resolve("examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            // not common, but built from the common source set
            val commonFile = rootProjectAbiDump("linuxX64", "testproject")
            val specializedFile = rootProjectAbiDump("linuxArm64", "testproject")

            // all hosts support Linux target, that's why the test is using them
            assertTrue(commonFile.exists(), "No dump for linuxX64")
            assertTrue(specializedFile.exists(), "No dump for linuxArm64")

            val expectedCommon = readFileList("examples/classes/AnotherBuildConfig.klib.dump")
            val expectedSpecialized = readFileList(
                "examples/classes/AnotherBuildConfigLinuxArm64Extra.klib.dump"
            )

            Assertions.assertThat(commonFile.readText()).isEqualToIgnoringNewLines(expectedCommon)
            Assertions.assertThat(specializedFile.readText()).isEqualToIgnoringNewLines(expectedSpecialized)
        }
    }

    @Test
    fun `apiDump with native targets along with JVM target`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/base/enableJvmInWithNativePlugin.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("examples/classes/AnotherBuildConfig.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/AnotherBuildConfig.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }

            val jvmApiDump = rootProjectAbiDump("jvm", "testproject")
            assertTrue(jvmApiDump.exists(), "No API dump for JVM")

            val jvmExpected = readFileList("examples/classes/AnotherBuildConfig.dump")
            Assertions.assertThat(jvmApiDump.readText()).isEqualToIgnoringNewLines(jvmExpected)
        }
    }

    @Test
    fun `apiDump should ignore a class listed in ignoredClasses`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/ignoredClasses/oneValidFullyQualifiedClass.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("examples/classes/BuildConfig.kt")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("examples/classes/AnotherBuildConfig.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/AnotherBuildConfig.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiDump should succeed if a class listed in ignoredClasses is not found`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/ignoredClasses/oneValidFullyQualifiedClass.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("examples/classes/AnotherBuildConfig.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/AnotherBuildConfig.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiDump should ignore all entities from a package listed in ingoredPacakges`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/ignoredPackages/oneValidPackage.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("examples/classes/BuildConfig.kt")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("examples/classes/AnotherBuildConfig.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/AnotherBuildConfig.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiDump should ignore all entities annotated with non-public markers`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/nonPublicMarkers/klib.gradle.kts")
            }
            kotlin("HiddenDeclarations.kt", "commonMain") {
                resolve("examples/classes/HiddenDeclarations.kt")
            }
            kotlin("NonPublicMarkers.kt", "commonMain") {
                resolve("examples/classes/NonPublicMarkers.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/HiddenDeclarations.klib.dump")

            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiDump should not dump subclasses excluded via ignoredClasses`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/ignoreSubclasses/ignore.gradle.kts")
            }
            kotlin("Subclasses.kt", "commonMain") {
                resolve("examples/classes/Subclasses.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val generatedDumps = nativeTargets
                .map { rootProjectAbiDump(target = it, project = "testproject") }
                .filter(File::exists)
            assertTrue(generatedDumps.isNotEmpty(), "There are no dumps generated for KLibs")

            val expected = readFileList("examples/classes/Subclasses.klib.dump")
            generatedDumps.forEach {
                Assertions.assertThat(it.readText()).isEqualToIgnoringNewLines(expected)
            }
        }
    }

    @Test
    fun `apiCheck for native targets using v1 signatures`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/signatures/v1.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("examples/classes/TopLevelDeclarations.kt")
            }

            nativeTargets.forEach {
                abiFile(projectName = "testproject", target = it) {
                    resolve("examples/classes/TopLevelDeclarations.klib.v1.dump")
                }
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    @Test
    fun `apiDump for native targets should fail when using invalid signature version`() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/signatures/invalid.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("examples/classes/TopLevelDeclarations.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.buildAndFail()
    }
}
