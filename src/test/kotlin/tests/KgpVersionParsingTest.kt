/*
 * Copyright 2016-2025 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package tests

import kotlinx.validation.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KgpVersionParsingTest {
    @Test
    fun isKgpVersionAtLeast2_1() {
        assertFalse(isKgpVersionAtLeast2_1(""))
        assertFalse(isKgpVersionAtLeast2_1("3"))
        assertFalse(isKgpVersionAtLeast2_1("1.9.20"))
        assertTrue(isKgpVersionAtLeast2_1("2.1"))
        assertTrue(isKgpVersionAtLeast2_1("2.1.0"))
        assertTrue(isKgpVersionAtLeast2_1("2.2.0-RC3"))
        assertTrue(isKgpVersionAtLeast2_1("2.2.20"))
    }

    @Test
    fun isKgpVersionAtLeast2_2_20() {
        assertFalse(isKgpVersionAtLeast2_2_20(""))
        assertFalse(isKgpVersionAtLeast2_2_20("3"))
        assertFalse(isKgpVersionAtLeast2_2_20("1.9.20"))
        assertFalse(isKgpVersionAtLeast2_2_20("2.1"))
        assertFalse(isKgpVersionAtLeast2_2_20("2.1.0"))
        assertFalse(isKgpVersionAtLeast2_2_20("2.2.0-RC3"))
        assertTrue(isKgpVersionAtLeast2_2_20("2.2.20"))
        assertTrue(isKgpVersionAtLeast2_2_20("2.2.20-Beta2"))
        assertTrue(isKgpVersionAtLeast2_2_20("2.2.21"))
        assertTrue(isKgpVersionAtLeast2_2_20("2.3.0"))
        assertTrue(isKgpVersionAtLeast2_2_20("3.0.0"))
    }
}
