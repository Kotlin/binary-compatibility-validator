/*
 * Copyright 2016-2022 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.test

import kotlinx.validation.KLIB_PHONY_TARGET_NAME
import kotlinx.validation.api.*
import kotlinx.validation.api.buildGradleKts
import kotlinx.validation.api.kotlin
import kotlinx.validation.api.resolve
import kotlinx.validation.api.test
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.Test

class PublicMarkersTest : BaseKotlinGradleTest() {

    @Test
    fun testPublicMarkers() {
        val runner = test {
            buildGradleKts {
                resolve("examples/gradle/base/withPlugin.gradle.kts")
                resolve("examples/gradle/configuration/publicMarkers/markers.gradle.kts")
            }

            kotlin("ClassWithPublicMarkers.kt") {
                resolve("examples/classes/ClassWithPublicMarkers.kt")
            }

            kotlin("ClassInPublicPackage.kt") {
                resolve("examples/classes/ClassInPublicPackage.kt")
            }

            apiFile(projectName = rootProjectDir.name) {
                resolve("examples/classes/ClassWithPublicMarkers.dump")
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.withDebug(true).build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }

    // Public markers are not supported in KLIB ABI dumps
    @Test
    fun testPublicMarkersForNativeTargets() {
        val runner = test {
            settingsGradleKts {
                resolve("examples/gradle/settings/settings-name-testproject.gradle.kts")
            }

            buildGradleKts {
                resolve("examples/gradle/base/withNativePlugin.gradle.kts")
                resolve("examples/gradle/configuration/publicMarkers/markers.gradle.kts")
            }

            kotlin("ClassWithPublicMarkers.kt", sourceSet = "commonMain") {
                resolve("examples/classes/ClassWithPublicMarkers.kt")
            }

            kotlin("ClassInPublicPackage.kt", sourceSet = "commonMain") {
                resolve("examples/classes/ClassInPublicPackage.kt")
            }

            abiFile(target = KLIB_PHONY_TARGET_NAME, projectName = "testproject") {
                resolve("examples/classes/ClassWithPublicMarkers.klib.dump")
            }

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.withDebug(true).build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }
}
