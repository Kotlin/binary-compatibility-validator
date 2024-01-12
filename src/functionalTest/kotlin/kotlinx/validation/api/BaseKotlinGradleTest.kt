/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api

import kotlinx.validation.API_DIR
import kotlinx.validation.KLIB_PHONY_TARGET_NAME
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

public open class BaseKotlinGradleTest {
    @Rule
    @JvmField
    internal val testProjectDir: TemporaryFolder = TemporaryFolder()

    internal val rootProjectDir: File get() = testProjectDir.root

    internal val rootProjectApiDump: File get() = rootProjectDir.resolve("$API_DIR/${rootProjectDir.name}.api")

    internal fun rootProjectAbiDump(target: String, project: String = rootProjectDir.name): File {
        // TODO: rewrite
        val suffix = if (target != KLIB_PHONY_TARGET_NAME) "api" else "klib.abi"
        return rootProjectDir.resolve("$API_DIR/$target/$project.$suffix")
    }

    internal fun rootProjectAbiDump(project: String = rootProjectDir.name): File {
        // TODO: rewrite
        return rootProjectDir.resolve("$API_DIR/$project.klib.abi")
    }
}
