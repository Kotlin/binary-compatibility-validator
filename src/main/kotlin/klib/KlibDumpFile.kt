/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.klib

internal data class Target(val name: String)

internal class LinesProvider(private val lines: Iterator<String>) : Iterator<String> {
    private var nextLine: String? = null

    public fun peek(): String? {
        if (nextLine != null) {
            return nextLine
        }
        if (!lines.hasNext()) {
            return null
        }
        nextLine = lines.next()
        return nextLine
    }

    override fun hasNext(): Boolean {
        return nextLine != null || lines.hasNext()
    }

    override fun next(): String {
        if (nextLine != null) {
            val res = nextLine!!
            nextLine = null
            return res
        }
        return lines.next()
    }
}

private fun String.depth(): Int {
    return this.takeWhile { it == ' ' }.count() / 4
}

private fun parserBcvTargetsLine(line: String): Set<Target> {
    check(line.startsWith("// BCV Targets: [") && line.endsWith("]")) {
        "Not a BCV-targets line: \"$line\""
    }
    return line.substring("// BCV Targets: [".length, line.length - 1)
        .split(", ")
        .map { Target(it) }
        .toSet()
}

internal class KlibDumpFileBuilder {
    val targets: MutableSet<Target> = mutableSetOf()
    val headerContent: MutableList<String> = mutableListOf()
    val topLevelDeclaration: DeclarationBuilder = DeclarationBuilder("")

    public fun mergeFile(targets: Set<Target>, lines: LinesProvider) {
        val header = mutableListOf<String>()
        var bcvTargets = targets
        var isMergedFile = false
        if (lines.peek()?.startsWith("// BCV Targets: [") == true) {
            isMergedFile = true
            val l = lines.next()
            require(targets.isEmpty())
            bcvTargets = parserBcvTargetsLine(l)
        }
        while (lines.hasNext()) {
            val next = lines.peek()!!
            if (next.startsWith("//") || next.isBlank()) {
                header.add(next)
                lines.next()
            } else {
                break
            }
        }
        if (this.targets.isEmpty()) {
            headerContent.addAll(header)
        } else if (headerContent != header) {
            throw IllegalStateException("File header doesn't match the header of other files")
        }
        this.targets.addAll(bcvTargets)

        topLevelDeclaration.targets.addAll(bcvTargets)
        var currentContainer = topLevelDeclaration
        var depth = -1
        while (lines.hasNext()) {
            val line = lines.peek()!!
            val lineDepth = line.depth()
            when {
                depth == lineDepth -> {
                    if (line.startsWith("// BCV Targets")) {
                        lines.next()
                        currentContainer = currentContainer.parent!!.createChildren(lines.next(),
                            parserBcvTargetsLine(line))
                    } else {
                        currentContainer = currentContainer.parent!!.createChildren(line,
                            if (isMergedFile) currentContainer.parent!!.targets else bcvTargets)
                        lines.next()
                    }
                }
                depth < lineDepth -> {
                    if (line.startsWith("// BCV Targets")) {
                        lines.next()
                        currentContainer = currentContainer.createChildren(lines.next(), parserBcvTargetsLine(line))
                    } else {
                        currentContainer = currentContainer.createChildren(line,
                            if (isMergedFile) currentContainer.targets else bcvTargets)
                        lines.next()
                    }
                    depth = lineDepth
                }
                else -> {
                    while (currentContainer.text.depth() > lineDepth) {
                        currentContainer = currentContainer.parent!!
                    }
                    if (line.trim() == "}") {
                        currentContainer.delimiter = line
                        lines.next()
                    }
                    depth = if (currentContainer.parent == null) -1 else currentContainer.text.depth()
                }
            }
        }
    }

    fun dump(appendable: Appendable) {
        appendable.append("// BCV Targets: [")
            .append(targets.sortedBy { it.name }.joinToString { it.name }).append("]\n")
        headerContent.forEach {
            appendable.append(it).append('\n')
        }
        topLevelDeclaration.children.sortedBy { it.text }.forEach {
            it.dump(appendable, targets)
        }
    }

    fun project(target: Target, appendable: Appendable) {
        if (!targets.contains(target)) {
            return
        }

        headerContent.forEach {
            appendable.append(it).append('\n')
        }
        topLevelDeclaration.children.sortedBy { it.text }.forEach {
            it.project(target, appendable)
        }
    }

    fun remove(target: Target) {
        if (!targets.contains(target)) {
            return
        }

        targets.remove(target)
        topLevelDeclaration.remove(target)
    }
}

internal class DeclarationBuilder(val text: String, val parent: DeclarationBuilder? = null) {
    val targets: MutableSet<Target> = mutableSetOf()
    val children: MutableList<DeclarationBuilder> = mutableListOf()
    var delimiter: String? = null
    private val childrenCache: MutableMap<String, DeclarationBuilder> = mutableMapOf()

    fun createChildren(text: String, targets: Set<Target>): DeclarationBuilder {
        val child = childrenCache.computeIfAbsent(text) {
            val newChild = DeclarationBuilder(it, this)
            children.add(newChild)
            newChild
        }
        child.targets.addAll(targets)
        return child
    }

    fun dump(appendable: Appendable, allTargets: Set<Target>) {
        if (targets != allTargets) {
            appendable.append(" ".repeat(text.depth() * 4))
                .append("// BCV Targets: [")
                .append(targets.sortedBy { it.name }.joinToString { it.name }).append("]\n")
        }
        appendable.append(text).append('\n')
        children.sortedBy { it.text }.forEach {
            it.dump(appendable, this.targets)
        }
        if (delimiter != null) {
            appendable.append(delimiter).append('\n')
        }
    }

    fun project(target: Target, appendable: Appendable) {
        if (!targets.contains(target)) {
            return
        }
        appendable.append(text).append('\n')
        children.sortedBy { it.text }.forEach {
            it.project(target, appendable)
        }
        if (delimiter != null) {
            appendable.append(delimiter).append('\n')
        }
    }

    fun remove(target: Target) {
        if (parent != null && !targets.contains(target)) {
            return
        }

        targets.remove(target)
        children.removeIf {
            val shouldRemove = it.targets.contains(target) && it.targets.size == 1
            if (shouldRemove) {
                childrenCache.remove(it.text)
            }
            shouldRemove
        }
        children.forEach { it.remove(target) }
    }
}
