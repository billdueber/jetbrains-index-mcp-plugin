package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StructureKind
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StructureNode

/**
 * Utility for formatting structure nodes as a tree string.
 *
 * Uses 2-space indentation per nesting level to show hierarchical relationships.
 */
object TreeFormatter {

    /**
     * Formats structure nodes as a tree string.
     *
     * @param nodes List of top-level structure nodes
     * @param fileName The file name to display as header
     * @return Formatted tree string
     */
    fun format(nodes: List<StructureNode>, fileName: String): String {
        val lines = mutableListOf<String>()

        // Add file header
        lines.add("$fileName")
        lines.add("")

        // Format all top-level nodes
        nodes.forEach { node ->
            formatNode(node, indent = 0, output = lines)
        }

        return lines.joinToString("\n")
    }

    /**
     * Recursively formats a structure node and its children.
     *
     * @param node The node to format
     * @param indent The indentation level (number of 2-space units)
     * @param output The output list to append formatted lines to
     */
    private fun formatNode(
        node: StructureNode,
        indent: Int,
        output: MutableList<String>
    ) {
        val indentStr = "  ".repeat(indent)
        val line = buildNodeLine(node)
        output.add(indentStr + line)

        // Format children with increased indentation
        if (node.children.isNotEmpty()) {
            node.children.forEach { child ->
                formatNode(child, indent + 1, output)
            }
        }
    }

    /**
     * Builds a single line representing a structure node.
     */
    private fun buildNodeLine(node: StructureNode): String {
        val modifiers = if (node.modifiers.isNotEmpty()) {
            "${node.modifiers.joinToString(" ")} "
        } else ""

        val kind = kindToString(node.kind)
        val signature = if (!node.signature.isNullOrBlank()) {
            " ${node.signature}"
        } else ""

        return "$kind $modifiers${node.name}$signature (line ${node.line})"
    }

    /**
     * Converts StructureKind to a readable string.
     */
    private fun kindToString(kind: StructureKind): String {
        return when (kind) {
            StructureKind.CLASS -> "class"
            StructureKind.INTERFACE -> "interface"
            StructureKind.ENUM -> "enum"
            StructureKind.ANNOTATION -> "@interface"
            StructureKind.RECORD -> "record"
            StructureKind.OBJECT -> "object"
            StructureKind.TRAIT -> "trait"
            StructureKind.METHOD -> "fun"
            StructureKind.FUNCTION -> "fun"
            StructureKind.FIELD -> "val"
            StructureKind.PROPERTY -> "val"
            StructureKind.CONSTRUCTOR -> "constructor"
            StructureKind.NAMESPACE -> "namespace"
            StructureKind.PACKAGE -> "package"
            StructureKind.MODULE -> "module"
            StructureKind.TYPE_ALIAS -> "typealias"
            StructureKind.VARIABLE -> "var"
            StructureKind.UNKNOWN -> "unknown"
        }
    }
}
