# Research Instructions: MCP Tool Analysis for Ruby Integration (Greenfield)

> **Goal:** For each MCP tool exposed by this repo, produce a single document in
> `lessons_learned/mcp_tools/` analyzing: what it does, how Java/Python/PHP implement
> it, what the equivalent Ruby calls would be, and how to implement it.
>
> **Greenfield version:** Assumes no Ruby code exists yet. All Ruby handlers,
> indexes, and integration must be built from scratch.

---

## 0. Prerequisite: Know the tool list

The canonical tool list is at `ToolNames.ALL`:
```
src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/ToolNames.kt
```

Tool implementations live under:
```
src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/
├── navigation/     ←  Most tools using language handlers
├── editor/
├── intelligence/
├── project/
├── refactoring/
├── lifecycle/      ←  Skip these (no language logic)
└── schema/
```

**Skip lifecycle tools** (`ide_get_project_modes`, `ide_set_project_mode`, etc.) —
they have no language-specific logic.

**Skip infrastructure tools** that use only platform APIs:
- `ide_find_file` — file name index (platform, works for any file)
- `ide_search_text` — word index (platform, works for any text)
- `ide_read_file` — file content reader (no PSI needed)
- `ide_diagnostics` — code analysis (platform)
- `ide_index_status` — indexing status (platform)
- `ide_sync_files` — VFS sync (platform)
- `ide_reformat_code` — platform formatter
- `ide_build_project`, `ide_reload_project` — build system (platform)
- `ide_open_file`, `ide_get_active_file` — editor (platform)

---

## 1. Research sources — where to look

### 1a. The tool implementation itself
```
src/main/kotlin/.../tools/{category}/{ToolName}Tool.kt
```
Read the FULL file. Focus on:
- `doExecute()` — the main logic
- What `LanguageHandlerRegistry` calls it makes
- What platform APIs it uses directly (`ReferencesSearch`, `DefinitionsScopedSearch`, etc.)
- Any language-specific branches or imports

### 1b. Java handler (direct PSI, no reflection)
```
src/main/kotlin/.../handlers/java/JavaHandlers.kt
```
Java is the "clean" reference — uses compile-time PSI imports.
Works for Java AND Kotlin.

### 1c. Python handler (reflection, closest analog to Ruby)
```
src/main/kotlin/.../handlers/python/PythonHandlers.kt
```
Uses reflection like Ruby will. Good structural analog.

### 1d. PHP handler (reflection + PhpIndex)
```
src/main/kotlin/.../handlers/php/PhpHandlers.kt
```

### 1e. Rust handler (reflection, no class inheritance — edge case reference)
```
src/main/kotlin/.../handlers/rust/RustHandlers.kt
```

### 1f. Ruby plugin extension points (the goldmine)
The closed-source Ruby plugin registers its capabilities in `plugin.xml`.
**File:** `/Users/dueberb/devel/ai/jetbrains-index-mcp-plugin-support/jetbrains-index-mcp-plugin-ruby-live-tests/ruby-plugin/plugin.xml`

**Complete list of Ruby-specific indexes and contributors registered in plugin.xml:**

Note that we don't know that all of these work the way we think they should.


| Extension point | Implementation class | What it provides |
|----------------|---------------------|-----------------|
| `lang.findUsagesProvider` | `RubyFindUsagesProvider` | Find usages of any Ruby symbol |
| `gotoClassContributor` | `RubyGotoClassContributor` | Go to Class (Ctrl+N) by name |
| `gotoSymbolContributor` | `RubyGotoSymbolContributor` | Go to Symbol (Ctrl+Shift+Alt+N) by name |
| `fileBasedIndex` | `RubyAnonymousDeclarationSuperclassIndex` | Superclass for anonymous declarations |
| `fileBasedIndex` | `RubyDeclarationFqnIndex` | FQN index for declarations |
| `fileBasedIndex` | `RubyDeclarationHierarchyIndex` | Hierarchy relationships |
| `fileBasedIndex` | `RubyDeclarationSuperclassIndex` | Superclass for each declaration |
| `fileBasedIndex` | `RDocFormatIndex` | RDoc documentation format |
| `fileBasedIndex` | `RailsRubySchemaVersionIndex` | Rails schema version |
| `stubIndex` | `RubyConstantDeclarationFqnIndex` | Constants by FQN |
| `stubIndex` | `RubyGlobalVariableDeclarationNameIndex` | Global vars by name |
| `stubIndex` | **`RubyClassModuleNameIndex`** | **Classes/modules by name — key for find_class** |
| `stubIndex` | `RubyAnonymousDefiningCallIndex` | Anonymous defining calls |
| `stubIndex` | `RubySymbolNameIndex` | Symbols by name |
| `stubIndex` | `RubyDynamicMethodsDeclarationsIndex` | Dynamic method declarations |
| `stubIndex` | `RenderCallIndex` | Rails render calls |
| `stubIndex` | **`RubyInheritanceIndex`** | **Inheritance relationships — key for type_hierarchy** |
| `stubIndex` | `RubyRequireLoadIndex` | require/load statements |
| `stubIndex` | `RubyAllInstanceVariablesIndex` | All instance variables |
| `stubIndex` | **`RubyInheritanceResolutionIndex`** | **Resolution of inherited members** |
| `stubIndex` | `RubyInheritanceResolutionIndex$ForSuperClasses` | Superclass resolution |
| `stubIndex` | `RubyIncludedExtendedFQNIndex` | Included/extended module FQNs |
| `stubIndex` | **`RubyResolutionIndex`** | **Symbol resolution — key for find_definition** |
| `stubIndex` | `RubyResolutionIndex$ForCompletion` | Resolution for completion |
| `stubIndex` | `RubyResolutionIndex$ForDocumentation` | Resolution for docs |

### 1g. Extracted XMLs from Ruby plugin JARs
```
/tmp/ruby/lib/modules/    (60 XML files total)
```
Key files for PSI research:
- `/tmp/ruby/lib/modules/intellij.ruby.psi/intellij.ruby.psi.xml`
- `/tmp/ruby/lib/modules/intellij.ruby.psi.impl/intellij.ruby.psi.impl.xml`
- `/tmp/ruby/lib/modules/intellij.ruby.core/intellij.ruby.core.xml`
- `/tmp/ruby/lib/modules/intellij.ruby.impl/intellij.ruby.impl.xml`

Search these for class names and extension points. The PSI XMLs may not contain
`stubIndex`/`fileBasedIndex` declarations (those are in plugin.xml) but they DO
contain the classes that IMPLEMENT the PSI — useful for confirming class FQNs.

### 1h. Installed Ruby plugin JARs (last resort)
```
~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/
├── ruby.jar       — main plugin JAR
└── modules/       — sub-module JARs
```
Use `jar tf` to list classes, e.g.:
```bash
jar tf ruby.jar | grep -iE "search|index|hierarchy|resolve|override|implement"
```

### 1i. Ruby plugin source files (from the installed plugin)
```
~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/
├── rb/            — Ruby source files shipped with the plugin
├── rubysigs/      — RBS type signatures
└── rubystubs/     — Ruby stub files
```
These may contain Ruby-side implementations that hint at Java-side structures.

### 1j. Live test repo
```
/Users/dueberb/devel/ai/jetbrains-index-mcp-plugin-support/jetbrains-index-mcp-plugin-ruby-live-tests/
├── plans/full-integration-suite.md    — test runbook (14 tools, ~75 cases)
├── fixtures/                          — Ruby test files (inheritance, calls, namespaced, structure, rename)
└── ruby-plugin/plugin.xml             — extracted plugin descriptor
```

---

## 2. Research methodology — step by step

For EACH tool, follow this sequence:

### Step 1: Identify the tool
- Read the tool's `override val name` and `override val description`
- Note which handler interfaces it delegates to (if any)
- Note any language-specific branches or imports

### Step 2: Trace Java implementation
- In `JavaHandlers.kt`, find the corresponding handler class
- Note the EXACT PSI methods called (e.g., `PsiClass.getSuperClass()`, `InheritanceUtil.getDeepestSuperMethods()`)
- Judge: "clean" = direct PSI API; "messy" = multiple fallbacks, reflection, or manual tree walks

### Step 3: Trace Python implementation
- In `PythonHandlers.kt`, find the corresponding handler class
- Note which PSI classes (`PyClass`, `PyFunction`, etc.) and search utilities (`PyClassInheritorsSearch`, `PyOverridingMethodsSearch`) are used
- Judge cleanliness — Python uses reflection, so "clean" here means a single well-defined reflection call

### Step 4: Trace PHP implementation
- In `PhpHandlers.kt`, find the corresponding handler class
- Note `PhpIndex` usage pattern (PHP has a central index API)
- Judge cleanliness

### Step 5: Find Ruby equivalents
**Heuristic — pattern matching across ecosystems:**
- Python `PyClassInheritorsSearch` → Ruby: `RubyInheritanceIndex` or `RubyInheritanceResolutionIndex`
- Python `PyOverridingMethodsSearch` → Ruby: Check `RubyDeclarationHierarchyIndex` or search for "overriding"/"override" in ruby.jar classes
- Python `PyClassNameIndex` → Ruby: `RubyClassModuleNameIndex`
- Python `PyStaticCallHierarchyUtil` → Ruby: Search for "call" + "hierarchy" in ruby.jar; try `ReferencesSearch` as fallback
- Java `InheritanceUtil.getDeepestSuperMethods()` → Ruby: Search for `RubyOverrideImplementUtil` or `RubyInheritanceResolutionIndex$ForSuperClasses`
- Generic `DefinitionsScopedSearch` → Ruby: may have its own implementation, but generic platform API works as fallback
- Generic `ReferencesSearch` → Ruby: should work via `RubyFindUsagesProvider`
- FQN resolution → Ruby: `RubyDeclarationFqnIndex`, `RubyConstantDeclarationFqnIndex`, or direct PSI method

**How to discover if a Ruby class exists:**
1. Check `plugin.xml` for extension point declarations matching the pattern
2. Try `jar tf` on ruby.jar for likely FQNs
3. Try loading via reflection with `Class.forName()`
4. Search extracted XMLs for class names

**Name mapping rules (apply these when searching for Ruby equivalents):**
- Python: `PyXxx` → Ruby: `RXxx` (e.g., `PyClass` → `RClass`)
- Python: `PyXxxSearch` → Ruby: `RubyXxxSearch` (e.g., `PyClassInheritorsSearch` → try `RubyClassInheritorsSearch`)
- Python: `com.jetbrains.python.psi.search.Xxx` → Ruby: `org.jetbrains.plugins.ruby.ruby.lang.search.Xxx`
- Java platform: `com.intellij.psi.search.Xxx` → Same for Ruby (these are platform APIs)
- Language indexes: `XxxIndex` → `RubyXxxIndex` (confirm against plugin.xml list above)
- Resolution utilities: `XxxResolveUtil` → `RubyXxxResolveUtil`
- Override search: `XxxOverridingMethodsSearch` → `RubyOverridingMethodsSearch`
- Implementation search: `XxxClassInheritorsSearch` → `RubyClassInheritorsSearch`

### Step 6: Determine implementation strategy
Which approach is best for this tool?

| Strategy | When to use | Risk |
|----------|-------------|------|
| Ruby-specific index/stub call | The index FQN is confirmed via `Class.forName()` test | May vary across plugin versions |
| Ruby PSI method (reflection) | Method is confirmed on PSI class (`RClass`, `RModule`, `RMethod`) | Method signature may differ; wrap in `runCatching` |
| Generic platform API | No Ruby-specific equivalent found | Slower, but guaranteed to work |
| Manual PSI tree walk | Everything else fails; no usable index or API | Slowest, most fragile |

Preference order: Ruby-specific index > Ruby PSI method > generic platform API > manual PSI walk.

### Step 7: Document findings
Use the template below.

---

## 3. Output format — template for each tool document

Save as `ruby/research/mcp_tools/{tool_name}.md`.

```markdown
# {tool_name} — {short description}

> **Tool file:** `src/main/kotlin/.../tools/{category}/{ToolName}Tool.kt`
> **Handler interfaces used:** {list or "none (platform API only)"}

## What it does

{Brief 1-2 sentence description of the tool's purpose}

## How each language implements it

| Language | Cleanliness | Mechanism | Key classes/calls |
|----------|-------------|-----------|-------------------|
| Java | {clean/messy} | {description} | `{class.method()}` |
| Python | {clean/messy} | {description} | `{class.method()}` |
| PHP | {clean/messy} | {description} | `{class.method()}` |

**Cleanliness scale:**
- **clean** = direct 1-1 call to a plugin-provided PSI method or search utility
- **semi-clean** = generic platform API (`ReferencesSearch`, `DefinitionsScopedSearch`) — works for any language but less optimized
- **messy** = manual PSI tree walk, multiple fallbacks, workarounds for bugs

### Java details
```kotlin
// Exact code path or key calls
```

### Python details
```kotlin
// Exact code path or key calls
```

### PHP details
```kotlin
// Exact code path or key calls
```

## Ruby implementation plan

### Plugin capabilities relevant to this tool
| Index/Contributor | What it provides | Relevance |
|-------------------|-----------------|-----------|
| `{IndexName}` | {description} | {direct/partial/none} |

### Recommended approach
```kotlin
// The ideal Ruby implementation
```

### Fallback approach (if primary unavailable)
```kotlin
// What to do if the ideal approach fails at runtime
```

### Implementation steps
1. {Step 1 — e.g., register a new handler class in RubyHandlers}
2. {Step 2 — e.g., implement the LanguageHandler<T> interface}
3. {Step 3 — e.g., wire into LanguageHandlerRegistry}
4. {Step 4 — e.g., update tool description strings}

### Ruby language semantics to handle
- {Edge case 1 — e.g., single inheritance vs multiple}
- {Edge case 2 — e.g., modules as both namespaces and mixins}
- {Edge case 3 — e.g., predicate/bang method suffixes}

### Risks and unknowns
- {Risk 1 — e.g., unconfirmed class FQN, needs `Class.forName()` test}
- {Risk 2 — e.g., method may differ across Ruby plugin versions}

## Search commands for further investigation
```bash
# Find relevant Ruby plugin classes
jar tf ~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/ruby.jar | grep -iE "{patterns}"

# Check plugin.xml for matching extension points
grep -iE "{patterns}" "/Users/dueberb/devel/ai/jetbrains-index-mcp-plugin-support/jetbrains-index-mcp-plugin-ruby-live-tests/ruby-plugin/plugin.xml"

# Search extracted XMLs for PSI implementations
grep -rl "{patterns}" /tmp/ruby/lib/modules/
```
```

---

## 4. Heuristics and tips

### Which tools need language-specific research?
Only tools that delegate to `LanguageHandlerRegistry` or have language-specific branches:
- `ide_type_hierarchy` — TypeHierarchyHandler
- `ide_call_hierarchy` — CallHierarchyHandler
- `ide_find_implementations` — ImplementationsHandler
- `ide_find_super_methods` — SuperMethodsHandler
- `ide_file_structure` — StructureHandler
- `ide_find_class` — may need language-specific FQN resolution
- `ide_find_symbol` — uses OptimizedSymbolSearch (auto-supports any language with ChooseByNameContributor)
- `ide_find_references` — uses ReferencesSearch + SymbolReferenceHandler
- `ide_find_definition` — uses DefinitionsScopedSearch + SymbolReferenceHandler

Everything else (editor, project, build, lifecycle, refactoring, diagnostics) uses only
platform APIs and needs no Ruby-specific work.

### "Clean" vs "messy" scoring
- **Clean**: Single call to a language-specific API that the plugin explicitly provides.
  Example: `PyClassInheritorsSearch.search(pyClass, scope, true).findAll()`
- **Semi-clean**: Generic platform API — works for any language but less optimized.
  Example: `DefinitionsScopedSearch.search(element, scope).findAll()`
- **Messy**: Manual PSI tree walks, multi-level fallbacks, workarounds.
  Example: walking `element.parent` chain because the language plugin's FQN method returns null.

### When to prefer a Ruby-specific index vs generic platform API
- Ruby-specific index: faster, but FQN must be confirmed via reflection test, may vary across plugin versions
- Generic platform API (`ReferencesSearch`, `DefinitionsScopedSearch`): slower, but always works
- Rule: prefer Ruby-specific if the FQN is confirmed and the index provides strictly more data.
  Fall back to generic platform API otherwise. Always include the fallback path in the handler.

### Reflection safety — CRITICAL
- Every `Class.forName()`, `getMethod()`, `.invoke()` MUST be wrapped in `runCatching`
- Never import Ruby PSI classes directly — that creates a compile-time dependency that breaks non-Ruby IDEs
- Always include fallback paths for when the Ruby plugin is not installed
- Check `PluginDetectors.ruby.isAvailable` before attempting any Ruby-specific operation

### Ruby language model
- `element.language.id` returns `"ruby"` (lowercase) for Ruby files
- **PSI classes** (confirmed FQNs):
  - `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass`
  - `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule`
  - `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod`
- **Language semantics**: single inheritance (`class Child < Parent`), mixins (`include`/`extend`), modules serve as both namespaces and mixins
- **Known gotcha**: `RClass.getQualifiedName()` may return `null` for module-namespaced classes — always include FQN reconstruction via PSI ancestor walk as fallback

### Handler registration — what to do for each new tool

For each handler type the tool needs, you must:
1. Create a handler class extending `BaseRubyHandler<T>` and implementing the handler interface
2. Register it in the `RubyHandlers.register()` method
3. Ensure `RubyHandlers` is listed in `LanguageHandlerRegistry.handlerRegistrations`
4. Update tool description strings to list "Ruby"

### Test strategy per tool
- Unit tests: test symbol patterns, FQN computation, parsing logic (no IntelliJ runtime needed)
- Platform tests: test handler execution with real Ruby PSI (CI only — requires Ruby plugin)
- Live tests: test full MCP tool calls against a running RubyMine/IntelliJ with Ruby plugin

---

## 5. Quick-start: research a single tool from scratch

Use `intellij-index` MCP tools for all codebase research. All examples use `project_path: "/Users/dueberb/devel/ai/jbimcp"`.

```
# 1. Read the tool implementation
intellij_index_ide_read_file(file: "src/main/kotlin/.../tools/navigation/{ToolName}Tool.kt")

# 2. Check what handler it uses
intellij_index_ide_search_text(
  query: "getHandler|LanguageHandlerRegistry",
  file: "src/main/kotlin/.../tools/navigation/{ToolName}Tool.kt"
)

# 3. Find the Java implementation
intellij_index_ide_read_file(
  file: "src/main/kotlin/.../handlers/java/JavaHandlers.kt",
  offset: <approximate line from search below>
)
intellij_index_ide_search_text(
  query: "class Java.*Handler",
  file: "src/main/kotlin/.../handlers/java/JavaHandlers.kt"
)

# 4. Find the Python implementation
intellij_index_ide_search_text(
  query: "class Python.*Handler",
  file: "src/main/kotlin/.../handlers/python/PythonHandlers.kt"
)

# 5. Find the PHP implementation
intellij_index_ide_search_text(
  query: "class Php.*Handler",
  file: "src/main/kotlin/.../handlers/php/PhpHandlers.kt"
)

# 6. Search Ruby plugin.xml for relevant indexes
intellij_index_ide_search_text(
  query: "{search terms}",
  file: "ruby/ruby-plugin/plugin.xml"
)

# 7. Search Ruby JAR for matching classes (bash required — no MCP tool for JAR inspection)
# Ask permission first.
# jar tf ~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/ruby.jar | grep -iE "{search terms}"

# 8. Verify a candidate class exists
# Write a quick unit test using Class.forName() in the ruby/ test scope, or
# use intellij_index_ide_find_class to check if the IDE knows about it in the project.

# 9. Search extracted XMLs for PSI implementation details
intellij_index_ide_search_text(
  query: "{search terms}",
  filePattern: "*.xml",
  file: "ruby/ruby-plugin/modules/"
)
```

---

## 6. Cross-reference: architecture and patterns

For the full architecture understanding, read:
- `lessons_learned/02-architecture-patterns.md` — handler pattern, reflection architecture, registration flow
- `lessons_learned/03-ruby-psi-reference.md` — RClass/RModule/RMethod methods, resolution utilities
- `lessons_learned/04-handler-implementation.md` — step-by-step handler implementation with comparisons
- `lessons_learned/07-pr-checklist-and-gotchas.md` — CONTRIBUTING.md checklist, Ruby-specific gotchas
