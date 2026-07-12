# Find Super Methods — Edge Cases and Errors

## 1. testEmptyMethodBody

**Purpose**: Verify that `ide_find_super_methods` works on methods with empty bodies.

**Setup**: Fixture `ruby/agent_tests/find_super_methods/fixtures/edge_cases.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/edge_cases.rb"`, `line = 6`, `column = 7` | Must succeed |
| 3 | Inspect `response.method` | `name` contains `"no_op"`, `file` ends with `"edge_cases.rb"`, `line` = 6 |
| 4 | Inspect `response.hierarchy` | Must be a non-empty array (parent `EmptyBody#no_op` at line 2) |
| 5 | Inspect the first entry in `hierarchy` | `depth` = 1, `line` = 2 |

## 2. testSingletonClassMethod

**Purpose**: Verify behavior when targeting a method defined in a `class << self` block.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/edge_cases.rb"`, `line = 11`, `column = 11` | May succeed with empty hierarchy OR return an error. Either is acceptable. |

## 3. testExtendOverride

**Purpose**: Verify behavior when a class method overrides a method from an `extend`ed module.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/edge_cases.rb"`, `line = 22`, `column = 13` | Must succeed |
| 3 | Inspect `response.hierarchy` | May be empty or non-empty (depends on `extend` support) |

## 4. testDefineMethodOverride

**Purpose**: Verify that a method defined via `define_method` in a parent class is found as a super method.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/edge_cases.rb"`, `line = 30`, `column = 7` | Must succeed |
| 3 | Inspect `response.hierarchy` | If `DynamicBase#alpha` (via `define_method` on line 26) is found, hierarchy is non-empty with `depth` = 1. Otherwise, it may be empty. |

## 5. testEmptyFile

**Purpose**: Verify that `ide_find_super_methods` returns a clear error when the file is empty.

**Setup**: Create `ruby/agent_tests/find_super_methods/fixtures/empty.rb` with the following content:

```ruby

```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Write `empty.rb` (blank file) | File created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/empty.rb"`, `line = 1`, `column = 1` | Must return an error message indicating no element was found at the position |

## 6. testNonExistentFile

**Purpose**: Verify that `ide_find_super_methods` returns a clear error for a nonexistent file.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/nonexistent.rb"`, `line = 1`, `column = 1` | Must return an error message indicating the file was not found |

## 7. testInvalidPosition

**Purpose**: Verify that `ide_find_super_methods` returns a clear error when the position is in whitespace/comment, not on a method.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_find_super_methods` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/find_super_methods/fixtures/basic_inheritance.rb"`, `line = 1`, `column = 1` | Must return an error: "No method/function found at position" or similar |

## Summary

| Test | Scenario | Expected |
|------|----------|----------|
| 1 | Empty body override | hierarchy non-empty (parent `EmptyBody#no_op`) |
| 2 | Singleton class method | May succeed or error |
| 3 | `extend` override | May be empty or non-empty |
| 4 | `define_method` parent | May be empty or non-empty |
| 5 | Empty file | Error message |
| 6 | Non-existent file | Error message |
| 7 | Whitespace/comment position | Error message |