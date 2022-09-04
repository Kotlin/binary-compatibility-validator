/*
 * Copyright 2016-2022 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.test

import kotlinx.validation.api.*
import org.junit.Test
import java.io.File

internal class AndroidLibraryTest : BaseKotlinGradleTest() {

    //region Kotlin Android Library

    @Test
    fun `Given a Kotlin Android Library, when api is dumped, then task should be successful`() {
        val runner = test {
            createProjectWithSubModules()
            runner {
                arguments.add(":kotlin-library:apiDump")
                arguments.add("--full-stacktrace")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":kotlin-library:apiDump")
        }
    }

    @Test
    fun `Given a Kotlin Android Library, when api is checked, then it should match the expected`() {
        test {
            createProjectWithSubModules()
            runner {
                arguments.add(":kotlin-library:apiCheck")
            }
        }.build().apply {
            assertTaskSuccess(":kotlin-library:apiCheck")
        }
    }

    //endregion

    //region Java Android Library

    // TODO #94 Java Android Library functional test cases

    //endregion

    /**
     * Creates a single project with 2 (Kotlin and Java Android Library) modules, applies
     * the plugin on the root project.
     */
    private fun BaseKotlinScope.createProjectWithSubModules() {
        settingsGradleKts {
            resolve("examples/gradle/settings/settings-android-project.gradle.kts")
        }
        buildGradleKts {
            resolve("examples/gradle/base/androidProjectRoot.gradle.kts")
        }
        initLocalProperties()

        dir("kotlin-library") {
            buildGradleKts {
                resolve("examples/gradle/base/androidKotlinLibrary.gradle.kts")
            }
            kotlin("KotlinLib.kt") {
                resolve("examples/classes/KotlinLib.kt")
            }
            apiFile(projectName = "kotlin-library") {
                resolve("examples/classes/KotlinLib.dump")
            }
        }
        dir("java-library") {
            buildGradleKts {
                resolve("examples/gradle/base/androidJavaLibrary.gradle.kts")
            }
            // TODO #94 Add sample Java class and expected api dump file
        }
    }

    private fun initLocalProperties() {
        val home = System.getenv("ANDROID_HOME") ?: System.getenv("HOME")
        File(rootProjectDir, "local.properties").apply {
            writeText("sdk.dir=$home/Android/Sdk")
        }
    }

}