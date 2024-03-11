/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.klib

/**
 * A hierarchy of KMP targets that should resemble the default hierarchy template.
 */
internal object TargetHierarchy {
    class Node(val name: String, vararg childrenNodes: Node) {
        var parent: Node? = null
        val children = childrenNodes.toList().toTypedArray()

        init {
            childrenNodes.forEach {
                it.parent = this
            }
        }

        fun <T> visit(visitor: (Node) -> T): T {
           return visitor(this)
        }
    }

    data class NodeClosure(val node: Node, val allLeafs: Set<String>)

    internal val hierarchyIndex: Map<String, NodeClosure>

    private val hierarchy = Node(
        "all",
        Node("js"),
        Node("wasmJs"),
        Node("wasmWasi"),
        Node("wasm32"),
        Node(
            "native",
            Node(
                "mingw",
                Node("mingwX64"),
                Node("mingwX86")
            ),
            Node(
                "linux",
                Node("linuxArm64"),
                Node("linuxArm32Hfp"),
                Node("linuxX64"),
                Node("linuxMips32"),
                Node("linuxMipsel32")
            ),
            Node(
                "androidNative",
                Node("androidNativeArm64"),
                Node("androidNativeArm32"),
                Node("androidNativeX86"),
                Node("androidNativeX64")
            ),
            Node(
                "apple",
                Node(
                    "macos",
                    Node("macosArm64"),
                    Node("macosX64")
                ),
                Node(
                    "ios",
                    Node("iosArm64"),
                    Node("iosArm32"),
                    Node("iosX64"),
                    Node("iosSimulatorArm64")
                ),
                Node(
                    "tvos",
                    Node("tvosArm64"),
                    Node("tvosX64"),
                    Node("tvosSimulatorArm64")
                ),
                Node(
                    "watchos",
                    Node("watchosArm32"),
                    Node("watchosArm64"),
                    Node("watchosX64"),
                    Node("watchosSimulatorArm64"),
                    Node("watchosDeviceArm64"),
                    Node("watchosX86")
                )
            )
        )
    )

    init {
        val closure = mutableMapOf<String, NodeClosure>()

        fun collectLeafs(node: Node): Set<String> {
            if (node.children.isEmpty()) {
                closure[node.name] = NodeClosure(node, setOf(node.name))
                return setOf(node.name)
            }
            val leafs = mutableSetOf<String>()
            node.children.forEach {
                leafs.addAll(it.visit(::collectLeafs))
            }
            closure[node.name] = NodeClosure(node, leafs)
            return leafs
        }
        val leafs = hierarchy.visit(::collectLeafs)
        closure[hierarchy.name] = NodeClosure(hierarchy, leafs)
        hierarchyIndex = closure
    }

    fun parent(targetOrGroup: String): String? {
        return hierarchyIndex[targetOrGroup]?.node?.parent?.name
    }

    fun targets(targetOrGroup: String): Set<String> {
        return hierarchyIndex[targetOrGroup]?.allLeafs ?: emptySet()
    }
}

internal val konanTargetNameMapping = mapOf(
    "android_x64" to "androidNativeX64",
    "android_x86" to "androidNativeX86",
    "android_arm32" to "androidNativeArm32",
    "android_arm64" to "androidNativeArm64",
    "ios_arm64" to "iosArm64",
    "ios_x64" to "iosX64",
    "ios_simulator_arm64" to "iosSimulatorArm64",
    "watchos_arm32" to "watchosArm32",
    "watchos_arm64" to "watchosArm64",
    "watchos_x64" to "watchosX64",
    "watchos_simulator_arm64" to "watchosSimulatorArm64",
    "watchos_device_arm64" to "watchosDeviceArm64",
    "tvos_arm64" to "tvosArm64",
    "tvos_x64" to "tvosX64",
    "tvos_simulator_arm64" to "tvosSimulatorArm64",
    "linux_x64" to "linuxX64",
    "mingw_x64" to "mingwX64",
    "macos_x64" to "macosX64",
    "macos_arm64" to "macosArm64",
    "linux_arm64" to "linuxArm64",
    "ios_arm32" to "iosArm32",
    "watchos_x86" to "watchosX86",
    "linux_arm32_hfp" to "linuxArm32Hfp",
    "mingw_x86" to "mingwX86",
    "linux_mips32" to "linuxMips32",
    "linux_mipsel32" to "linuxMipsel32",
    "wasm32" to "wasm32"
)

