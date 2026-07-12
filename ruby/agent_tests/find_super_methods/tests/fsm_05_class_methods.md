# Find Super Methods — Class Methods

## 1. testClassMethodOverride

**Purpose**: Verify that `ide_find_super_methods` works for class-level (`self.`) method overrides.

**Setup**: Fixture `ruby/agent_tests/find_super_methods/fixtures/class_methods.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/class_methods.rb"`, `line = 7`, `column = 13` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"find"`, `line` = 7 |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `depth` = 1, `name` contains `"BaseClass.find"` or `"find"` |
| 6 | Inspect `response.totalCount` | Must be >= 1 |

## 2. testClassMethodSymbol

**Purpose**: Verify symbol lookup for class methods.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "SubClass.find"` | Must succeed |
| 3 | Inspect `response.hierarchy` | Non-empty array |
| 4 | Inspect `response.totalCount` | >= 1 |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `SubClass.find` (line 7, col 13) | `BaseClass.find` | >= 1 |
| Symbol `SubClass.find` | `BaseClass.find` | >= 1 |