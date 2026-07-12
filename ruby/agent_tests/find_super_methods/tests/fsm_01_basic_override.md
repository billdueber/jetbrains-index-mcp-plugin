# Find Super Methods — Basic Override

## 1. testBasicInheritance

**Purpose**: Verify that `ide_find_super_methods` on `Dog#speak` returns `Animal#speak` as its parent.

**Setup**: Fixture file `ruby/agent_tests/find_super_methods/fixtures/basic_inheritance.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/basic_inheritance.rb"`, `line = 7`, `column = 7` | Must succeed (returns object, not error) |
| 3 | Inspect `response.method` | `name` contains `"speak"` or `"Dog#speak"`, `file` ends with `"basic_inheritance.rb"`, `line` = 7 |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `name` contains `"speak"` or `"Animal#speak"`, `depth` = 1 |
| 6 | Inspect `response.totalCount` | Must be 1 (one parent method) |

## 2. testPositionInsideMethodBody

**Purpose**: Verify that the tool works when the position is on a line inside the method body, not just the `def` line.

**Setup**: Same fixture, `basic_inheritance.rb`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/basic_inheritance.rb"`, `line = 8`, `column = 5` | Must succeed; `method.line` = 7, `hierarchy` is non-empty |
| 3 | Inspect the first entry in `hierarchy` | `name` contains `"Animal#speak"` or parent method name, `depth` = 1 |

## 3. testLanguageSymbolLookup

**Purpose**: Verify that `language + symbol` lookup works for Ruby.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Dog#speak"` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"speak"` or `"Dog#speak"` |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array with at least one parent entry |
| 5 | Inspect `response.totalCount` | Must be >= 1 |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `Dog#speak` (line 7, col 7) | `Animal#speak` | 1 |
| Inside body (line 8, col 5) | `Animal#speak` | 1 |
| Symbol `Dog#speak` | `Animal#speak` | >= 1 |