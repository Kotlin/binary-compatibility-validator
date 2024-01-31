/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.klib

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TargetHierarchyTest {
    @Test
    fun testHierarchy() {
        assertContentEquals(listOf("linuxArm64", "linux", "native", "all"),
            hierarchyFrom("linuxArm64"))

        assertContentEquals(listOf("js", "all"),
            hierarchyFrom("js"))

        assertContentEquals(listOf("iosArm64", "ios", "apple", "native", "all"),
            hierarchyFrom("iosArm64"))

        assertContentEquals(listOf("androidNative", "native", "all"),
            hierarchyFrom("androidNative"))

        assertContentEquals(listOf("unknown"), hierarchyFrom("unknown"))
    }

    @Test
    fun testTargetsList() {
        assertEquals(setOf("linuxX64"), TargetHierarchy.targets("linuxX64"))
        assertEquals(setOf("macosX64", "macosArm64"), TargetHierarchy.targets("macos"))
        assertEquals(emptySet(), TargetHierarchy.targets("unknown"))
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun hierarchyFrom(groupOrTarget: String): List<String> {
        return buildList {
            var i = 0
            var group: String? = groupOrTarget
            while (group != null) {
                if (i > TargetHierarchy.hierarchyIndex.size) {
                    throw AssertionError("Cycle detected: $this")
                }
                add(group)
                group = TargetHierarchy.parent(group)
                i++
            }
        }
    }
}
