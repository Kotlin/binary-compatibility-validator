/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

import kotlinx.validation.api.klib.KlibTarget
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class KlibTargetNameTest {
    @Test
    fun parse() {
        assertEquals("a.b", KlibTarget("b", "a").toString())
        assertEquals("a", KlibTarget("a").toString())
        assertEquals("a", KlibTarget("a", "a").toString())

        assertFailsWith<IllegalArgumentException> { KlibTarget.parse("") }
        assertFailsWith<IllegalArgumentException> { KlibTarget.parse(" ") }
        assertFailsWith<IllegalArgumentException> { KlibTarget.parse("a.b.c") }
        assertFailsWith<IllegalArgumentException> { KlibTarget.parse("a.") }
        assertFailsWith<IllegalArgumentException> { KlibTarget.parse(".a") }

        KlibTarget.parse("a.b").also {
            assertEquals("b", it.configurableName)
            assertEquals("a", it.targetName)
        }

        KlibTarget.parse("a.a").also {
            assertEquals("a", it.configurableName)
            assertEquals("a", it.targetName)
        }

        KlibTarget.parse("a").also {
            assertEquals("a", it.configurableName)
            assertEquals("a", it.targetName)
        }
    }

    @Test
    fun validate() {
        assertFailsWith<IllegalArgumentException> {
            KlibTarget("a.b", "c")
        }
        assertFailsWith<IllegalArgumentException> {
            KlibTarget("a", "b.c")
        }
    }
}
