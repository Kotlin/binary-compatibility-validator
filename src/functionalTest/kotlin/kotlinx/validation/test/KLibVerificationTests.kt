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
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertTrue

internal const val BANNED_TARGETS_PROPERTY_NAME = "binary.compatibility.validator.klib.targets.blacklist.for.testing"

private fun KLibVerificationTests.checkKlibDump(
    buildResult: BuildResult,
    expectedDumpFileName: String,
    projectName: String = "testproject",
    dumpTask: String = ":apiDump"
) {
    buildResult.assertTaskSuccess(dumpTask)

    val generatedDump = rootProjectAbiDump(projectName)
    assertTrue(generatedDump.exists(), "There are no dumps generated for KLibs")

    val expected = readFileList(expectedDumpFileName)

    Assertions.assertThat(generatedDump.readText()).isEqualToIgnoringNewLines(expected)
}

internal class KLibVerificationTests : BaseKotlinGradleTest() {
    @Test
    fun `apiDump for native targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/TopLevelDeclarations.klib.with.linux.dump")
    }

    @Test
    fun `apiCheck for native targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }

            abiFile(projectName = "testproject") {
                resolve("/examples/classes/TopLevelDeclarations.klib.dump")
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
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("/examples/classes/BuildConfig.kt")
            }

            abiFile(projectName = "testproject") {
                resolve("/examples/classes/Empty.klib.dump")
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.buildAndFail().apply {

            Assertions.assertThat(output)
                .contains("+final class com.company/BuildConfig { // com.company/BuildConfig|null[0]")
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
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            kotlin("AnotherBuildConfigLinuxArm64.kt", "linuxArm64Main") {
                resolve("/examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            checkKlibDump(
                this,
                "/examples/classes/AnotherBuildConfigLinuxArm64Extra.klib.dump"
            )
        }
    }

    @Test
    fun `apiDump with native targets along with JVM target`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/base/enableJvmInWithNativePlugin.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        runner.build().apply {
            checkKlibDump(this, "/examples/classes/AnotherBuildConfig.klib.dump")

            val jvmApiDump = rootProjectDir.resolve("$API_DIR/testproject.api")
            assertTrue(jvmApiDump.exists(), "No API dump for JVM")

            val jvmExpected = readFileList("/examples/classes/AnotherBuildConfig.dump")
            Assertions.assertThat(jvmApiDump.readText()).isEqualToIgnoringNewLines(jvmExpected)
        }
    }

    @Test
    fun `apiDump should ignore a class listed in ignoredClasses`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/ignoredClasses/oneValidFullyQualifiedClass.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("/examples/classes/BuildConfig.kt")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/AnotherBuildConfig.klib.dump")
    }

    @Test
    fun `apiDump should succeed if a class listed in ignoredClasses is not found`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/ignoredClasses/oneValidFullyQualifiedClass.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/AnotherBuildConfig.klib.dump")
    }

    @Test
    fun `apiDump should ignore all entities from a package listed in ingoredPackages`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/ignoredPackages/oneValidPackage.gradle.kts")
            }
            kotlin("BuildConfig.kt", "commonMain") {
                resolve("/examples/classes/BuildConfig.kt")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            kotlin("SubPackage.kt", "commonMain") {
                resolve("/examples/classes/SubPackage.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/AnotherBuildConfig.klib.dump")
    }

    @Test
    fun `apiDump should ignore all entities annotated with non-public markers`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/nonPublicMarkers/klib.gradle.kts")
            }
            kotlin("HiddenDeclarations.kt", "commonMain") {
                resolve("/examples/classes/HiddenDeclarations.kt")
            }
            kotlin("NonPublicMarkers.kt", "commonMain") {
                resolve("/examples/classes/NonPublicMarkers.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/HiddenDeclarations.klib.dump")
    }

    @Test
    fun `apiDump should not dump subclasses excluded via ignoredClasses`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/ignoreSubclasses/ignore.gradle.kts")
            }
            kotlin("Subclasses.kt", "commonMain") {
                resolve("/examples/classes/Subclasses.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/Subclasses.klib.dump")
    }

    @Test
    fun `apiCheck for native targets using v1 signatures`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/signatures/v1.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }

            abiFile(projectName = "testproject") {
                resolve("/examples/classes/TopLevelDeclarations.klib.v1.dump")
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
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/signatures/invalid.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }

            runner {
                arguments.add(":apiDump")
            }
        }

        runner.buildAndFail().apply {
            Assertions.assertThat(output).contains("Unsupported KLib signature version '100500'")
        }
    }

    @Test
    fun `apiDump should work for Apple-targets`() {
        Assume.assumeTrue(HostManager().isEnabled(KonanTarget.MACOS_ARM64))
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/appleTargets/targets.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/TopLevelDeclarations.klib.all.dump")
    }

    @Test
    fun `apiCheck should work for Apple-targets`() {
        Assume.assumeTrue(HostManager().isEnabled(KonanTarget.MACOS_ARM64))
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/appleTargets/targets.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            abiFile(projectName = "testproject") {
                resolve("/examples/classes/TopLevelDeclarations.klib.all.dump")
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
    fun `apiCheck should not fail if a target is not supported`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            abiFile(projectName = "testproject") {
                resolve("/examples/classes/TopLevelDeclarations.klib.dump")
            }
            runner {
                arguments.add("-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64")
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    @Test
    fun `apiCheck should ignore unsupported targets by default`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            abiFile(projectName = "testproject") {
                // note that the regular dump is used, where linuxArm64 is presented
                resolve("/examples/classes/TopLevelDeclarations.klib.dump")
            }
            runner {
                arguments.add("-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64")
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    @Test
    fun `apiCheck should fail for unsupported targets with strict mode turned on`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/unsupported/enforce.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            abiFile(projectName = "testproject") {
                // note that the regular dump is used, where linuxArm64 is presented
                resolve("/examples/classes/TopLevelDeclarations.klib.dump")
            }
            runner {
                arguments.add("-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64")
                arguments.add(":apiCheck")
            }
        }

        runner.buildAndFail().apply {
            assertTaskFailure(":klibApiPrepareAbiForValidation")
        }
    }

    @Test
    fun `klibDump should infer a dump for unsupported target from similar enough target`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            kotlin("AnotherBuildConfigLinuxArm64.kt", "linuxArm64Main") {
                resolve("/examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }
            runner {
                arguments.add("-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64")
                arguments.add(":klibApiDump")
            }
        }

        checkKlibDump(
            runner.build(), "/examples/classes/TopLevelDeclarations.klib.with.linux.dump",
            dumpTask = ":klibApiDump"
        )
    }

    @Test
    fun `klibDump should fail when the only target in the project is disabled`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePluginAndSingleTarget.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            kotlin("AnotherBuildConfigLinuxArm64.kt", "linuxArm64Main") {
                resolve("/examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }
            runner {
                arguments.add("-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64")
                arguments.add(":klibApiDump")
            }
        }

        runner.buildAndFail().apply {
            assertTaskFailure(":linuxArm64ApiInferAbiDump")
            Assertions.assertThat(output).contains(
                "The target linuxArm64 is not supported by the host compiler " +
                        "and there are no targets similar to linuxArm64 to infer a dump from it."
            )
        }
    }

    @Test
    fun `klibDump if all klib-targets are unavailable`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            runner {
                arguments.add(
                    "-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64,linuxX64,mingwX64," +
                            "androidNativeArm32,androidNativeArm64,androidNativeX64,androidNativeX86"
                )
                arguments.add(":klibApiDump")
            }
        }

        runner.buildAndFail().apply {
            Assertions.assertThat(output).contains(
                "is not supported by the host compiler and there are no targets similar to"
            )
        }
    }

    @Test
    fun `klibCheck if all klib-targets are unavailable`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("TopLevelDeclarations.kt", "commonMain") {
                resolve("/examples/classes/TopLevelDeclarations.kt")
            }
            abiFile(projectName = "testproject") {
                // note that the regular dump is used, where linuxArm64 is presented
                resolve("/examples/classes/TopLevelDeclarations.klib.dump")
            }
            runner {
                arguments.add(
                    "-P$BANNED_TARGETS_PROPERTY_NAME=linuxArm64,linuxX64,mingwX64," +
                            "androidNativeArm32,androidNativeArm64,androidNativeX64,androidNativeX86"
                )
                arguments.add(":klibApiCheck")
            }
        }

        runner.buildAndFail().apply {
            Assertions.assertThat(output).contains(
                "KLib ABI dump/validation requires at least enabled klib target, but none were found."
            )
        }
    }

    @Test
    fun `target name grouping should be disabled on group name clash`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePluginAndNoTargets.gradle.kts")
                resolve("/examples/gradle/configuration/grouping/clashingTargetNames.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            runner {
                arguments.add(":klibApiDump")
            }
        }

        checkKlibDump(
            runner.build(), "/examples/classes/AnotherBuildConfig.klib.clash.dump",
            dumpTask = ":klibApiDump"
        )
    }

    @Test
    fun `target name grouping with custom target names`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePluginAndNoTargets.gradle.kts")
                resolve("/examples/gradle/configuration/grouping/customTargetNames.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            runner {
                arguments.add(":klibApiDump")
            }
        }

        checkKlibDump(
            runner.build(), "/examples/classes/AnotherBuildConfig.klib.custom.dump",
            dumpTask = ":klibApiDump"
        )
    }

    @Test
    fun `target name grouping`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            kotlin("AnotherBuildConfigLinuxArm64.kt", "linuxArm64Main") {
                resolve("/examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }
            kotlin("AnotherBuildConfigLinuxX64.kt", "linuxX64Main") {
                resolve("/examples/classes/AnotherBuildConfigLinuxArm64.kt")
            }
            runner {
                arguments.add(":klibApiDump")
            }
        }

        checkKlibDump(
            runner.build(), "/examples/classes/AnotherBuildConfigLinux.klib.grouping.dump",
            dumpTask = ":klibApiDump"
        )
    }

    @Test
    fun `apiDump should work with web targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/nonNativeKlibTargets/targets.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            runner {
                arguments.add(":apiDump")
            }
        }

        checkKlibDump(runner.build(), "/examples/classes/AnotherBuildConfig.klib.web.dump")
    }

    @Test
    fun `apiCheck should work with web targets`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("/examples/gradle/configuration/nonNativeKlibTargets/targets.gradle.kts")
            }
            kotlin("AnotherBuildConfig.kt", "commonMain") {
                resolve("/examples/classes/AnotherBuildConfig.kt")
            }
            abiFile(projectName = "testproject") {
                resolve("/examples/classes/AnotherBuildConfig.klib.web.dump")
            }
            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }
}
