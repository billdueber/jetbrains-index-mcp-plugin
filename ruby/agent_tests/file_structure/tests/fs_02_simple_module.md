# File Structure — Simple Module

## 1. testSimpleModule

**Purpose**: Verify that `ide_file_structure` on a file with a single empty module returns the module with its line number.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/simple_module.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/simple_module.rb"` | Must succeed |
| 3 | Inspect `response.file` | Ends with `"simple_module.rb"` |
| 4 | Inspect `response.language` | `"ruby"` |
| 5 | Inspect `response.structure` | Contains `"module MyModule (line 1)"` |
| 6 | Inspect `response.structure` | Does NOT contain `"method"` |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `simple_module.rb` | `module MyModule (line 1)` |