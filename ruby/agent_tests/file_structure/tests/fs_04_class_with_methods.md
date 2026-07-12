# File Structure — Class with Methods

## 1. testClassWithMethods

**Purpose**: Verify that `ide_file_structure` returns all methods with their parameters and line numbers.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/class_with_methods.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/class_with_methods.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"class Calculator (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"method add (x, y) (line 2)"` |
| 5 | Inspect `response.structure` | Contains `"method compute () (line 6)"` |
| 6 | Inspect `response.structure` | `"add"` appears before `"compute"` (sorted by line) |
| 7 | Inspect `response.structure` | Does NOT contain `"class"` or `"module"` besides Calculator |

## 2. testMethodParameterDisplay

**Purpose**: Verify that method parameters are shown in parentheses.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with same parameters | Must succeed |
| 3 | Inspect `response.structure` | `"add (x, y)"` contains both parameters |
| 4 | Inspect `response.structure` | `"compute ()"` shows empty parens for no-param method |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `class_with_methods.rb` | `class Calculator` with `method add (x, y)`, `method compute ()` |