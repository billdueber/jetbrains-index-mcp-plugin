# IDE Index MCP Server - Add support for ruby

You are a direct, concise assistant. Do not add pleasantries, summaries,
or concluding remarks. Answer in 3 sentences or fewer unless asked for more
details. Do not use filler words.

The goal is to add functionality to this repo such that an intellij
IDE (Rubymine or IDEA with the ruby plugin) can use all the tools
exposed by this MCP server in ways that most closely adhere to
the conventions associated with ruby and are at least as good
as the native tools provided by the IDE.

## Basic rules

- relative paths are always relative to the root of this repo
- always report time/date on New York time, GMT -5
- output documents as gfm, with mermaid diagrams where necessary
- answers should be brief
- do as little to existing code as absolutely possible. Ideally, we touch
  only the new handler and tests and leave everything else alone
  except as needed to register/use the handler

## Reinstall the plugin 

The live tests require the plugin to be built and 
re-installed if code has changed at all. The script
`./build-and-install.sh` does this. 

## Using the intellij-index MCP

Prefer using the intellij-MCP for all code searching and editing.

Always pass the `project_path` argument when calling tools.

### Connect

 ```json
   {
     "action": "connect",
     "server": "intellij-index"
   }
 ```

### List tools

 ```json
   {
     "action": "list",
     "server": "intellij-index"
   }
 ```

### Describe a tool before calling it

 ```json
   {
     "action": "describe",
     "server": "intellij-index",
     "tool": "intellij_index_<tool name>"
   }
 ```

### Generic tool call

 ```json
   {
     "action": "tool",
     "server": "intellij-index",
     "tool": "intellij_index_<tool name>",
     "args": {
       "project_path": "/Users/dueberb/devel/ai/jbimcp",
       "<arg1>": "<value1>"
     }
   }
 ```


## Your Tools

- Whenever searching through the code base, use the `intellij-index`
  MCP server, making sure to always pass the project_path. MCP calls are
  prefixed by the configured name (e.g., "intellij_index_ide_find_symbol")
- context-mode is second choice. If using context-mode, it must be because
  you are chaining together
  multiple tools and/or writing code that must run in the context-mode
  sandbox.
- always use `sd` instead of `sed`
- always use `rg --json` instead of `grep`
- builtin commands and bash as a last resort. If using a built-in command
  or bash, you must first ask the user for permission and explain
  why there is no intellij-index MCP tool that will suffice.

In general:

- prefer using intellij-index MCP over anything else
- prefer using ide_find_symbol or ide_find_implementation to
  ide_search_text
- use ide_read_file only when you're not looking for anything in particular

## Development Guidelines

### Kotlin Standards

- Use Kotlin idioms (data classes, extension functions, coroutines where
  appropriate)
- Leverage null safety features
- Use `@RequiresBackgroundThread` / `@RequiresReadLock` annotations where
  needed

### IntelliJ Platform Best Practices

- Always check `DumbService.isDumb()` before accessing indexes
- Use `ReadAction` / `WriteAction` for PSI modifications
- Register extensions in `plugin.xml`, not programmatically
- Use `ApplicationManager.getApplication().invokeLater()` for UI updates
- Handle threading correctly (read actions on background threads, write
  actions on EDT)

### PSI-Document Synchronization

The IntelliJ Platform maintains separate Document (text) and PSI (parsed
structure) layers.
When files are modified externally (e.g., by AI coding tools), PSI may not
immediately reflect
the changes. This can cause search APIs to miss references in newly created
files.

**Solution**: `AbstractMcpTool` automatically refreshes the VFS and commits
documents
before executing any tool. This ensures PSI is synchronized with external
file changes.

**User Setting**: "Sync external file changes before operations" (
Settings → MCP Server)

- **Disabled** (default): Best performance, suitable for most use cases
- **Enabled**: **WARNING - SIGNIFICANT PERFORMANCE IMPACT.** Use only when
  rename/find-usages misses references in files just created externally.
  Each operation will take seconds instead of milliseconds on large repos.

**For tool developers**:

- Extend `AbstractMcpTool` and implement `doExecute()` (not `execute()`)
- PSI synchronization happens automatically before `doExecute()` is called
- To opt-out (for tools that don't use PSI), override:
  ```kotlin
  override val requiresPsiSync: Boolean = false
  ```

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable names
- Keep functions small and focused
- Extract reusable logic to utility classes

### Tool Schema Guidelines

All tool input schemas MUST use `SchemaBuilder` (in
`tools/schema/SchemaBuilder.kt`). This eliminates boilerplate and ensures
consistency:

```kotlin
// ✓ Use SchemaBuilder for all tool schemas
override val inputSchema = SchemaBuilder.tool()
    .projectPath()
    .file()
    .lineAndColumn()
    .intProperty(
        "maxResults",
        "Maximum results to return. Default: 100, max: 500."
    )
    .build()

// For enum parameters:
    .enumProperty(
        "matchMode",
        "How to match the query.",
        listOf("substring", "prefix", "exact")
    )

// For complex properties that don't fit the builder, use the escape hatch:
    .property("target_type", buildJsonObject { /* custom schema */ })
```

### Processor Pattern for Search Collections

Always use the `Processor` pattern for streaming search with early
termination — never `.findAll().take(N)`:

```kotlin
// ✗ Inefficient: loads all results into memory
val results = SomeSearch.search(element).findAll().take(100)

// ✓ Efficient: streams results with early termination
val results = mutableListOf<Result>()
SomeSearch.search(element).forEach(Processor { item ->
    results.add(convertToResult(item))
    results.size < 100  // Return false to stop iteration
})
```

### Test Architecture

Tests are split into two categories:

| Pattern        | Base Class                 | Speed       | Needs IDE?                              | Use For                                                          |
|----------------|----------------------------|-------------|-----------------------------------------|------------------------------------------------------------------|
| `*UnitTest.kt` | `junit.framework.TestCase` | Fast (<30s) | No                                      | Serialization, schemas, data classes, registries, pure logic     |
| `*Test.kt`     | `BasePlatformTestCase`     | Slow        | Yes (full IntelliJ Platform + indexing) | Tests needing `project`, PSI ops, tool execution, resource reads |

**Rules**:

- Run locally: `./gradlew test --tests "*UnitTest*"`
- **Never run platform tests locally** — they require full IDE init and
  hang on headless machines; let CI handle them.
- Place test fixtures in `src/test/testData/`.

# Agent Info — Ruby Plugin Integration

## Main source of information about the ruby plugin

`ruby/research/ruby-indexes-master-document.md` attempts to be 
the one-stop document for learning what we know about interacting with
the ruby plugin. Look at it first.


## Quick answers: where to look

| Question                            | Look here                                                                                                |
|-------------------------------------|----------------------------------------------------------------------------------------------------------|
| What Ruby PSI classes exist?        | `/ruby/ruby-plugin/lib/modules/` — extracted XMLs from plugin JARs; look for `psi`, `stub`, `index`      |
| What does the Ruby plugin expose?   | Summary in ruby/03-ruby-psi-reference.md. Plugin xml at `ruby/ruby-plugin/plugin.xml`                    |
| Where is the installed Ruby plugin? | `~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/` (JARs + .class/.rb files) |
| Where is handler registration?      | `src/main/kotlin/.../handlers/LanguageHandlerRegistry.kt` line 284                                       |
| Where is plugin detection?          | `src/main/kotlin/.../util/PluginDetectors.kt` lines 34-37                                                |

## Critical facts

- **Ruby plugin is closed-source** — no source browsing, only reflection +
  extracted XMLs
- **No compile-time dep** — all PSI access via `Class.forName()` +
  `.invoke()` wrapped in `runCatching`
- **Language ID is `"ruby"`** (lowercase) —
  `element.language.id.equals("ruby", true)`
- **Plugin ID**: `org.jetbrains.plugins.ruby`

### Handler Registration Flow

1. `LanguageHandlerRegistry.registerHandlers()` — registers handlers for
   available language plugins
2. `ToolRegistry.registerUniversalTools()` — registers tools available in
   all IDEs
3. `ToolRegistry.registerLanguageNavigationTools()` — registers tools if
   any language handlers are available
4. `ToolRegistry.registerJavaRefactoringTools()` — registers
   `ide_refactor_safe_delete` if Java plugin is available

New language handlers (e.g., Ruby) must follow this pipeline. Handlers for
closed-source plugins (Ruby, Python, Go, PHP, Rust, JS/TS) use reflection
via `Class.forName()` + `.invoke()` wrapped in `runCatching` — no
compile-time dependency.

## Ruby semantics relevant to handlers

| Concept                            | Mapped to                                                            |
|------------------------------------|----------------------------------------------------------------------|
| `class Child < Parent`             | Single superclass via `getSuperClass()`                              |
| `include Module` / `extend Module` | Mixins as "interfaces" via `getIncludedModules()`                    |
| `module Namespace`                 | Also a namespace container (shown as parent in structure)            |
| `super`                            | Override chain via `RubyOverrideImplementUtil` or manual parent walk |
| `User#admin?`, `User#save!`        | Valid method names (predicate/bang suffixes) in symbol patterns      |

## Useful IntelliJ Platform Classes

```kotlin
// PSI Navigation
PsiTreeUtil           // Tree traversal utilities
PsiUtilCore          // Core PSI utilities
ReferencesSearch     // Find references to element

// Refactoring
RefactoringFactory   // Create refactoring instances
RenameProcessor      // Rename refactoring
RefactoringBundle    // Refactoring messages

// Indexes
DumbService          // Check index status
FileBasedIndex       // Access file indexes
StubIndex            // Access stub indexes

// Project Structure
ProjectRootManager   // Project roots
ModuleManager        // Module access
VirtualFileManager   // Virtual file system
```

