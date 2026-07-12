# File Structure — Simple Class

## 1. testSimpleClass

**Purpose**: Verify that `ide_file_structure` on a file with a single empty class returns the class with its line number.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/simple_class.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/simple_class.rb"` | Must succeed (returns object, not error) |
| 3 | Inspect `response.file` | Ends with `"simple_class.rb"` |
| 4 | Inspect `response.language` | `"ruby"` |
| 5 | Inspect `response.structure` | Contains `"class Simple (line 1)"` |
| 6 | Inspect `response.structure` | Does NOT contain `"method"` (no methods) |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `simple_class.rb` | `class Simple (line 1)` |