# Ruby resolveSymbol — Agent Integration Tests

## Overview

These tests verify that the `resolveSymbol` MCP tool works correctly for Ruby symbol references. The handler resolves fully-qualified Ruby symbol references to PSI elements.

Supported symbol formats:
- `SomeClass` — bare class/module name (top-level)
- `Module::SomeClass` — namespaced class/module
- `Module::SomeClass#method_name` — instance method
- `Module::SomeClass.method_name` — class method (dot notation)
- `Module::SomeClass#method_name!` — bang method
- `Module::SomeClass#method_name?` — predicate method

## Pre-requisites

1. The jbimcp plugin is installed and running in an IntelliJ IDE (IDEA Ultimate or RubyMine) that has the **Ruby plugin** installed.
2. The IDE has indexed all project files. Call `ide_index_status` and confirm `isDumbMode: false` before running any tests.
3. The working project is `/Users/dueberb/devel/ai/jbimcp` (the jbimcp repo).
   All fixture paths are relative to this root.

> **Important**: After creating any fixture file, call `ide_sync_files` with no arguments BEFORE calling `resolveSymbol`. This ensures the IDE's PSI cache sees the new file.

## Response format for `resolveSymbol`

Returns a JSON object with:
- `element`: the resolved PSI element data (name, file, line, column, kind, language)
- `success`: boolean indicating whether resolution succeeded

The `element` object contains:
- `name` (String): Display name (e.g., `"User"` for class, `"admin?"` for method)
- `file` (String): Relative file path
- `line` (Int): 1-based line number
- `column` (Int): 1-based column number
- `kind` (String): `"CLASS"`, `"MODULE"`, or `"METHOD"`
- `language` (String): `"Ruby"`

---

# Tests

---

## Fixture 1: `simple_class.rb`

```ruby
class User
end
```

### T1.1 — Bare class name

**Purpose**: Verify that a bare class name resolves to the class element.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | Response has `syncedPaths` (array), `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"User"` |
| 4 | Inspect `element.kind` | Must equal `"CLASS"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/simple_class.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |
| 7 | Inspect `element.column` | Must be >= 1 |

---

### T1.2 — Namespaced class

**Purpose**: Verify that a namespaced class resolves correctly.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Admin::User"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"User"` |
| 4 | Inspect `element.kind` | Must equal `"CLASS"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/namespaced_class.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.3 — Deeply namespaced class

**Purpose**: Verify that deeply nested namespaces resolve correctly.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "A::B::C::User"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"User"` |
| 4 | Inspect `element.kind` | Must equal `"CLASS"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/deeply_namespaced.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.4 — Instance method

**Purpose**: Verify that an instance method reference resolves to the RMethod element.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User#admin?"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"admin?"` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/class_with_methods.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.5 — Class method

**Purpose**: Verify that a class method reference resolves to the RMethod element.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User.find_by_email"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"find_by_email"` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/class_with_class_methods.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.6 — Module only

**Purpose**: Verify that a module-only reference resolves to the RModule element.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Authenticatable"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"Authenticatable"` |
| 4 | Inspect `element.kind` | Must equal `"MODULE"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/module_only.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.7 — Underscored names

**Purpose**: Verify that gem-style namespaced classes resolve correctly.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "MyGem::MyClass"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"MyClass"` |
| 4 | Inspect `element.kind` | Must equal `"CLASS"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/underscored_names.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

### T1.8 — Setter method

**Purpose**: Verify that a setter method reference resolves to the RMethod element.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User#name="` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"name="` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/class_with_setter.rb"` |
| 6 | Inspect `element.line` | Must be >= 1 |

---

## Fixture 2: `non_existent.rb`

```ruby
# Empty file for non-existent tests
```

---

### T2.1 — Non-existent class

**Purpose**: Verify that a non-existent class returns failure.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "NonExistent"` | Response has `success: false` |
| 3 | Inspect `element` | Must be null or empty |

---

### T2.2 — Non-existent method

**Purpose**: Verify that a non-existent method returns failure.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User#nonexistent"` | Response has `success: false` |
| 3 | Inspect `element` | Must be null or empty |

---

### T2.3 — Method in namespaced class

**Purpose**: Verify that a method in a namespaced class resolves correctly.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Admin::User#admin?"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"admin?"` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/namespaced_class.rb"` |

---

### T2.4 — Bang method

**Purpose**: Verify that a bang method reference resolves correctly.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "User#save!"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"save!"` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/class_with_methods.rb"` |

---

### T2.5 — Symbol with leading whitespace

**Purpose**: Verify that leading whitespace is trimmed and the symbol is resolved.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `resolveSymbol` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "  User#find_by_email"` | Response has `success: true` |
| 3 | Inspect `element.name` | Must equal `"find_by_email"` |
| 4 | Inspect `element.kind` | Must equal `"METHOD"` |
| 5 | Inspect `element.file` | Must equal `"ruby/agent_tests/resolveSymbol/fixtures/class_with_methods.rb"` |

---

## Summary of expected test outcomes

| Test | Fixture | Symbol | Expected | Kind |
|------|---------|--------|----------|------|
| T1.1 | simple_class.rb | `"User"` | Success | CLASS |
| T1.2 | namespaced_class.rb | `"Admin::User"` | Success | CLASS |
| T1.3 | deeply_namespaced.rb | `"A::B::C::User"` | Success | CLASS |
| T1.4 | class_with_methods.rb | `"User#admin?""` | Success | METHOD |
| T1.5 | class_with_class_methods.rb | `"User.find_by_email"` | Success | METHOD |
| T1.6 | module_only.rb | `"Authenticatable"` | Success | MODULE |
| T1.7 | underscored_names.rb | `"MyGem::MyClass"` | Success | CLASS |
| T1.8 | class_with_setter.rb | `"User#name="` | Success | METHOD |
| T2.1 | non_existent.rb | `"NonExistent"` | Failure | — |
| T2.2 | non_existent.rb | `"User#nonexistent"` | Failure | — |
| T2.3 | namespaced_class.rb | `"Admin::User#admin?""` | Success | METHOD |
| T2.4 | class_with_methods.rb | `"User#save!""` | Success | METHOD |
| T2.5 | class_with_methods.rb | `"  User#find_by_email"` | Success | METHOD |

---

## Writing the report

After running tests, create a new file in `ruby/agent_tests/resolveSymbol/reports/` named `resolveSymbol-1.md`. If that file exists, increment the number.

Put the date at the top of the report and a summary of test results below, along with any useful error messages or warnings for failures.

## Finishing

Once the report is written, stop. Do not try to fix anything or edit any other files.