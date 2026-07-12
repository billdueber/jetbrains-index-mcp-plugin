# Find Super Methods — Predicate and Bang Methods

## 1. testPredicateOverride

**Purpose**: Verify that `ide_find_super_methods` works for methods ending with `?` (predicate methods).

**Setup**: Fixture `ruby/agent_tests/find_super_methods/fixtures/predicate_and_bang.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/predicate_and_bang.rb"`, `line = 13`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"admin?"` or `"Sub#admin?"`, `line` = 13 |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `depth` = 1, `name` contains `"admin?"` or `"Base#admin?"`, `file` ends with `"predicate_and_bang.rb"` |

## 2. testBangOverride

**Purpose**: Verify that `ide_find_super_methods` works for methods ending with `!` (bang methods).

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/predicate_and_bang.rb"`, `line = 9`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"save!"` or `"Sub#save!"`, `line` = 9 |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array |
| 5 | Inspect the first entry in `hierarchy` | `depth` = 1, `name` contains `"save!"` or `"Base#save!"`, `file` ends with `"predicate_and_bang.rb"` |

## 3. testPredicateBangSymbol

**Purpose**: Verify symbol lookup for predicate and bang methods.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Sub#admin?"` | Must succeed, `hierarchy` non-empty |
| 3 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Sub#save!"` | Must succeed, `hierarchy` non-empty |

## Summary

| Target | Parents expected | totalCount |
|--------|-----------------|------------|
| `Sub#admin?` (line 13, col 7) | `Base#admin?` | 1 |
| `Sub#save!` (line 9, col 7) | `Base#save!` | 1 |