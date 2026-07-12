package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * DIAGNOSTIC TOOL — probes what PSI elements exist at a given file/line/column
 * in a Ruby file and why `findContainingRMethod()` fails during call hierarchy.
 *
 * Temporary — delete when call hierarchy diagnosis is complete.
 */
class RubyCallHierarchyDebugTool : AbstractMcpTool() {

    override val requiresPsiSync = false
    override val participatesInLifecycle = false

    override val name = "ide_ruby_call_hierarchy_debug"

    override val description = """
        Diagnose why call hierarchy cannot resolve a Ruby method at a given position.

        Probes: element class at offset, parent chain (15 levels) with isInstance checks,
        PsiTreeUtil.getParentOfType with reflectively-loaded classes, manual string-based
        parent walk, file-level RMethod scan by line range, RCall descendant scan,
        and PSI subtree dump around the target offset.

        Requires the Ruby plugin.
        Parameters:
        - file (required): project-relative Ruby file path
        - line (required): 1-based line number
        - column (required): 1-based column number
        - writeToFile (optional): if true, writes Markdown report to
          ruby/agent_tests/call_hierarchy/reports/diagnostic-<fname>-<line>-<col>.md
          and returns the file path instead of inline JSON.
        - project_path (optional): routing hint
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Project-relative Ruby file path to diagnose.")
        .lineAndColumn()
        .booleanProperty("writeToFile", "Write a Markdown report to file instead of returning inline JSON.")
        .build()

    // ---- Reflectively loaded Ruby PSI classes -----------------------------------

    private val rMethodClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod") } catch (_: Throwable) { null } }
    private val rClassClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass") } catch (_: Throwable) { null } }
    private val rModuleClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule") } catch (_: Throwable) { null } }
    private val rCallClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall") } catch (_: Throwable) { null } }

    // ---- Execute ----------------------------------------------------------------

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val filePath = optionalStringArg(arguments, "file") ?: return createErrorResult("Missing required: file")
        val line = arguments["line"]?.jsonPrimitive?.int ?: return createErrorResult("Missing required: line")
        val column = arguments["column"]?.jsonPrimitive?.int ?: return createErrorResult("Missing required: column")
        val writeToFile = arguments["writeToFile"]?.jsonPrimitive?.boolean ?: false

        if (rMethodClass == null) {
            return createJsonResult(buildJsonObject { put("error", JsonPrimitive("Ruby plugin not loaded")) })
        }

        return suspendingReadAction {
            val psiFile = resolvePsiFile(project, filePath)
            if (psiFile == null) {
                return@suspendingReadAction createJsonResult(buildJsonObject { put("error", JsonPrimitive("File not found: $filePath")) })
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            val offset = offsetFromLineCol(document, line, column)
            if (offset == null) {
                return@suspendingReadAction createJsonResult(buildJsonObject {
                    put("error", JsonPrimitive("Invalid position L${line}C${column}"))
                    put("fileLineCount", JsonPrimitive(document?.lineCount ?: -1))
                })
            }

            val leaf = psiFile.findElementAt(offset)
            if (leaf == null) {
                return@suspendingReadAction createJsonResult(buildJsonObject { put("error", JsonPrimitive("No element at offset $offset")) })
            }

            val resultJson = buildProbeResult(project, psiFile, document, leaf, filePath, line, column, offset)

            if (writeToFile) {
                val reportDir = File(project.basePath ?: ".", "ruby/agent_tests/call_hierarchy/reports")
                reportDir.mkdirs()
                val safeName = filePath.replace("/", "__").replace("\\", "__").replace(".", "_")
                val reportFile = File(reportDir, "diagnostic-${safeName}-L${line}C${column}.md")
                reportFile.writeText(buildMarkdownReport(resultJson, filePath, line, column))
                createJsonResult(buildJsonObject {
                    put("status", JsonPrimitive("written"))
                    put("reportFile", JsonPrimitive(reportFile.absolutePath))
                })
            } else {
                createJsonResult(resultJson)
            }
        }
    }

    // ---- Probe ---------------------------------------------------------------

    private fun buildProbeResult(
        project: Project, psiFile: PsiFile, document: Document?,
        leaf: PsiElement, filePath: String, line: Int, column: Int, offset: Int
    ): JsonObject = buildJsonObject {
        put("file", JsonPrimitive(filePath))
        put("line", JsonPrimitive(line))
        put("column", JsonPrimitive(column))
        put("offset", JsonPrimitive(offset))

        // 1. Leaf element
        put("leafElement", elementToJson(leaf))
        put("elementText", JsonPrimitive(leaf.text?.take(300) ?: ""))

        // 2. Parent chain
        put("parentChain", buildJsonArray {
            var cur: PsiElement? = leaf
            var depth = 0
            while (cur != null && depth < 15) {
                add(ancestorInfo(cur))
                cur = cur.parent
                depth++
            }
        })

        // 3. PsiTreeUtil probes
        put("psiTreeUtil", probesToJson(leaf))

        // 4. Manual parent walk
        put("manualWalk", manualWalkToJson(leaf))

        // 5. File-level RMethod scan
        val methods = scanMethods(psiFile, document, line)
        put("methods", buildJsonArray { methods.forEach { add(it) } })

        // 6. PSI subtree (collect children, not ancestors)
        put("psiSubtree", buildJsonArray {
            collectSubtreeNodes(psiFile, offset, 60).forEach { add(it) }
        })

        // 7. RCall descendants
        val calls = scanCalls(psiFile)
        if (calls.isNotEmpty()) {
            put("rCalls", buildJsonArray { calls.forEach { add(it) } })
        }

        // 8. Summary
        val insidePTU = probePsiTreeUtil(leaf, rMethodClass) != null
        val insideMW = findParentBySimpleName(leaf, "RMethod", "RMethodImpl") != null
        put("summary", buildJsonObject {
            put("insideRMethod_psiTreeUtil", JsonPrimitive(insidePTU))
            put("insideRMethod_manualWalk", JsonPrimitive(insideMW))
            put("insideClassOrModule", JsonPrimitive(
                findParentBySimpleName(leaf, "RClass", "RModule", "RClassImpl", "RModuleImpl") != null
            ))
            put("leafClassName", JsonPrimitive(leaf::class.java.name))
            put("leafText", JsonPrimitive(leaf.text?.take(100) ?: ""))
        })
    }

    // ---- Helpers: position ---------------------------------------------------

    private fun resolvePsiFile(project: Project, filePath: String): PsiFile? {
        val basePath = project.basePath ?: return null
        for (f in listOf(File(basePath, filePath), File(filePath))) {
            if (f.exists()) {
                val vf = LocalFileSystem.getInstance().findFileByIoFile(f) ?: continue
                return PsiManager.getInstance(project).findFile(vf)
            }
        }
        return null
    }

    private fun offsetFromLineCol(document: Document?, line: Int, column: Int): Int? {
        if (document == null) return null
        val lineIndex = line - 1
        if (lineIndex !in 0 until document.lineCount) return null
        val ls = document.getLineStartOffset(lineIndex)
        val le = document.getLineEndOffset(lineIndex)
        return ls + (column - 1).coerceIn(0, le - ls)
    }

    // ---- Helpers: PSI inspection ---------------------------------------------

    private fun elementToJson(e: PsiElement): JsonObject = buildJsonObject {
        put("class", JsonPrimitive(e::class.java.name))
        put("simpleName", JsonPrimitive(e::class.java.simpleName))
        put("text", JsonPrimitive(e.text?.take(120) ?: ""))
        put("textOffset", JsonPrimitive(e.textOffset))
        val tr = try { e.textRange } catch (_: Throwable) { null }
        if (tr != null) put("textRange", JsonPrimitive("[${tr.startOffset},${tr.endOffset}) len=${tr.length}"))
        put("language", JsonPrimitive(e.language.id))
    }

    private fun ancestorInfo(e: PsiElement): JsonObject = buildJsonObject {
        put("class", JsonPrimitive(e::class.java.name))
        put("simpleName", JsonPrimitive(e::class.java.simpleName))
        put("text", JsonPrimitive(e.text?.take(60) ?: ""))
        put("textOffset", JsonPrimitive(e.textOffset))
        put("isRMethod", JsonPrimitive(rMethodClass?.isInstance(e) ?: false))
        put("isRClass", JsonPrimitive(rClassClass?.isInstance(e) ?: false))
        put("isRModule", JsonPrimitive(rModuleClass?.isInstance(e) ?: false))
        // Also test via PsiTreeUtil static instanceOf
        put("psiTreeUtil_instanceOf", JsonPrimitive(
            try { PsiTreeUtil.instanceOf(e, rMethodClass!!) } catch (_: Throwable) { false }
        ))
    }

    private fun probesToJson(leaf: PsiElement): JsonObject = buildJsonObject {
        val rm = probePsiTreeUtil(leaf, rMethodClass)
        put("psiTreeUtil_getParentOfType_RMethod", JsonPrimitive(rm?.let { getNameQuick(it) } ?: "null"))
        val rc = probePsiTreeUtil(leaf, rClassClass)
        put("psiTreeUtil_getParentOfType_RClass", JsonPrimitive(rc?.let { getNameQuick(it) } ?: "null"))
        val rmod = probePsiTreeUtil(leaf, rModuleClass)
        put("psiTreeUtil_getParentOfType_RModule", JsonPrimitive(rmod?.let { getNameQuick(it) } ?: "null"))
    }

    private fun manualWalkToJson(leaf: PsiElement): JsonObject = buildJsonObject {
        put("byName_RMethod", JsonPrimitive(
            findParentBySimpleName(leaf, "RMethod", "RMethodImpl")?.let { getNameQuick(it) } ?: "null"
        ))
        put("byName_RClass", JsonPrimitive(
            findParentBySimpleName(leaf, "RClass", "RClassImpl")?.let { getNameQuick(it) } ?: "null"
        ))
        put("byName_RModule", JsonPrimitive(
            findParentBySimpleName(leaf, "RModule", "RModuleImpl")?.let { getNameQuick(it) } ?: "null"
        ))
    }

    @Suppress("UNCHECKED_CAST")
    private fun probePsiTreeUtil(element: PsiElement, cls: Class<*>?): PsiElement? {
        if (cls == null) return null
        return try { PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>) } catch (_: Throwable) { null }
    }

    private fun findParentBySimpleName(element: PsiElement, vararg targets: String): PsiElement? {
        var cur: PsiElement? = element
        while (cur != null) {
            if (targets.any { cur::class.java.simpleName.contains(it) }) return cur
            cur = cur.parent
        }
        return null
    }

    private fun getNameQuick(e: PsiElement): String? {
        return try { e::class.java.getMethod("getName").invoke(e) as? String } catch (_: Throwable) { null }
    }

    // ---- File-level scans -----------------------------------------------------

    private fun scanMethods(psiFile: PsiFile, document: Document?, targetLine: Int): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val cls = rMethodClass ?: return result
        try {
            @Suppress("UNCHECKED_CAST")
            val methods = PsiTreeUtil.collectElementsOfType(psiFile, cls as Class<out PsiElement>)
            methods.forEachIndexed { i, m ->
                if (i >= 30) return@forEachIndexed
                val name = getNameQuick(m) ?: "(unnamed)"
                val tr = try { m.textRange } catch (_: Throwable) { null }
                val sl = tr?.startOffset?.let { document?.getLineNumber(it)?.plus(1) }
                val el = tr?.let { t ->
                    val endOff = (t.endOffset).coerceAtMost(t.startOffset + t.length) - 1
                    if (endOff >= 0) document?.getLineNumber(endOff)?.plus(1) else null
                }
                result.add(buildJsonObject {
                    put("index", JsonPrimitive(i))
                    put("name", JsonPrimitive(name))
                    put("class", JsonPrimitive(m::class.java.name))
                    put("offset", JsonPrimitive(tr?.startOffset ?: -1))
                    put("length", JsonPrimitive(tr?.length ?: -1))
                    put("startLine", JsonPrimitive(sl ?: -1))
                    put("endLine", JsonPrimitive(el ?: -1))
                    put("containsTargetLine", JsonPrimitive(sl != null && el != null && targetLine in sl..el))
                })
            }
            if (methods.isEmpty()) {
                result.add(buildJsonObject { put("info", JsonPrimitive("No RMethod descendants found in file")) })
            }
        } catch (e: Throwable) {
            result.add(buildJsonObject { put("error", JsonPrimitive("scanMethods: ${e.message}")) })
        }
        return result
    }

    private fun scanCalls(psiFile: PsiFile): List<JsonObject> {
        val cls = rCallClass ?: return emptyList()
        val result = mutableListOf<JsonObject>()
        try {
            @Suppress("UNCHECKED_CAST")
            val calls = PsiTreeUtil.collectElementsOfType(psiFile, cls as Class<out PsiElement>)
            calls.take(20).forEach { call ->
                val cmd = try { call::class.java.getMethod("getCommand").invoke(call) as? String } catch (_: Throwable) { null }
                result.add(buildJsonObject {
                    put("command", JsonPrimitive(cmd ?: "(no command)"))
                    put("text", JsonPrimitive(call.text?.take(100) ?: ""))
                    put("class", JsonPrimitive(call::class.java.name))
                    put("offset", JsonPrimitive(call.textOffset))
                })
            }
        } catch (_: Throwable) {}
        return result
    }

    // ---- PSI subtree dump ----------------------------------------------------

    private fun collectSubtreeNodes(psiFile: PsiFile, targetOffset: Int, maxNodes: Int): List<JsonObject> {
        val nodes = mutableListOf<JsonObject>()
        val pad = 200

        fun walk(e: PsiElement, depth: Int) {
            if (nodes.size >= maxNodes || depth > 10) return
            val tr = try { e.textRange } catch (_: Throwable) { return }
            val near = tr.startOffset in (targetOffset - pad)..(targetOffset + pad) ||
                tr.endOffset in (targetOffset - pad)..(targetOffset + pad) ||
                (tr.startOffset <= targetOffset && tr.endOffset >= targetOffset)
            if (!near) return

            nodes.add(buildJsonObject {
                put("depth", JsonPrimitive(depth))
                put("class", JsonPrimitive(e::class.java.simpleName))
                put("text", JsonPrimitive(e.text?.take(80)?.replace("\n", "\\n") ?: ""))
                put("range", JsonPrimitive("[${tr.startOffset},${tr.endOffset})"))
                put("containsTarget", JsonPrimitive(tr.startOffset <= targetOffset && tr.endOffset > targetOffset))
            })
            for (child in e.children) walk(child, depth + 1)
        }
        walk(psiFile, 0)
        return nodes
    }

    // ---- Markdown report -----------------------------------------------------

    private fun buildMarkdownReport(json: JsonObject, filePath: String, line: Int, column: Int): String {
        val sb = StringBuilder()
        val fn = filePath.substringAfterLast("/")
        sb.appendLine("# Ruby Call Hierarchy Diagnostic")
        sb.appendLine()
        sb.appendLine("**File:** `$fn`")
        sb.appendLine("**Position:** L${line}C${column}")
        sb.appendLine()

        // Summary
        val summary = json["summary"]?.jsonObject
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("| Check | Result |")
        sb.appendLine("|---|---|")
        sb.appendLine("| Inside RMethod (PsiTreeUtil) | ${summary?.get("insideRMethod_psiTreeUtil")?.jsonPrimitive?.content ?: "?"} |")
        sb.appendLine("| Inside RMethod (manual walk) | ${summary?.get("insideRMethod_manualWalk")?.jsonPrimitive?.content ?: "?"} |")
        sb.appendLine("| Class or Module | ${summary?.get("insideClassOrModule")?.jsonPrimitive?.content ?: "?"} |")
        sb.appendLine("| Leaf Class | `${summary?.get("leafClassName")?.jsonPrimitive?.content ?: "?"}` |")
        sb.appendLine("| Leaf Text | `${(summary?.get("leafText")?.jsonPrimitive?.content ?: "").take(80)}` |")
        sb.appendLine()

        // Parent chain
        sb.appendLine("## Parent Chain")
        sb.appendLine()
        sb.appendLine("| Depth | Class | Text | isRMethod | psiTreeUtil.instanceOf |")
        sb.appendLine("|---|---|---|---|---|")
        val chain = json["parentChain"]?.jsonArray ?: JsonArray(emptyList())
        chain.forEachIndexed { i, entry ->
            val o = entry.jsonObject
            val cls = o["simpleName"]?.jsonPrimitive?.content ?: "?"
            val txt = (o["text"]?.jsonPrimitive?.content ?: "").take(40).replace("|", "/")
            val isRm = o["isRMethod"]?.jsonPrimitive?.content ?: "?"
            val psi = o["psiTreeUtil_instanceOf"]?.jsonPrimitive?.content ?: "?"
            sb.appendLine("| $i | `$cls` | `$txt` | $isRm | $psi |")
        }
        sb.appendLine()

        // PsiTreeUtil probes
        sb.appendLine("## PsiTreeUtil Probes")
        sb.appendLine()
        sb.appendLine("| Probe | Result |")
        sb.appendLine("|---|---|")
        val probes = json["psiTreeUtil"]?.jsonObject ?: JsonObject(emptyMap())
        probes.forEach { (k, v) -> sb.appendLine("| `$k` | ${v.jsonPrimitive.content} |") }
        sb.appendLine()

        // Manual walk
        sb.appendLine("## Manual Parent Walk")
        sb.appendLine()
        sb.appendLine("| Walk | Result |")
        sb.appendLine("|---|---|")
        val walk = json["manualWalk"]?.jsonObject ?: JsonObject(emptyMap())
        walk.forEach { (k, v) -> sb.appendLine("| `$k` | ${v.jsonPrimitive.content} |") }
        sb.appendLine()

        // Methods
        sb.appendLine("## RMethod Scan")
        sb.appendLine()
        val methods = json["methods"]?.jsonArray ?: JsonArray(emptyList())
        if (methods.isEmpty() || methods.firstOrNull()?.jsonObject?.containsKey("info") == true) {
            sb.appendLine("_No RMethod descendants found._")
        } else {
            sb.appendLine("| # | Name | Class | Lines | Contains Target? |")
            sb.appendLine("|---|---|---|---|---|")
            methods.forEach { m ->
                val o = m.jsonObject
                sb.appendLine("| ${o["index"]?.jsonPrimitive?.content ?: "?"} | `${o["name"]?.jsonPrimitive?.content ?: "?"}` | `${o["class"]?.jsonPrimitive?.content?.substringAfterLast(".") ?: "?"}` | ${o["startLine"]?.jsonPrimitive?.content ?: "?"}-${o["endLine"]?.jsonPrimitive?.content ?: "?"} | ${o["containsTargetLine"]?.jsonPrimitive?.content ?: "?"} |")
            }
        }
        sb.appendLine()

        // PSI subtree
        sb.appendLine("## PSI Subtree")
        sb.appendLine()
        sb.appendLine("```")
        val nodes = json["psiSubtree"]?.jsonArray ?: JsonArray(emptyList())
        nodes.forEach { n ->
            val o = n.jsonObject
            val d = o["depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val indent = "  ".repeat(d)
            val cls = o["class"]?.jsonPrimitive?.content ?: "?"
            val txt = o["text"]?.jsonPrimitive?.content ?: ""
            val marker = if (o["containsTarget"]?.jsonPrimitive?.boolean == true) "  <-- TARGET" else ""
            sb.appendLine("$indent$cls \"$txt\"$marker")
        }
        sb.appendLine("```")
        sb.appendLine()

        // RCall descendants
        val calls = json["rCalls"]?.jsonArray ?: JsonArray(emptyList())
        if (calls.isNotEmpty()) {
            sb.appendLine("## RCall Descendants")
            sb.appendLine()
            sb.appendLine("| Command | Text | Class | Offset |")
            sb.appendLine("|---|---|---|---|")
            calls.forEach { c ->
                val o = c.jsonObject
                val cmd = o["command"]?.jsonPrimitive?.content ?: "?"
                val txt = (o["text"]?.jsonPrimitive?.content ?: "").take(60).replace("|", "/")
                val cls = o["class"]?.jsonPrimitive?.content?.substringAfterLast(".") ?: "?"
                sb.appendLine("| `$cmd` | `$txt` | `$cls` | ${o["offset"]?.jsonPrimitive?.content ?: "?"} |")
            }
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine("_Generated by `ide_ruby_call_hierarchy_debug`_")
        return sb.toString()
    }
}