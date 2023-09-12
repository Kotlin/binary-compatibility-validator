/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.test

import kotlinx.validation.api.BaseKotlinGradleTest
import kotlinx.validation.api.assertTaskSuccess
import kotlinx.validation.api.buildGradleKts
import kotlinx.validation.api.emptyApiFile
import kotlinx.validation.api.kotlin
import kotlinx.validation.api.resolve
import kotlinx.validation.api.runner
import kotlinx.validation.api.test
import org.junit.Test

internal class IgnoredAdditionsTests : BaseKotlinGradleTest() {

    @Test
    fun `apiCheck should pass, when a public class is not in api-File, but is ignoreAdditions is enabled`() {
        val runner = test {
            buildGradleKts {
                resolve("examples/gradle/base/withPlugin.gradle.kts")
                resolve("examples/gradle/configuration/ignoreAdditions/ignoreAdditions.gradle.kts")
            }

            kotlin("BuildConfig.kt") {
                resolve("examples/classes/BuildConfig.kt")
            }

            emptyApiFile(projectName = rootProjectDir.name)

            runner {
                arguments.add(":apiCheck")
            }
        }

        runner.build().apply {
            assertTaskSuccess(":apiCheck")
        }
    }
}
