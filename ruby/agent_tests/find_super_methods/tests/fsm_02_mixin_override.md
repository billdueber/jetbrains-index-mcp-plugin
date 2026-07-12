# Find Super Methods — Mixin Override

## 1. testMixinOverride

**Purpose**: Verify that `ide_find_super_methods` on a method that overrides an `include`d module returns the module's method.

**Setup**: Fixture `ruby/agent_tests/find_super_methods/fixtures/mixin_override.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/mixin_override.rb"`, `line = 9`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"greet"`, `file` ends with `"mixin_override.rb"`, `line` = 9 (the `User#greet` def line) |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `name` contains `"Greetable#greet"` or `"greet"`, `depth` = 1 |

## 2. testMixinSymbolLookup

**Purpose**: Verify that `language + symbol` lookup resolves the mixin override.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User#greet"` | Must succeed |
| 3 | Inspect `response.hierarchy` | Non-empty array, first entry `depth` = 1 |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `User#greet` (line 9, col 7) | `Greetable#greet` | 1 |
| Symbol `User#greet` | `Greetable#greet` | >= 1 |