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

private val nativeTargets = listOf(
    "linuxX64",
    "linuxArm64",
    "mingwX64",
    "macosX64",
    "macosArm64",
    "iosX64",
    "iosArm64",
    "iosSimulatorArm64",
    "tvosX64",
    "tvosArm64",
    "tvosSimulatorArm64",
    "watchosArm32",
    "watchosArm64",
    "watchosX64",
    "watchosSimulatorArm64",
    "watchosDeviceArm64",
    "androidNativeArm32",
    "androidNativeArm64",
    "androidNativeX64",
    "androidNativeX86"
)

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

    // todo: test mixed jvm + native
    // todo: test native with different source sets
    // todo: test markers and filtration
}
