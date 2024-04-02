/*
 * Copyright 2016-2021 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.test

import kotlinx.validation.api.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

internal class MultiPlatformSingleJvmTargetTest : BaseKotlinGradleTest() {
    private fun BaseKotlinScope.createProjectHierarchyWithPluginOnRoot() {
        settingsGradleKts {
            resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
        }
        buildGradleKts {
            resolve("/examples/gradle/base/multiplatformWithSingleJvmTarget.gradle.kts")
        }
    }

    @Test
    fun testApiCheckPasses() {
        val runner = test {
                createProjectHierarchyWithPluginOnRoot()
                runner {
                    arguments.add(":apiCheck")
                    arguments.add("--stacktrace")
                }

                dir("$API_DIR/") {
                    file("testproject.api") {
                        resolve("/examples/classes/Subsub1Class.dump")
                        resolve("/examples/classes/Subsub2Class.dump")
                    }
                }

                dir("src/jvmMain/kotlin") {}
                kotlin("Subsub1Class.kt", "commonMain") {
                    resolve("/examples/classes/Subsub1Class.kt")
                }
                kotlin("Subsub2Class.kt", "jvmMain") {
                    resolve("/examples/classes/Subsub2Class.kt")
                }

            }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    @Test
    fun testApiCheckFails() {
        val runner = test {
                createProjectHierarchyWithPluginOnRoot()
                runner {
                    arguments.add("--continue")
                    arguments.add(":check")
                    arguments.add("--stacktrace")
                }

                dir("$API_DIR/") {
                    file("testproject.api") {
                        resolve("/examples/classes/Subsub2Class.dump")
                        resolve("/examples/classes/Subsub1Class.dump")
                    }
                }

                dir("src/jvmMain/kotlin") {}
                kotlin("Subsub1Class.kt", "commonMain") {
                    resolve("/examples/classes/Subsub1Class.kt")
                }
                kotlin("Subsub2Class.kt", "jvmMain") {
                    resolve("/examples/classes/Subsub2Class.kt")
                }

            }

        runner.buildAndFail().apply {
            assertTaskFailure(":jvmApiCheck")
            assertTaskNotRun(":apiCheck")
            assertThat(output).contains("API check failed for project testproject")
            assertTaskNotRun(":check")
        }
    }

    @Test
    fun testApiDumpPasses() {
        val runner = test {
                createProjectHierarchyWithPluginOnRoot()

                runner {
                    arguments.add(":apiDump")
                    arguments.add("--stacktrace")
                }

                dir("src/jvmMain/kotlin") {}
                kotlin("Subsub1Class.kt", "commonMain") {
                    resolve("/examples/classes/Subsub1Class.kt")
                }
                kotlin("Subsub2Class.kt", "jvmMain") {
                    resolve("/examples/classes/Subsub2Class.kt")
                }

            }

        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val commonExpectedApi = readFileList("/examples/classes/Subsub1Class.dump")

            val mainExpectedApi = commonExpectedApi + "\n" + readFileList("/examples/classes/Subsub2Class.dump")
            assertThat(jvmApiDump.readText()).isEqualToIgnoringNewLines(mainExpectedApi)
        }
    }

    @Test
    fun `apiDump for a project with generated sources only`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/multiplatformWithSingleJvmTarget.gradle.kts")
                resolve("/examples/gradle/configuration/generatedSources/generatedSources.gradle.kts")
            }
            // TODO: enable configuration cache back when we start skipping tasks correctly
            runner(withConfigurationCache = false) {
                arguments.add(":apiDump")
            }
        }
        runner.build().apply {
            assertTaskSuccess(":apiDump")

            val expectedApi = readFileList("/examples/classes/GeneratedSources.dump")
            assertThat(jvmApiDump.readText()).isEqualToIgnoringNewLines(expectedApi)
        }
    }

    @Test
    fun `apiCheck for a project with generated sources only`() {
        val runner = test {
            settingsGradleKts {
                resolve("/examples/gradle/settings/settings-name-testproject.gradle.kts")
            }
            buildGradleKts {
                resolve("/examples/gradle/base/multiplatformWithSingleJvmTarget.gradle.kts")
                resolve("/examples/gradle/configuration/generatedSources/generatedSources.gradle.kts")
            }
            apiFile(projectName = "testproject") {
                resolve("/examples/classes/GeneratedSources.dump")
            }
            // TODO: enable configuration cache back when we start skipping tasks correctly
            runner(withConfigurationCache = false) {
                arguments.add(":apiCheck")
            }
        }
        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    private val jvmApiDump: File get() = rootProjectDir.resolve("$API_DIR/testproject.api")

}
