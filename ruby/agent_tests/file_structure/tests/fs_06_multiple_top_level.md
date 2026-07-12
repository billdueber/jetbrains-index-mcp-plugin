# File Structure — Multiple Top-Level Classes

## 1. testMultipleTopLevelClasses

**Purpose**: Verify that `ide_file_structure` correctly handles multiple top-level classes in one file.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/multiple_top_level.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/multiple_top_level.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"class First (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"class Second (line 5)"` |
| 5 | Inspect `response.structure` | Contains `"method one (line 2)"` |
| 6 | Inspect `response.structure` | Contains `"method two (line 6)"` |
| 7 | Inspect `response.structure` | `"First"` appears before `"Second"` (file order) |
| 8 | Inspect `response.structure` | `"one"` is under `"First"`, `"two"` is under `"Second"` |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `multiple_top_level.rb` | `class First` with `method one`, `class Second` with `method two` |