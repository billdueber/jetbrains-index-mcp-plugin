# File Structure — Module with Methods

## 1. testModuleWithMethods

**Purpose**: Verify that `ide_file_structure` correctly shows a module with its methods.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/module_with_methods.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/module_with_methods.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"module Greetable (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"method greet () (line 2)"` |
| 5 | Inspect `response.structure` | Contains `"method farewell (name) (line 6)"` |
| 6 | Inspect `response.structure` | `"greet"` appears before `"farewell"` (sorted by line) |

## 2. testModuleMethodWithParameters

**Purpose**: Verify that module methods with parameters show the parameter names.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with same parameters | Must succeed |
| 3 | Inspect `response.structure` | `"greet ()"` shows no parameters |
| 4 | Inspect `response.structure` | `"farewell (name)"` shows the `name` parameter |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `module_with_methods.rb` | `module Greetable` with `method greet ()`, `method farewell (name)` |