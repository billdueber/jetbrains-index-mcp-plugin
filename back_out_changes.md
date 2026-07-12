# Restore the repo to a state without all the ruby bookkeeping

- git checkout CLAUDE.md
- git checkout .idea/gradle.xml
- roll back build.gradle.kts to main

# Before submitting a Ruby-related PR

## build.gradle.kts — local Ruby plugin support
- 
- **Drop it** — revert `build.gradle.kts` to the original form:


## Extra Ruby MCP Tools (research/debug tools)

Three temporary MCP tools that probe the Ruby plugin internals via
reflection and write results to the `ruby/` directory.

| Tool | Tool name | Source file |
|------|-----------|-------------|
| Index Explorer | `ide_ruby_index_explorer` | `RubyIndexExplorerTool.kt` |
| Call Hierarchy Debug | `ide_ruby_call_hierarchy_debug` | `RubyCallHierarchyDebugTool.kt` |
| Unknowns Probe | `ide_ruby_unknowns_probe` | `RubyUnknownsProbeTool.kt` |

### Files to delete

```bash
git rm \
  src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/ruby/RubyIndexExplorerTool.kt \
  src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/ruby/RubyCallHierarchyDebugTool.kt \
  src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/ruby/RubyUnknownsProbeTool.kt
```

### Files to revert (remove imports + register() lines + constants)

```bash
git checkout \
  src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/tools/ToolRegistry.kt \
  src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/constants/ToolNames.kt
```

**ToolRegistry.kt** reverts: 3 imports (`RubyCallHierarchyDebugTool`,
`RubyIndexExplorerTool`, `RubyUnknownsProbeTool`) and 3 `register()` calls
inside the `if (PluginDetectors.ruby.isAvailable)` block.

**ToolNames.kt** reverts: 3 constants (`RUBY_INDEX_EXPLORER`,
`RUBY_CALL_HIERARCHY_DEBUG`, `RUBY_UNKNOWNS_PROBE`) and 3 entries in
`ALL` list.

### Rebuild

```bash
./gradlew buildPlugin
```

---

## gradle.properties — platformPlugins

Confirm `platformPlugins =` is empty (no Ruby plugin download). This is the
correct state for CI and the default after our changes:

  ```bash
  grep platformPlugins gradle.properties
  # should print: platformPlugins =
  ```