# Find Super Methods — Deep Chain

## 1. testDeepChain

**Purpose**: Verify that `ide_find_super_methods` on `Child#talk` returns a 2‑entry hierarchy: `Parent#talk` (depth=1) then `Grandparent#talk` (depth=2).

**Setup**: Fixture `ruby/agent_tests/find_super_methods/fixtures/deep_chain.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/deep_chain.rb"`, `line = 13`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"talk"` or `"Child#talk"`, `line` = 13 |
| 4 | Inspect `response.hierarchy` | Must be an array of length >= 2 |
| 5 | Inspect first entry in `hierarchy` | `depth` = 1, `name` contains `"Parent#talk"` or `"talk"`, `file` ends with `"deep_chain.rb"` |
| 6 | Inspect second entry in `hierarchy` | `depth` = 2, `name` contains `"Grandparent#talk"` or `"talk"`, `file` ends with `"deep_chain.rb"` |
| 7 | Inspect `response.totalCount` | Must be 2 |

## 2. testDeepChainSymbolLookup

**Purpose**: Verify symbol lookup for deep chain.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Child#talk"` | Must succeed |
| 3 | Inspect `response.hierarchy` | Length >= 2 |
| 4 | Inspect `response.totalCount` | Must be 2 |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `Child#talk` (line 13, col 7) | `Parent#talk` (depth 1), `Grandparent#talk` (depth 2) | 2 |