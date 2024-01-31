/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.klib

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
                Node("linuxX64")
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

    fun nonLeafTargets(): Set<String> {
        return hierarchyIndex.values.asSequence()
            .filter {
                it.allLeafs.size > 1 || (it.allLeafs.size == 1 && it.allLeafs.first() != it.node.name)
            }
            .map { it.node.name }
            .toSet()
    }
}

