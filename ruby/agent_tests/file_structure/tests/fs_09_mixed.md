# File Structure — Mixed Classes and Modules

## 1. testMixedClassAndModule

**Purpose**: Verify that `ide_file_structure` correctly handles a file with both a module and a class at the top level.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/mixed.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/mixed.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"module Services (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"class Application (line 4)"` |
| 5 | Inspect `response.structure` | `"Services"` appears before `"Application"` (file order) |
| 6 | Inspect `response.structure` | `"module"` keyword is used for Services, `"class"` for Application |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `mixed.rb` | `module Services (line 1)`, `class Application (line 4)` |