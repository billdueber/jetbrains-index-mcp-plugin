# File Structure — Nested Modules

## 1. testNestedModules

**Purpose**: Verify that `ide_file_structure` correctly shows deeply nested modules and classes.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/nested_modules.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/nested_modules.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"module Outer (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"module Inner (line 2)"` |
| 5 | Inspect `response.structure` | Contains `"class Nested (line 3)"` |
| 6 | Inspect `response.structure` | Contains `"method inside () (line 4)"` |
| 7 | Inspect `response.structure` | Indentation shows nesting: `Inner` is indented under `Outer`, `Nested` under `Inner`, `inside` under `Nested` |

## 2. testNestingDepth

**Purpose**: Verify that the nesting depth is represented through indentation.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with same parameters | Must succeed |
| 3 | Inspect `response.structure` | `"module Inner"` is indented more than `"module Outer"` |
| 4 | Inspect `response.structure` | `"class Nested"` is indented more than `"module Inner"` |
| 5 | Inspect `response.structure` | `"method inside"` is indented more than `"class Nested"` |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `nested_modules.rb` | `module Outer` > `module Inner` > `class Nested` > `method inside ()` |