package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDateTime

/**
 * TEMPORARY RESEARCH TOOL — probes every handler-relevant Ruby stub index and
 * file-based index via reflection, writes a full Markdown report to
 * ruby/research/empirical_indexes_results.md, and returns a JSON summary.
 *
 * Requires the Ruby plugin (org.jetbrains.plugins.ruby) to be loaded.
 * Tears out cleanly: delete this file + revert ToolRegistry.kt + ToolNames.kt.
 */
class RubyIndexExplorerTool : AbstractMcpTool() {

    override val requiresPsiSync = false
    override val participatesInLifecycle = false

    override val name = "ide_ruby_index_explorer"

    override val description = """
        Probe all Ruby stub and file-based indexes via reflection and write results
        to ruby/research/empirical_indexes_results.md.

        Requires the Ruby plugin to be loaded. Reports key/value types, version,
        sample keys, and a known-key lookup for each index.

        Parameters:
        - project_path (optional): routing hint when multiple projects are open.
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool().projectPath().build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        if (!rubyPluginLoaded()) {
            return createJsonResult(buildJsonObject {
                put("error", "Ruby plugin not loaded. Cannot probe Ruby indexes.")
                put("rubyPluginLoaded", JsonPrimitive(false))
            })
        }

        val scope = GlobalSearchScope.allScope(project)
        val outputFile = File(project.basePath ?: ".", "ruby/research/empirical_indexes_results.md")
        outputFile.parentFile.mkdirs()

        val results = ALL_INDEXES.map { desc ->
            when (desc.type) {
                "stub" -> probeStubIndexReflective(desc, project, scope)
                "fileBased" -> probeFileBasedIndexReflective(desc, project)
                else -> ProbeResult(desc, errors = listOf("Unknown type: ${desc.type}"))
            }
        }

        // Write full Markdown report
        val markdown = buildMarkdownReport(project, results)
        outputFile.writeText(markdown)

        // Return JSON summary
        val summary = buildJsonObject {
            put("rubyPluginLoaded", JsonPrimitive(true))
            put("reportFile", outputFile.absolutePath)
            put("indexCount", JsonPrimitive(results.size))
            put("errorCount", JsonPrimitive(results.count { it.errors.isNotEmpty() }))
            put("indexes", buildJsonArray {
                results.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.index.simpleName)
                        put("type", r.index.type)
                        put("keyType", r.index.keyType)
                        put("valueType", r.index.valueType)
                        put("version", r.version?.toString() ?: "N/A")
                        put("keyCount", r.keyCount?.toString() ?: "N/A")
                        if (r.errors.isNotEmpty()) {
                            put("errors", buildJsonArray { r.errors.forEach { add(JsonPrimitive(it)) } })
                        }
                    })
                }
            })
        }

        return createJsonResult(summary)
    }

    // ---- Ruby plugin detection ---------------------------------------------------

    private fun rubyPluginLoaded(): Boolean = try {
        Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass")
        true
    } catch (_: Throwable) {
        false
    }

    // ---- Data classes ------------------------------------------------------------

    private data class IndexDescriptor(
        val className: String,
        val simpleName: String,
        val type: String,
        val keyType: String,
        val valueType: String,
        val innerClasses: List<String> = emptyList(),
        val staticMethods: List<String> = emptyList()
    )

    private data class ProbeResult(
        val index: IndexDescriptor,
        val version: Int? = null,
        val keyCount: Int? = null,
        val sampleKeys: List<String> = emptyList(),
        val sampleKeyValue: String? = null,
        val indexedFileCount: Int? = null,
        val errors: List<String> = emptyList()
    )

    // ---- Stub index probe (fully reflective) -------------------------------------

    private fun probeStubIndexReflective(
        desc: IndexDescriptor,
        project: Project,
        scope: GlobalSearchScope
    ): ProbeResult {
        val cls: Class<*>
        try {
            cls = Class.forName(desc.className)
        } catch (e: Throwable) {
            return ProbeResult(desc, errors = listOf("ClassNotFound: ${e.message}"))
        }

        val errors = mutableListOf<String>()

        // Get version and instance
        val instance = try {
            val instMethod = cls.getMethod("getInstance")
            instMethod.invoke(null)
        } catch (_: NoSuchMethodException) {
            null
        }

        val version: Int? = try {
            val verMethod = cls.getMethod("getVersion")
            if (instance != null) {
                verMethod.invoke(instance) as? Int
            } else {
                verMethod.invoke(cls.getDeclaredConstructor().newInstance()) as? Int
            }
        } catch (_: Exception) { null }

        // Get KEY constant
        var keyField: java.lang.reflect.Field? = null
        val keyName: String = try {
            keyField = cls.getDeclaredField("KEY")
            keyField!!.isAccessible = true
            val key = keyField!!.get(null)
            key!!::class.java.getMethod("getName").invoke(key) as String
        } catch (e: Exception) {
            errors.add("No KEY field: ${e.message}")
            return ProbeResult(desc, version = version, errors = errors)
        }

        // Use StubIndex API directly (available at compile time)
        val maxKeys = if (keyName.contains("Require", ignoreCase = true)) 30 else 20
        val keys = mutableListOf<String>()

        try {
            ReadAction.run<RuntimeException> {
                @Suppress("UNCHECKED_CAST")
                val skey = keyField!!.get(null) as StubIndexKey<String, *>
                StubIndex.getInstance().processAllKeys(
                    skey, Processor { k: String ->
                        if (keys.size < maxKeys) {
                            keys.add(k)
                            true
                        } else false
                    }, scope, null
                )
            }
        } catch (e: Exception) {
            errors.add("processAllKeys (samples) failed: ${e.message}")
        }

        // Count all keys (up to 10000)
        var keyCount: Int? = null
        if (keys.isNotEmpty()) {
            try {
                val all = mutableListOf<String>()
                ReadAction.run<RuntimeException> {
                    @Suppress("UNCHECKED_CAST")
                    val skey = keyField!!.get(null) as StubIndexKey<String, *>
                    StubIndex.getInstance().processAllKeys(
                        skey, Processor { k: String ->
                            all.add(k)
                            all.size < 10000
                        }, scope, null
                    )
                }
                keyCount = all.size
            } catch (_: Exception) { /* best effort */ }
        }

        // Try a known-key lookup using reflection on the specific index class
        var sampleValues: String? = null
        if (keys.isNotEmpty()) {
            try {
                ReadAction.run<RuntimeException> {
                    // Ruby index instances have getElements methods
                    val getElementsMethod = cls.methods.firstOrNull { it.name == "getElements" }
                    if (getElementsMethod != null && instance != null) {
                        val params = mutableListOf<Any>()
                        for (pt in getElementsMethod.parameterTypes) {
                            when {
                                Project::class.java.isAssignableFrom(pt) -> params.add(project)
                                GlobalSearchScope::class.java.isAssignableFrom(pt) -> params.add(scope)
                                pt.name.contains("SearchScope") -> params.add(scope)
                                String::class.java.isAssignableFrom(pt) ||
                                    pt.name.contains("FQN") ||
                                    pt.name.contains("String") -> params.add(keys.first())
                                else -> params.add(keys.first())
                            }
                        }
                        val result = getElementsMethod.invoke(instance, *params.toTypedArray()) as? Collection<*>
                        val count = result?.size ?: 0
                        val firstPsi = result?.firstOrNull()
                        val firstName = if (firstPsi != null) firstPsi::class.java.simpleName else "null"
                        sampleValues = "count=$count, first=$firstName: '$firstPsi'"
                    } else {
                        sampleValues = "no getElements method found"
                    }
                }
            } catch (e: Exception) {
                sampleValues = "lookup failed: ${e.message}"
            }
        }

        return ProbeResult(
            index = desc,
            version = version,
            keyCount = keyCount ?: keys.size,
            sampleKeys = keys,
            sampleKeyValue = sampleValues,
            errors = errors
        )
    }

    // ---- File-based index probe (reflective) -------------------------------------

    private fun probeFileBasedIndexReflective(
        desc: IndexDescriptor,
        project: Project
    ): ProbeResult {
        val cls: Class<*>
        try {
            cls = Class.forName(desc.className)
        } catch (e: Throwable) {
            return ProbeResult(desc, errors = listOf("ClassNotFound: ${e.message}"))
        }

        val errors = mutableListOf<String>()
        var version: Int? = null
        val allKeys = mutableListOf<String>()

        try {
            val instance = cls.getDeclaredConstructor().newInstance()

            try {
                val verMethod = cls.getMethod("getVersion")
                version = verMethod.invoke(instance) as? Int
            } catch (_: Exception) { /* no version */ }

            // Get the ID/name to use with FileBasedIndex
            val nameMethod = cls.getMethod("getName")
            val nameObj = nameMethod.invoke(instance)

            @Suppress("UNCHECKED_CAST")
            val indexId = nameObj as? ID<String, *>
            if (indexId != null) {
                @Suppress("UNCHECKED_CAST")
                val sid = indexId as ID<String, Any>
                ReadAction.run<RuntimeException> {
                    FileBasedIndex.getInstance().processAllKeys(
                        sid, Processor { key: String ->
                            if (allKeys.size < 20) {
                                allKeys.add(key.toString())
                                true
                            } else false
                        }, project
                    )
                }
            }
        } catch (e: Exception) {
            errors.add("Probe failed: ${e.message}")
        }

        return ProbeResult(
            index = desc,
            version = version,
            keyCount = allKeys.size,
            sampleKeys = allKeys,
            errors = errors
        )
    }

    // ---- Markdown report generation ----------------------------------------------

    private fun buildMarkdownReport(project: Project, results: List<ProbeResult>): String {
        val sb = StringBuilder()

        sb.appendLine("# Ruby Plugin Index Explorer")
        sb.appendLine()
        sb.appendLine("**Project:** `${project.name}`")
        sb.appendLine("**Ruby plugin loaded:** true")
        sb.appendLine("**Probed at:** ${LocalDateTime.now()}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("| Index | Type | Version | Keys Found | Status |")
        sb.appendLine("|---|---|---|---|---|")
        results.forEach { r ->
            val status = when {
                r.errors.isNotEmpty() -> ":x: ${r.errors.first().take(60)}"
                r.keyCount != null && r.keyCount > 0 -> ":white_check_mark:"
                r.keyCount == 0 -> ":warning: empty"
                else -> ":grey_question:"
            }
            sb.appendLine("| `${r.index.simpleName}` | ${r.index.type} | ${r.version?.toString() ?: "?"} | ${r.keyCount?.toString() ?: "?"} | $status |")
        }
        sb.appendLine()

        sb.appendLine("## Detailed Index Probe Results")
        sb.appendLine()

        val categories = mapOf(
            "Class/Module Resolution" to listOf("RubyClassModuleNameIndex", "RubyMethodNameIndex", "RubySymbolNameIndex", "RubyConstantDeclarationFqnIndex", "RubyGlobalVariableDeclarationNameIndex"),
            "Inheritance / Type Hierarchy" to listOf("RubyInheritanceIndex", "RubyInheritanceResolutionIndex", "RubyInheritanceResolutionIndex\$ForSuperClasses", "RubyIncludedExtendedFQNIndex", "RubyAnonymousDefiningCallIndex", "RubyAnonymousDeclarationSuperclassIndex"),
            "General Resolution" to listOf("RubyResolutionIndex", "RubyResolutionIndex\$ForCompletion", "RubyResolutionIndex\$ForDocumentation", "RubyDynamicMethodsDeclarationsIndex"),
            "Require / Variable Tracking" to listOf("RubyRequireLoadIndex", "RubyAllInstanceVariablesIndex"),
            "File-Based Declaration Indexes" to listOf("RubyDeclarationFqnIndex", "RubyDeclarationSuperclassIndex", "RubyDeclarationHierarchyIndex")
        )

        categories.forEach { (category, simpleNames) ->
            sb.appendLine("### $category")
            sb.appendLine()
            val catResults = results.filter { r -> simpleNames.any { r.index.simpleName == it } }
            catResults.forEach { sb.append(toMarkdownSection(it)) }
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.appendLine("## Analysis & Key Takeaways")
        sb.appendLine()
        sb.appendLine("### Stub Index Base Classes")
        sb.appendLine()
        sb.appendLine("All Ruby stub indexes inherit from one of two abstract bases:")
        sb.appendLine()
        sb.appendLine("| Base Class | Key Type | Key Features |")
        sb.appendLine("|---|---|---|")
        sb.appendLine("| `RubyStringStubIndexExtension<E>` | String | `getAllValidKeys()`, `containsElements()`, `findElement()` |")
        sb.appendLine("| `RubyFqnStubIndexExtension<E>` (extends RubyStringStubIndexExtension) | String (FQN) | `containsElements(FQN)`, `findElement(FQN)`, `getElements(FQN)` |")
        sb.appendLine()
        sb.appendLine("### Expected Patterns")
        sb.appendLine()
        sb.appendLine("- FQN-keyed indexes (RubyFqnStubIndexExtension): `RubyInheritanceIndex`, `RubyInheritanceResolutionIndex`(+ForSuperClasses), `RubyIncludedExtendedFQNIndex`, `RubyResolutionIndex`(+ForCompletion/+ForDocumentation), `RubyAnonymousDefiningCallIndex`, `RubyDynamicMethodsDeclarationsIndex`, `RubyAllInstanceVariablesIndex`")
        sb.appendLine("- Simple-name-keyed (RubyStringStubIndexExtension): `RubyClassModuleNameIndex`, `RubySymbolNameIndex`, `RubyRequireLoadIndex`, `RubyGlobalVariableDeclarationNameIndex`")
        sb.appendLine("- Direct StringStubIndexExtension: `RubyMethodNameIndex`")
        sb.appendLine()
        sb.appendLine("### Key Relationships for Handler Implementation")
        sb.appendLine()
        sb.appendLine("```mermaid")
        sb.appendLine("graph TD")
        sb.appendLine("    A[RubyClassModuleNameIndex] -->|name -> class/module| B[resolve class/module]")
        sb.appendLine("    C[RubyInheritanceIndex] -->|subclass FQN -> elements| D[find subclasses]")
        sb.appendLine("    E[RubyInheritanceResolutionIndex\$ForSuperClasses] -->|super FQN -> subclasses| D")
        sb.appendLine("    F[RubyIncludedExtendedFQNIndex] -->|module FQN -> includers| G[find mixin users]")
        sb.appendLine("    H[RubyMethodNameIndex] -->|name -> RMethod| I[find methods by name]")
        sb.appendLine("    J[RubyDeclarationFqnIndex] -->|Type -> FQNs in file| K[file-level declaration scan]")
        sb.appendLine("```")
        sb.appendLine()

        return sb.toString()
    }

    private fun toMarkdownSection(r: ProbeResult): String {
        val sb = StringBuilder()
        sb.appendLine("### ${r.index.simpleName}")
        sb.appendLine()
        sb.appendLine("| Property | Value |")
        sb.appendLine("|---|---|")
        sb.appendLine("| **Type** | `${r.index.type}` |")
        sb.appendLine("| **Key** | `${r.index.keyType}` |")
        sb.appendLine("| **Value** | `${r.index.valueType}` |")
        if (r.index.innerClasses.isNotEmpty()) {
            sb.appendLine("| **Inner types** | ${r.index.innerClasses.joinToString(", ") { "`$it`" }} |")
        }
        if (r.index.staticMethods.isNotEmpty()) {
            sb.appendLine("| **Helper methods** | ${r.index.staticMethods.joinToString("<br>") { "`$it`" }} |")
        }
        sb.appendLine("| **Version** | `${r.version?.toString() ?: "N/A"}` |")
        sb.appendLine()

        if (r.errors.isNotEmpty()) {
            sb.appendLine("**Errors:**")
            r.errors.forEach { sb.appendLine("- `$it`") }
            sb.appendLine()
            return sb.toString()
        }

        sb.appendLine("**All keys count:** ${r.keyCount?.toString() ?: "N/A"}")
        sb.appendLine()
        sb.appendLine("**Sample keys (${r.sampleKeys.size} shown):**")
        sb.appendLine()
        sb.appendLine("```")
        if (r.sampleKeys.isEmpty()) {
            sb.appendLine("(no keys found)")
        } else {
            r.sampleKeys.forEach { sb.appendLine("  $it") }
        }
        sb.appendLine("```")
        sb.appendLine()

        if (r.sampleKeyValue != null) {
            val firstKey = r.sampleKeys.firstOrNull() ?: "N/A"
            sb.appendLine("**Sample lookup:** key=`$firstKey` → $r.sampleKeyValue")
            sb.appendLine()
        }

        return sb.toString()
    }

    // ---- Index definitions (from bytecode analysis) ------------------------------

    companion object {
        private val ALL_INDEXES = listOf(
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyClassModuleNameIndex",
                simpleName = "RubyClassModuleNameIndex",
                type = "stub",
                keyType = "String (simple name)",
                valueType = "RElementWithFQN (class/module PSI)",
                staticMethods = listOf(
                    "getInstance()", "getKey()", "getVersion()",
                    "find(project, name, scope) -> Collection<RElementWithFQN>",
                    "findOne(project, name, scope, predicate) -> RElementWithFQN"
                )
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyMethodNameIndex",
                simpleName = "RubyMethodNameIndex",
                type = "stub",
                keyType = "String (method name, e.g., 'find', 'admin?', 'save!')",
                valueType = "RMethod (method PSI element)",
                staticMethods = listOf("getInstance()", "getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubySymbolNameIndex",
                simpleName = "RubySymbolNameIndex",
                type = "stub",
                keyType = "String (symbol name, simple)",
                valueType = "RPsiElement (general PSI element)",
                staticMethods = listOf("getInstance()", "getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyConstantDeclarationFqnIndex",
                simpleName = "RubyConstantDeclarationFqnIndex",
                type = "stub",
                keyType = "String (FQN, e.g. 'MyApp::CONST')",
                valueType = "RConstant (constant PSI element)",
                staticMethods = listOf("getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyGlobalVariableDeclarationNameIndex",
                simpleName = "RubyGlobalVariableDeclarationNameIndex",
                type = "stub",
                keyType = "String (variable name, e.g. '\$redis', '\$logger')",
                valueType = "RPsiElement (global variable PSI)",
                staticMethods = listOf("getInstance()", "getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyInheritanceIndex",
                simpleName = "RubyInheritanceIndex",
                type = "stub",
                keyType = "String (FQN of subclass, e.g. 'MyClass')",
                valueType = "RPsiElement (the inheriting element)",
                staticMethods = listOf("getInstance()", "getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyInheritanceResolutionIndex",
                simpleName = "RubyInheritanceResolutionIndex",
                type = "stub",
                keyType = "String (FQN for resolution lookup)",
                valueType = "RPsiElement",
                staticMethods = listOf("getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyInheritanceResolutionIndex\$ForSuperClasses",
                simpleName = "RubyInheritanceResolutionIndex\$ForSuperClasses",
                type = "stub",
                keyType = "String (FQN of superclass)",
                valueType = "RPsiElement (subclass elements)",
                staticMethods = listOf("getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyIncludedExtendedFQNIndex",
                simpleName = "RubyIncludedExtendedFQNIndex",
                type = "stub",
                keyType = "String (FQN of included/extended module)",
                valueType = "RPsiElement (elements that include/extend it)",
                staticMethods = listOf("getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyAnonymousDefiningCallIndex",
                simpleName = "RubyAnonymousDefiningCallIndex",
                type = "stub",
                keyType = "String (FQN of anonymous definition context)",
                valueType = "RPossibleCall (anonymous call element)",
                staticMethods = listOf("getInstance()", "getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyAnonymousDeclarationSuperclassIndex",
                simpleName = "RubyAnonymousDeclarationSuperclassIndex",
                type = "fileBased",
                keyType = "String",
                valueType = "unknown",
                staticMethods = emptyList()
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyResolutionIndex",
                simpleName = "RubyResolutionIndex",
                type = "stub",
                keyType = "String (FQN for general resolution)",
                valueType = "RPsiElement",
                staticMethods = listOf("getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyResolutionIndex\$ForCompletion",
                simpleName = "RubyResolutionIndex\$ForCompletion",
                type = "stub",
                keyType = "String (FQN for completion)",
                valueType = "RPsiElement",
                staticMethods = listOf("getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyResolutionIndex\$ForDocumentation",
                simpleName = "RubyResolutionIndex\$ForDocumentation",
                type = "stub",
                keyType = "String (FQN for documentation)",
                valueType = "RPsiElement",
                staticMethods = listOf("getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDynamicMethodsDeclarationsIndex",
                simpleName = "RubyDynamicMethodsDeclarationsIndex",
                type = "stub",
                keyType = "String (FQN of class/module with dynamic methods)",
                valueType = "RPsiElement (dynamic method declarations)",
                staticMethods = listOf("getInstance()", "getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyRequireLoadIndex",
                simpleName = "RubyRequireLoadIndex",
                type = "stub",
                keyType = "String (require/load path, e.g. 'active_support/core_ext')",
                valueType = "RPossibleCall (the require/load call PSI)",
                staticMethods = listOf("getInstance()", "getKey()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyAllInstanceVariablesIndex",
                simpleName = "RubyAllInstanceVariablesIndex",
                type = "stub",
                keyType = "String (FQN of containing class/module)",
                valueType = "RPsiElement (instance variable references)",
                staticMethods = listOf("getInstance()", "getKey()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDeclarationFqnIndex",
                simpleName = "RubyDeclarationFqnIndex",
                type = "fileBased",
                keyType = "RubyDeclarationFqnIndex.Type (CLASS|MODULE|CONSTANT)",
                valueType = "List<String> (FQN strings for each declaration of that type in the file)",
                innerClasses = listOf("Type: CLASS, MODULE, CONSTANT"),
                staticMethods = listOf("getName()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDeclarationSuperclassIndex",
                simpleName = "RubyDeclarationSuperclassIndex",
                type = "fileBased",
                keyType = "String (FQN of class)",
                valueType = "List<Pair<FQN, String>> (superclass FQN + optional second value)",
                staticMethods = listOf("getName()", "getVersion()")
            ),
            IndexDescriptor(
                className = "org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDeclarationHierarchyIndex",
                simpleName = "RubyDeclarationHierarchyIndex",
                type = "fileBased",
                keyType = "String (FQN of declaration)",
                valueType = "List<Pair<Type, String>> (hierarchy type + referenced FQN)",
                innerClasses = listOf("Type: CLASS, MODULE, CONSTANT"),
                staticMethods = listOf("getName()", "getVersion()")
            )
        )
    }
}