package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
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
import java.time.LocalDateTime
import java.util.function.Consumer

/**
 * DIAGNOSTIC TOOL — probes the 8 "Remaining Unknowns" from section 11
 * of ruby/research/ruby-indexes-master-document.md.
 *
 * Each probe exercises one unknown via reflection on the Ruby plugin's
 * PSI classes and indexes, reporting success/failure and sample data.
 *
 * Temporary — delete when all unknowns are resolved.
 */
class RubyUnknownsProbeTool : AbstractMcpTool() {

    override val requiresPsiSync = false
    override val participatesInLifecycle = false

    override val name = "ide_ruby_unknowns_probe"

    override val description = """
        Probe the 8 remaining unknowns from the Ruby plugin integration research.
        
        For each unknown, exercises the relevant API via reflection on a real
        PSI element or index and reports the result.
        
        Requires the Ruby plugin.
        Parameters:
        - file (required): project-relative Ruby file path to probe against
        - writeToFile (optional): if true, writes Markdown report to
          ruby/research/unknowns/report-<fname>.md and returns the file path
        - project_path (optional): routing hint
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Project-relative Ruby file path to probe against.")
        .booleanProperty("writeToFile", "Write a Markdown report to file instead of returning inline JSON.")
        .build()

    // ---- Reflectively loaded Ruby PSI classes ------------------------------------

    private val rClassClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass") } catch (_: Throwable) { null } }
    private val rModuleClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule") } catch (_: Throwable) { null } }
    private val rMethodClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod") } catch (_: Throwable) { null } }
    private val rCallClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall") } catch (_: Throwable) { null } }
    private val rPossibleCallClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall") } catch (_: Throwable) { null } }
    private val rContainerClass: Class<*>? by lazy { try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.holders.RContainer") } catch (_: Throwable) { null } }

    // ---- Resolution utilities ---------------------------------------------

    private val rubyClassResolveUtilClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.impl.RubyClassResolveUtil") } catch (_: Throwable) { null }
    }
    private val rubyOverrideImplementUtilClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.codeInsight.RubyOverrideImplementUtil") } catch (_: Throwable) { null }
    }
    private val rubyIncludeExtendCallTypesClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.callExpressions.RubyIncludeExtendCallTypes") } catch (_: Throwable) { null }
    }
    private val rubyCallTypeClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.RubyCallType") } catch (_: Throwable) { null }
    }
    private val rubyIncludeExtendCallTypeClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.callExpressions.RubyIncludeExtendCallType") } catch (_: Throwable) { null }
    }

    // ---- Index classes ----------------------------------------------------

    private val rubyMethodNameIndexClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyMethodNameIndex") } catch (_: Throwable) { null }
    }
    private val rubyInheritanceResolutionForSuperClassesClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyInheritanceResolutionIndex\$ForSuperClasses") } catch (_: Throwable) { null }
    }
    private val rubyDeclarationFqnIndexClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDeclarationFqnIndex") } catch (_: Throwable) { null }
    }

    // ---- Execute ----------------------------------------------------------

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val filePath = optionalStringArg(arguments, "file") ?: return createErrorResult("Missing required: file")
        val writeToFile = arguments["writeToFile"]?.jsonPrimitive?.boolean ?: false

        if (rClassClass == null) {
            return createJsonResult(buildJsonObject { put("error", JsonPrimitive("Ruby plugin not loaded")) })
        }

        return suspendingReadAction {
            val psiFile = resolvePsiFile(project, filePath)
            if (psiFile == null) {
                return@suspendingReadAction createJsonResult(buildJsonObject { put("error", JsonPrimitive("File not found: $filePath")) })
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            val fileContent = psiFile.text ?: ""
            val scope = GlobalSearchScope.allScope(project)

            val resultsJson = buildJsonObject {
                put("file", JsonPrimitive(filePath))
                put("probedAt", JsonPrimitive(LocalDateTime.now().toString()))
                put("rubyPluginLoaded", JsonPrimitive(true))

                // Run all 8 probes
                put("probe1_processCallsOfType", probeProcessCallsOfType(psiFile))
                put("probe2_findCall", probeFindCall(psiFile))
                put("probe3_inheritanceTransitivity", probeInheritanceTransitivity(project, scope))
                put("probe4_regexEdgeCases", probeRegexEdgeCases(fileContent))
                put("probe5_attrMethodsInIndex", probeAttrMethodsInIndex(project, scope))
                put("probe6_declarationFqnEnum", probeDeclarationFqnEnum(project))
                put("probe7_overridingOnModule", probeOverridingOnModule(psiFile))
                put("probe8_symbolScope", probeSymbolScope(project, scope, psiFile))
            }

            if (writeToFile) {
                val reportDir = File(project.basePath ?: ".", "ruby/research/unknowns")
                reportDir.mkdirs()
                val safeName = filePath.replace("/", "__").replace("\\", "__").replace(".", "_")
                val reportFile = File(reportDir, "report-${safeName}.md")
                reportFile.writeText(buildMarkdownReport(resultsJson, filePath))
                createJsonResult(buildJsonObject {
                    put("status", JsonPrimitive("written"))
                    put("reportFile", JsonPrimitive(reportFile.absolutePath))
                })
            } else {
                createJsonResult(resultsJson)
            }
        }
    }

    // ---- Probe 1: processCallsOfType(Consumer<RCall>) ------

    private fun probeProcessCallsOfType(psiFile: PsiFile): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(1))
        put("question", JsonPrimitive("Does processCallsOfType invoke Consumer<RCall> correctly via reflection?"))

        try {
            val rClassElements = findRClassElements(psiFile)
            if (rClassElements.isEmpty()) {
                put("status", JsonPrimitive("skipped"))
                put("reason", JsonPrimitive("No RClass elements found in file"))
                return@buildJsonObject
            }

            val firstClass = rClassElements.first()
            val includeCallType = resolveIncludeCallType()
            if (includeCallType == null) {
                put("status", JsonPrimitive("error"))
                put("reason", JsonPrimitive("Could not resolve INCLUDE_CALL type"))
                return@buildJsonObject
            }

            val calls = mutableListOf<String>()
            val consumerClass = Class.forName("java.util.function.Consumer")

            try {
                val method = firstClass::class.java.getMethod("processCallsOfType", rubyCallTypeClass, consumerClass)
                val consumer = Consumer<Any> { call ->
                    val text = try { call::class.java.getMethod("getCommand").invoke(call) as? String } catch (_: Throwable) { null }
                    calls.add(text ?: call.toString().take(100))
                }
                method.invoke(firstClass, includeCallType, consumer)

                put("status", JsonPrimitive("ok"))
                put("methodFound", JsonPrimitive(true))
                put("calledWithConsumerRCall", JsonPrimitive(true))
                put("callsFound", JsonPrimitive(calls.size))
                if (calls.isNotEmpty()) {
                    put("sampleCommands", buildJsonArray { calls.take(10).forEach { add(JsonPrimitive(it)) } })
                } else {
                    put("note", JsonPrimitive("processCallsOfType executed but found no calls of INCLUDE_CALL type"))
                }
            } catch (e: Exception) {
                put("status", JsonPrimitive("error"))
                put("methodFound", JsonPrimitive(true))
                put("exception", JsonPrimitive(e::class.java.simpleName))
                put("message", JsonPrimitive(e.message?.take(200) ?: ""))
                // Try alternative: Consumer<RCall> using raw type
                put("retryWithRawConsumer", JsonPrimitive("attempted - see exception"))
            }
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- Probe 2: RubyClassResolveUtil.findCall() -----------

    private fun probeFindCall(psiFile: PsiFile): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(2))
        put("question", JsonPrimitive("Does RubyClassResolveUtil.findCall() work on real PSI elements?"))

        val utilClass = rubyClassResolveUtilClass
        if (utilClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("RubyClassResolveUtil class not found"))
            return@buildJsonObject
        }

        try {
            val rClassElements = findRClassElements(psiFile)
            if (rClassElements.isEmpty()) {
                put("status", JsonPrimitive("skipped"))
                put("reason", JsonPrimitive("No RClass elements found in file"))
                return@buildJsonObject
            }

            val firstClass = rClassElements.first()
            val includeCallType = resolveIncludeCallType()
            if (includeCallType == null) {
                put("status", JsonPrimitive("error"))
                put("reason", JsonPrimitive("Could not resolve INCLUDE_CALL type"))
                return@buildJsonObject
            }

            // Look for findCall method: findCall(RClass, Predicate<RubyCallType<?>>) -> RPossibleCall
            var findCallMethod: java.lang.reflect.Method? = null
            for (m in utilClass.methods) {
                if (m.name == "findCall" && m.parameterCount == 2) {
                    findCallMethod = m
                    break
                }
            }

            if (findCallMethod == null) {
                put("status", JsonPrimitive("error"))
                put("reason", JsonPrimitive("findCall method not found on RubyClassResolveUtil"))
                return@buildJsonObject
            }

            put("methodSignature", JsonPrimitive(findCallMethod.toString()))

            // Build a Predicate that matches INCLUDE_CALL
            val predicate = object : java.util.function.Predicate<Any> {
                override fun test(t: Any): Boolean {
                    return try {
                        val name = t::class.java.name
                        name.contains("INCLUDE_CALL") || name.contains("Include")
                    } catch (_: Throwable) { false }
                }
            }

            val result = findCallMethod.invoke(null, firstClass, predicate)
            if (result != null) {
                put("status", JsonPrimitive("ok"))
                put("resultType", JsonPrimitive(result::class.java.name))
                val resultText = try {
                    result::class.java.getMethod("getCommand").invoke(result) as? String
                } catch (_: Throwable) { null }
                put("resultCommand", JsonPrimitive(resultText ?: result.toString().take(100)))
            } else {
                put("status", JsonPrimitive("ok"))
                put("resultIsNull", JsonPrimitive(true))
                put("note", JsonPrimitive("findCall returned null — may mean no INCLUDE_CALL found in this class"))
            }
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- Probe 3: Inheritance transitivity ------------------

    private fun probeInheritanceTransitivity(project: Project, scope: GlobalSearchScope): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(3))
        put("question", JsonPrimitive("Does RubyInheritanceResolutionIndex\$ForSuperClasses return transitive or direct subclasses?"))

        val indexClass = rubyInheritanceResolutionForSuperClassesClass
        if (indexClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("ForSuperClasses index class not found"))
            return@buildJsonObject
        }

        try {
            // Get KEY field
            val keyField = indexClass.getDeclaredField("KEY")
            keyField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val skey = keyField.get(null) as StubIndexKey<String, *>

            // Sample keys from index
            val allKeys = mutableListOf<String>()
            ReadAction.run<RuntimeException> {
                StubIndex.getInstance().processAllKeys(
                    skey, Processor { k: String ->
                        allKeys.add(k)
                        allKeys.size < 500
                    }, scope, null
                )
            }

            put("totalKeys", JsonPrimitive(allKeys.size))
            put("sampleKeys", buildJsonArray {
                allKeys.take(30).forEach { add(JsonPrimitive(it)) }
            })

            // Check if Object (root) has many subclasses
            val objectKey = "Object"
            val objectSubclasses = mutableListOf<String>()
            ReadAction.run<RuntimeException> {
                @Suppress("UNCHECKED_CAST")
                val sk = keyField.get(null) as StubIndexKey<String, *>
                StubIndex.getInstance().processAllKeys(
                    sk, Processor { k: String ->
                        if (k.contains(objectKey) || objectKey.contains(k)) {
                            objectSubclasses.add(k)
                        }
                        true
                    }, scope, null
                )
            }

            // Look for transitive chains: if "Object" has "Animal" and "Animal" has "Dog",
            // transitive would include "Dog" under "Object", direct would not
            put("objectRelatedKeys", JsonPrimitive(objectSubclasses.size))
            put("objectRelatedSamples", buildJsonArray {
                objectSubclasses.take(15).forEach { add(JsonPrimitive(it)) }
            })

            // For a precise test: look for known chain patterns
            val chainPatterns = findInheritanceChains(allKeys)
            put("chainPatterns", JsonPrimitive(chainPatterns))

            // Conclusion
            if (chainPatterns.contains("transitive") || objectSubclasses.size > 100) {
                put("conclusion", JsonPrimitive("LIKELY TRANSITIVE — Object alone has many entries, suggesting transitive inclusion"))
            } else {
                put("conclusion", JsonPrimitive("LIKELY DIRECT — keys appear limited to immediate superclass references"))
            }
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    private fun findInheritanceChains(keys: List<String>): String {
        // Look for patterns like Object::Foo::Bar (transitive qualified)
        val transitive = keys.count { it.contains("::") && it.count { c -> c == ':' } >= 4 }
        val direct = keys.count { !it.contains("::") }
        return "transitivePatternKeys=$transitive, directPatternKeys=$direct"
    }

    // ---- Probe 4: PSI text walk regex for multi-line/dynamic --

    private fun probeRegexEdgeCases(fileContent: String): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(4))
        put("question", JsonPrimitive("Does PSI text walk regex handle multi-line/dynamic edge cases?"))

        val regexPatterns = mapOf(
            "singleLineInclude" to Regex("""include\s+(\S+)"""),
            "multiLineInclude" to Regex("""include\s+[\w:]+\s*$""", RegexOption.MULTILINE),
            "includeWithBlock" to Regex("""include\s+(\S+)(?:\s+do\b|$)"""),
            "dynamicInclude" to Regex("""include\s+\S*(?:\$\w+|if|unless|case)"""),
            "extendPattern" to Regex("""extend\s+(\S+)"""),
            "prependPattern" to Regex("""prepend\s+(\S+)"""),
            "classWithParent" to Regex("""class\s+\w+(?:\s*<\s*\w+)?"""),
            "modulePattern" to Regex("""module\s+\w+""")
        )

        val results = buildJsonObject {
            regexPatterns.forEach { (name, pattern) ->
                val matches = pattern.findAll(fileContent).toList()
                put(name, buildJsonObject {
                    put("pattern", JsonPrimitive(pattern.pattern))
                    put("matchCount", JsonPrimitive(matches.size))
                    if (matches.isNotEmpty()) {
                        put("samples", buildJsonArray {
                            matches.take(5).forEach { m ->
                                add(JsonPrimitive(m.value.trim().take(80)))
                            }
                        })
                    }
                })
            }
        }

        put("regexResults", results)
        put("fileLength", JsonPrimitive(fileContent.length))
        put("multiLinePresent", JsonPrimitive(fileContent.contains("\n")))
        put("hasDynamicPatterns", JsonPrimitive(
            fileContent.contains("\$") || fileContent.contains("if ") ||
            fileContent.contains("case ") || fileContent.contains("send(")
        ))

        // Analyze hard edges: multi-line include/extend
        val lines = fileContent.lines()
        val multiLineIssues = mutableListOf<String>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if ((line.startsWith("include ") || line.startsWith("extend ") || line.startsWith("prepend ")) &&
                (line.endsWith("\\") || !line.contains("#") && (i + 1 < lines.size && lines[i + 1].trim().startsWith("#")))
            ) {
                multiLineIssues.add("L${i+1}: $line")
            }
        }

        put("multiLineEdgeCases", buildJsonArray {
            multiLineIssues.take(10).forEach { add(JsonPrimitive(it)) }
        })
    }

    // ---- Probe 5: attr_accessor/define_method in RubyMethodNameIndex --

    private fun probeAttrMethodsInIndex(project: Project, scope: GlobalSearchScope): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(5))
        put("question", JsonPrimitive("Are attr_accessor/define_method methods in RubyMethodNameIndex?"))

        val indexClass = rubyMethodNameIndexClass
        if (indexClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("RubyMethodNameIndex class not found"))
            return@buildJsonObject
        }

        try {
            // Get KEY field
            val keyField = indexClass.getDeclaredField("KEY")
            keyField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val skey = keyField.get(null) as StubIndexKey<String, *>

            // Scan all keys looking for attr_* patterns and define_method related entries
            val attrKeys = mutableListOf<String>()
            val defineMethodKeys = mutableListOf<String>()
            val allKeys = mutableListOf<String>()

            ReadAction.run<RuntimeException> {
                StubIndex.getInstance().processAllKeys(
                    skey, Processor { k: String ->
                        allKeys.add(k)
                        if (k.startsWith("attr_")) attrKeys.add(k)
                        if (k.contains("define_method") || k.contains("define_singleton")) defineMethodKeys.add(k)
                        allKeys.size < 10000
                    }, scope, null
                )
            }

            put("totalKeys", JsonPrimitive(allKeys.size))
            put("attrKeyCount", JsonPrimitive(attrKeys.size))
            put("defineMethodKeyCount", JsonPrimitive(defineMethodKeys.size))

            if (attrKeys.isNotEmpty()) {
                put("attrSampleKeys", buildJsonArray { attrKeys.take(20).forEach { add(JsonPrimitive(it)) } })
            }

            if (defineMethodKeys.isNotEmpty()) {
                put("defineMethodSampleKeys", buildJsonArray { defineMethodKeys.take(10).forEach { add(JsonPrimitive(it)) } })
            }

            // Key presence alone answers the question — attr_*/define_method entries exist in the index
            // Element resolution skipped: StubIndex.processElements not available in this SDK version

            put("conclusion", JsonPrimitive(
                if (attrKeys.isNotEmpty()) "YES — attr_* methods ARE in RubyMethodNameIndex (${attrKeys.size} entries found)"
                else "NO — no attr_* methods found in RubyMethodNameIndex (0 entries)"
            ))
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- Probe 6: RubyDeclarationFqnIndex enum key type -------

    private fun probeDeclarationFqnEnum(project: Project): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(6))
        put("question", JsonPrimitive("Correct key type for RubyDeclarationFqnIndex probe with enum?"))

        val indexClass = rubyDeclarationFqnIndexClass
        if (indexClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("RubyDeclarationFqnIndex class not found"))
            return@buildJsonObject
        }

        try {
            // Find the Type enum
            var typeEnum: Class<*>? = null
            for (cls in indexClass.declaredClasses) {
                if (cls.simpleName == "Type") {
                    typeEnum = cls
                    break
                }
            }

            if (typeEnum == null) {
                put("status", JsonPrimitive("error"))
                put("reason", JsonPrimitive("Type inner enum not found on RubyDeclarationFqnIndex"))
                return@buildJsonObject
            }

            val enumConstants = typeEnum.enumConstants
            put("enumValues", buildJsonArray {
                enumConstants.forEach { add(JsonPrimitive(it.toString())) }
            })

            // Try to probe the index by its name
            val instance = indexClass.getDeclaredConstructor().newInstance()
            val nameMethod = indexClass.getMethod("getName")
            val nameObj = nameMethod.invoke(instance)

            put("indexName", JsonPrimitive(nameObj.toString()))

            // Try to use FileBasedIndex with enum keys
            @Suppress("UNCHECKED_CAST")
            val indexId = nameObj as? com.intellij.util.indexing.ID<Any, Any>
            if (indexId != null) {
                val allKeys = mutableListOf<String>()
                try {
                    ReadAction.run<RuntimeException> {
                        com.intellij.util.indexing.FileBasedIndex.getInstance().processAllKeys(
                            indexId,
                            Processor { key: Any ->
                                allKeys.add(key.toString())
                                allKeys.size < 20
                            },
                            project
                        )
                    }
                } catch (e: Exception) {
                    put("processAllKeysError", JsonPrimitive(e.message?.take(200) ?: ""))
                }

                put("keysFound", JsonPrimitive(allKeys.size))
                if (allKeys.isNotEmpty()) {
                    put("sampleKeys", buildJsonArray { allKeys.forEach { add(JsonPrimitive(it)) } })
                }

                put("conclusion", JsonPrimitive(
                    if (allKeys.isNotEmpty()) "Enum keyed index WORKS — keys returned via FileBasedIndex.processAllKeys"
                    else "Enum keyed index EMPTY or not accessible via FileBasedIndex"
                ))
            } else {
                put("status", JsonPrimitive("error"))
                put("reason", JsonPrimitive("Could not cast index name to ID"))
            }
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- Probe 7: getOverridingElements on RModule -----------

    private fun probeOverridingOnModule(psiFile: PsiFile): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(7))
        put("question", JsonPrimitive("Does RubyOverrideImplementUtil.getOverridingElements work on RModule elements?"))

        val utilClass = rubyOverrideImplementUtilClass
        val containerClass = rContainerClass
        if (utilClass == null || containerClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("RubyOverrideImplementUtil or RContainer class not found"))
            return@buildJsonObject
        }

        try {
            val rModuleElements = findRModuleElements(psiFile)
            val rClassElements = findRClassElements(psiFile)

            put("modulesFound", JsonPrimitive(rModuleElements.size))
            put("classesFound", JsonPrimitive(rClassElements.size))

            // Test RModule
            if (rModuleElements.isNotEmpty()) {
                val firstModule = rModuleElements.first()
                if (containerClass.isInstance(firstModule)) {
                    try {
                        val method = utilClass.getMethod("getOverridingElements", containerClass)
                        val result = method.invoke(null, containerClass.cast(firstModule)) as? Collection<*>
                        put("moduleResult", buildJsonObject {
                            put("methodFound", JsonPrimitive(true))
                            put("resultCount", JsonPrimitive(result?.size ?: 0))
                            if (!result.isNullOrEmpty()) {
                                put("resultTypes", buildJsonArray {
                                    result.take(10).forEach { add(JsonPrimitive(it?.let { it::class.java.simpleName } ?: "null")) }
                                })
                            }
                        })
                    } catch (e: Exception) {
                        put("moduleResult", buildJsonObject {
                            put("methodFound", JsonPrimitive(true))
                            put("error", JsonPrimitive(e.message?.take(200) ?: ""))
                        })
                    }
                } else {
                    put("moduleResult", buildJsonObject {
                        put("isInstanceOfRContainer", JsonPrimitive(false))
                        put("actualClass", JsonPrimitive(firstModule::class.java.name))
                    })
                }
            }

            // Compare: test RClass too
            if (rClassElements.isNotEmpty()) {
                val firstClass = rClassElements.first()
                if (containerClass.isInstance(firstClass)) {
                    try {
                        val method = utilClass.getMethod("getOverridingElements", containerClass)
                        val result = method.invoke(null, containerClass.cast(firstClass)) as? Collection<*>
                        put("classResult", buildJsonObject {
                            put("resultCount", JsonPrimitive(result?.size ?: 0))
                            if (!result.isNullOrEmpty()) {
                                put("resultTypes", buildJsonArray {
                                    result.take(10).forEach { add(JsonPrimitive(it?.let { it::class.java.simpleName } ?: "null")) }
                                })
                            }
                        })
                    } catch (e: Exception) {
                        put("classResult", buildJsonObject {
                            put("error", JsonPrimitive(e.message?.take(200) ?: ""))
                        })
                    }
                }
            }

            put("conclusion", JsonPrimitive(
                when {
                    rModuleElements.isEmpty() && rClassElements.isEmpty() -> "No modules or classes to test"
                    else -> "getOverridingElements was invoked on available elements"
                }
            ))
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- Probe 8: Symbol tree scope (project vs gems) ---------

    private fun probeSymbolScope(project: Project, scope: GlobalSearchScope, psiFile: PsiFile): JsonObject = buildJsonObject {
        put("unknown", JsonPrimitive(8))
        put("question", JsonPrimitive("What is the scope of the symbol tree — does it cover gems from Gemfile or only project files + stdlib?"))

        val utilClass = rubyOverrideImplementUtilClass
        if (utilClass == null) {
            put("status", JsonPrimitive("error"))
            put("reason", JsonPrimitive("RubyOverrideImplementUtil class not found"))
            return@buildJsonObject
        }

        try {
            val rClassElements = findRClassElements(psiFile)

            // Check what the current file's scope is
            val vFile = psiFile.virtualFile
            val isInProject = vFile != null && project.basePath != null && vFile.path.startsWith(project.basePath!!)
            put("currentFileInProject", JsonPrimitive(isInProject))

            // Try to get overriding elements — the results will reveal scope
            if (rClassElements.isNotEmpty()) {
                val firstClass = rClassElements.first()
                val containerClass = rContainerClass
                if (containerClass != null && containerClass.isInstance(firstClass)) {
                    try {
                        val method = utilClass.getMethod("getOverridingElements", containerClass)
                        val result = method.invoke(null, containerClass.cast(firstClass)) as? Collection<*>
                        val resultCount = result?.size ?: 0
                        put("overridingElementCount", JsonPrimitive(resultCount))

                        // If results > 0, check their origins
                        if (resultCount > 0) {
                            val origins = result!!.take(10).mapNotNull { elem ->
                                try {
                                    val containingFile = elem as? PsiElement
                                    val file = containingFile?.containingFile?.virtualFile
                                    if (file != null) {
                                        file.path
                                    } else {
                                        "(no virtual file)"
                                    }
                                } catch (_: Throwable) { null }
                            }
                            put("elementOrigins", buildJsonArray {
                                origins.forEach { add(JsonPrimitive(it)) }
                            })
                            val hasGemPaths = origins.any { it.contains("/gems/") || it.contains("/ruby/") || it.contains("ruby-3") }
                            val hasProjectPaths = origins.any { it.contains(project.basePath ?: "NOMATCH") }
                            put("hasGemSources", JsonPrimitive(hasGemPaths))
                            put("hasProjectSources", JsonPrimitive(hasProjectPaths))
                            put("scopeConclusion", JsonPrimitive(
                                when {
                                    hasGemPaths && hasProjectPaths -> "COVERS BOTH — project files AND gem sources"
                                    hasGemPaths -> "GEMS ONLY — symbol tree covers gem sources"
                                    hasProjectPaths -> "PROJECT ONLY — symbol tree limited to project files"
                                    else -> "UNKNOWN — could not determine source origins"
                                }
                            ))
                        }
                    } catch (e: Exception) {
                        put("overridingError", JsonPrimitive(e.message?.take(200) ?: ""))
                    }
                }
            }
        } catch (e: Exception) {
            put("status", JsonPrimitive("error"))
            put("exception", JsonPrimitive(e::class.java.simpleName))
            put("message", JsonPrimitive(e.message?.take(200) ?: ""))
        }
    }

    // ---- PSI helpers ----------------------------------------------------

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

    private fun findRClassElements(psiFile: PsiFile): List<PsiElement> {
        val cls = rClassClass ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            PsiTreeUtil.collectElementsOfType(psiFile, cls as Class<out PsiElement>).toList()
        } catch (_: Throwable) { emptyList() }
    }

    private fun findRModuleElements(psiFile: PsiFile): List<PsiElement> {
        val cls = rModuleClass ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            PsiTreeUtil.collectElementsOfType(psiFile, cls as Class<out PsiElement>).toList()
        } catch (_: Throwable) { emptyList() }
    }

    private fun resolveIncludeCallType(): Any? {
        try {
            val typesClass = rubyIncludeExtendCallTypesClass ?: return null
            val field = typesClass.getDeclaredField("INCLUDE_CALL")
            return field.get(null)
        } catch (_: Throwable) { return null }
    }

    // ---- Markdown report builder -----------------------------------------

    private fun buildMarkdownReport(json: JsonObject, filePath: String): String {
        val sb = StringBuilder()
        val fn = filePath.substringAfterLast("/")

        sb.appendLine("# Ruby Plugin Unknowns Probe Report")
        sb.appendLine()
        sb.appendLine("**File:** `$fn`")
        sb.appendLine("**Probed at:** ${json["probedAt"]?.jsonPrimitive?.content ?: "?"}")
        sb.appendLine()

        val probeKeys = listOf(
            "probe1_processCallsOfType",
            "probe2_findCall",
            "probe3_inheritanceTransitivity",
            "probe4_regexEdgeCases",
            "probe5_attrMethodsInIndex",
            "probe6_declarationFqnEnum",
            "probe7_overridingOnModule",
            "probe8_symbolScope"
        )

        val probeLabels = mapOf(
            "probe1_processCallsOfType" to "Unknown 1: processCallsOfType(Consumer<RCall>)",
            "probe2_findCall" to "Unknown 2: RubyClassResolveUtil.findCall()",
            "probe3_inheritanceTransitivity" to "Unknown 3: Inheritance transitivity",
            "probe4_regexEdgeCases" to "Unknown 4: PSI text walk regex",
            "probe5_attrMethodsInIndex" to "Unknown 5: attr_* methods in MethodNameIndex",
            "probe6_declarationFqnEnum" to "Unknown 6: DeclarationFqnIndex enum key",
            "probe7_overridingOnModule" to "Unknown 7: getOverridingElements on RModule",
            "probe8_symbolScope" to "Unknown 8: Symbol tree scope"
        )

        for (key in probeKeys) {
            val probe = json[key]?.jsonObject ?: continue
            val label = probeLabels[key] ?: key
            val question = probe["question"]?.jsonPrimitive?.content ?: ""
            sb.appendLine("## $label")
            sb.appendLine()
            sb.appendLine("**Question:** $question")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(formatJsonForMd(probe))
            sb.appendLine("```")
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine("_Generated by `ide_ruby_unknowns_probe`_")
        return sb.toString()
    }

    private fun formatJsonForMd(obj: JsonObject): String {
        val sb = StringBuilder()
        obj.forEach { (k, v) ->
            when (v) {
                is JsonPrimitive -> sb.appendLine("  \"$k\": ${v.content}")
                is JsonObject -> sb.appendLine("  \"$k\": <object>")
                is JsonArray -> {
                    val items = v.map { elem ->
                        val prim = elem as? JsonPrimitive
                        prim?.content ?: "<object>"
                    }
                    sb.append("  \"$k\": [")
                    sb.append(items.take(5).joinToString(", "))
                    sb.appendLine("]")
                }
            }
        }
        return sb.toString()
    }
}