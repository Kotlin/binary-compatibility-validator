/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.tests

import kotlinx.validation.generateQualifiedNames
import org.jetbrains.kotlin.library.abi.*
import org.junit.Test
import kotlin.test.assertEquals

class AbiTest {
    @OptIn(ExperimentalLibraryAbiReader::class, ExperimentalStdlibApi::class)
    @Test
    fun generateAbiNames() {
        assertEquals(
            listOf(
                AbiQualifiedName(AbiCompoundName(""), AbiCompoundName("foo.bar.Baz")),
                AbiQualifiedName(AbiCompoundName("foo"), AbiCompoundName("bar.Baz")),
                AbiQualifiedName(AbiCompoundName("foo.bar"), AbiCompoundName("Baz"))
            ),
            generateQualifiedNames("foo.bar.Baz")
        )
        assertEquals(
            listOf(AbiQualifiedName(AbiCompoundName(""), AbiCompoundName("Class"))),
            generateQualifiedNames("Class")
        )
    }
}
