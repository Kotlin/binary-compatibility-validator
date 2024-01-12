/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.klib

import java.io.File
import java.nio.file.Files

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

private const val COMMENT_PREFIX = "//"
private const val TARGETS_LIST_PREFIX = "// Targets: ["
private const val TARGETS_LIST_SUFFIX = "]"
private const val TARGETS_DELIMITER = ", "
private const val CLASS_DECLARATION_TERMINATOR = "}"
private const val INDENT_WIDTH = 4

private fun String.depth(): Int {
    val indentation = this.takeWhile { it == ' ' }.count()
    require(indentation % INDENT_WIDTH == 0) {
        "Unexpected indentation, should be a multiple of $INDENT_WIDTH: $this"
    }
    return indentation / INDENT_WIDTH
}

private fun parseBcvTargetsLine(line: String): Set<Target> {
    check(line.startsWith(TARGETS_LIST_PREFIX) && line.endsWith(TARGETS_LIST_SUFFIX)) {
        "Not a BCV-targets line: \"$line\""
    }
    return line.substring(TARGETS_LIST_PREFIX.length, line.length - 1)
        .split(TARGETS_DELIMITER)
        .map { Target(it) }
        .toSet()
}

internal class KlibAbiDumpMerger {
    private val targets: MutableSet<Target> = mutableSetOf()
    private val headerContent: MutableList<String> = mutableListOf()
    private val topLevelDeclaration: DeclarationContainer = DeclarationContainer("")

    public fun loadMergedDump(file: File) {
        require(file.exists()) { "File does not exist: $file" }
        Files.lines(file.toPath()).use {
            mergeFile(emptySet(), LinesProvider(it.iterator()))
        }
    }

    public fun addIndividualDump(target: Target, file: File) {
        require(file.exists()) { "File does not exist: $file" }
        Files.lines(file.toPath()).use {
            mergeFile(setOf(target), LinesProvider(it.iterator()))
        }
    }

    private fun mergeFile(targets: Set<Target>, lines: LinesProvider) {
        val isMergedFile = targets.isEmpty()
        if (isMergedFile) check(this.targets.isEmpty()) { "Merged dump could only be loaded once." }

        val bcvTargets = if (isMergedFile) {
            lines.parseTargets()
        } else {
            targets
        }
        val header = lines.parseFileHeader()
        if (isMergedFile || this.targets.isEmpty()) {
            headerContent.addAll(header)
        } else if (headerContent != header) {
            throw IllegalStateException("File header doesn't match the header of other files")
        }
        this.targets.addAll(bcvTargets)
        topLevelDeclaration.targets.addAll(bcvTargets)

        // All declarations belonging to the same scope has equal indentation.
        // Nested declarations have higher indentation.
        // By tracking the indentation we can decide if the line should be added into the current container,
        // to its parent container (i.e. the line represents sibling declaration) or the current declaration ended,
        // and we must pop one or several declaration out of the parsing stack.
        var currentContainer = topLevelDeclaration
        var depth = -1

        while (lines.hasNext()) {
            val line = lines.peek()!!
            // TODO: wrap the line and cache the depth inside that wrapper?
            val lineDepth = line.depth()
            when {
                // The depth is the same as before, we encountered a sibling
                depth == lineDepth -> {
                    currentContainer = lines.parseDeclaration(currentContainer.parent!!, bcvTargets, isMergedFile)
                }
                // The depth is increasing, that means we encountered child declaration
                depth < lineDepth -> {
                    check(lineDepth - depth == 1) {
                        "The line has too big indentation relative to a previous line\nline: $line\n" +
                                "previous: ${currentContainer.text}"
                    }
                    currentContainer = lines.parseDeclaration(currentContainer, bcvTargets, isMergedFile)
                    depth = lineDepth
                }
                // Otherwise, we're finishing all the declaration with greater depth compared to the depth of
                // the next line.
                // We won't process a line if it contains a new declaration here, just update the depth and current
                // declaration reference to process the new declaration on the next iteration.
                else -> {
                    while (currentContainer.text.depth() > lineDepth) {
                        currentContainer = currentContainer.parent!!
                    }
                    // If the line is '}' - add it as a terminator to corresponding declaration, it'll simplify
                    // dumping the merged file back to text format.
                    if (line.trim() == CLASS_DECLARATION_TERMINATOR) {
                        currentContainer.delimiter = line
                        // We processed the terminator char, so let's skip this line.
                        lines.next()
                    }
                    // For the top level declaration depth is -1
                    depth = if (currentContainer.parent == null) -1 else currentContainer.text.depth()
                }
            }
        }
    }

    private fun LinesProvider.parseTargets(): Set<Target> {
        val line = peek()
        require(line != null) {
            "List of targets expected, but there are no more lines left."
        }
        require(line.startsWith(TARGETS_LIST_PREFIX)) {
            "The line should starts with $TARGETS_LIST_PREFIX, but was: $line"
        }
        next()
        return parseBcvTargetsLine(line)
    }

    private fun LinesProvider.parseFileHeader(): List<String> {
        val header = mutableListOf<String>()
        while (hasNext()) {
            val next = peek()!!
            if ((next.startsWith(COMMENT_PREFIX) && !next.startsWith(TARGETS_LIST_PREFIX)) || next.isBlank()) {
                header.add(next)
                next()
            } else {
                break
            }
        }
        return header
    }

    private fun LinesProvider.parseDeclaration(
        parent: DeclarationContainer,
        allTargets: Set<Target>,
        isMergedFile: Boolean
    ): DeclarationContainer {
        val line = peek()!!
        return if (line.startsWith(TARGETS_LIST_PREFIX)) {
            check(isMergedFile) {
                "Targets declaration should only be a part of merged file, " +
                        "and the current file claimed to be a regular dump file:\n$line"
            }
            next() // skip prefix
            // Target list means that the declaration following it has narrower set of targets then its parent,
            // so we must use it.
            parent.createOrUpdateChildren(next(), parseBcvTargetsLine(line))
        } else {
            // That's an ugly part:
            // - for a merged file (isMergedFile==true) we need to use parent declaration targets: if we're in this
            //   branch, no explicit targets were specified and new declaration targets should be the same as targets
            //   of its parent. We can't use allTargets here, as parent may have more specific set of targets.
            // - for a single klib dump file we need to specify exact target associated with this file and allTargets
            //   must contain exactly one value here.
            parent.createOrUpdateChildren(next(), if (isMergedFile) parent.targets else allTargets)
        }
    }

    fun dump(appendable: Appendable) {
        val targetsStr = targets.sortedBy { it.name }
            .joinToString(TARGETS_DELIMITER, TARGETS_LIST_PREFIX, TARGETS_LIST_SUFFIX) { it.name }
        appendable.append(targetsStr).append('\n')
        headerContent.forEach {
            appendable.append(it).append('\n')
        }
        topLevelDeclaration.children.sortedWith(DeclarationsComparator).forEach {
            it.dump(appendable, targets)
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

private class DeclarationContainer(val text: String, val parent: DeclarationContainer? = null) {
    val targets: MutableSet<Target> = mutableSetOf()
    val children: MutableList<DeclarationContainer> = mutableListOf()
    var delimiter: String? = null
    private val childrenCache: MutableMap<String, DeclarationContainer> = mutableMapOf()

    fun createOrUpdateChildren(text: String, targets: Set<Target>): DeclarationContainer {
        val child = childrenCache.computeIfAbsent(text) {
            val newChild = DeclarationContainer(it, this)
            children.add(newChild)
            newChild
        }
        child.targets.addAll(targets)
        return child
    }

    fun dump(appendable: Appendable, allTargets: Set<Target>) {
        if (targets != allTargets) {
            val targetsStr = targets.sortedBy { it.name }
                .joinToString(TARGETS_DELIMITER, TARGETS_LIST_PREFIX, TARGETS_LIST_SUFFIX) { it.name }
            // Use the same indentation for target list as for the declaration itself
            appendable.append(" ".repeat(text.depth() * INDENT_WIDTH))
                .append(targetsStr)
                .append('\n')
        }
        appendable.append(text).append('\n')
        children.sortedWith(DeclarationsComparator).forEach {
            it.dump(appendable, this.targets)
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

// TODO: optimize
private object DeclarationsComparator : Comparator<DeclarationContainer> {
    override fun compare(c0: DeclarationContainer, c1: DeclarationContainer): Int {
        return if (c0.targets == c1.targets) {
            c0.text.compareTo(c1.text)
        } else {
            if (c0.targets.size == c1.targets.size) {
                val c0targets = c0.targets.asSequence().map { it.name }.sorted().iterator()
                val c1targets = c1.targets.asSequence().map { it.name }.sorted().iterator()
                var result = 0
                while (c1targets.hasNext() && c0targets.hasNext() && result == 0) {
                    result = c0targets.next().compareTo(c1targets.next())
                }
                result
            } else {
                // longer the target list, earlier the declaration would appear
                c1.targets.size.compareTo(c0.targets.size)
            }
        }
    }
}
