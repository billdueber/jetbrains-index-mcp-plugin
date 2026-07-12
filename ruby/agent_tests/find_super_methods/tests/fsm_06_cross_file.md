# Find Super Methods — Cross-File Resolution

## 1. testCrossFileOverride

**Purpose**: Verify that `ide_find_super_methods` resolves super methods defined in a different file (class inheritance across files).

**Setup**: Fixtures `ruby/agent_tests/find_super_methods/fixtures/cross_file/base.rb` and `sub.rb` already exist.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/cross_file/sub.rb"`, `line = 7`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"process"`, `file` ends with `"sub.rb"` |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `depth` = 1. The `file` may point to either `base.rb` (class inheritance) or the same file (mixin). At least one parent method must be found. |
| 6 | Inspect `response.totalCount` | Must be >= 1 |

## 2. testCrossFileSymbolLookup

**Purpose**: Verify symbol lookup across files.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "SubService#process"` | Must succeed |
| 3 | Inspect `response.hierarchy` | Non-empty array |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `SubService#process` (line 7, col 7, sub.rb) | `BaseService#process` (base.rb) and/or `M#process` (base.rb) | >= 1 |