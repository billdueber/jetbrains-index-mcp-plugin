# File Structure — Class Methods

## 1. testClassWithClassMethods

**Purpose**: Verify that `ide_file_structure` correctly shows class methods (def self.method_name) alongside instance methods.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/class_methods.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/class_methods.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"class Calculator (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"method self square (x) (line 2)"` |
| 5 | Inspect `response.structure` | Contains `"method add (x, y) (line 6)"` |
| 6 | Inspect `response.structure` | `"self square"` appears before `"add"` (sorted by line) |

## 2. testClassMethodDistinction

**Purpose**: Verify that class methods are visually distinct from instance methods.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with same parameters | Must succeed |
| 3 | Inspect `response.structure` | `"self square"` includes the `self` modifier |
| 4 | Inspect `response.structure` | `"add (x, y)"` does NOT include `self` |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `class_methods.rb` | `class Calculator` with `method self square (x)`, `method add (x, y)` |