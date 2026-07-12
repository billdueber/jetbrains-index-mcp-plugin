# Type Hierarchy Tests — Position Resolution & Scope

Tests 7, 13, 15, 17, 28 from the original `type_hierarchy.md`. Covers
method-body-to-class resolution, multiple classes per file, stdlib scope
parameter, self-reference guard, and syntax error recovery.

---

### 7. testMethodInsideClassFindsContainingClass

**Purpose**: Verify that pointing `ide_type_hierarchy` at a method body inside
a class still returns the type hierarchy for the **containing class**.

**Setup**: File `service.rb` must exist with content:
```
class Service
  def perform
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/service.rb"`, `line = 2`, `column = 6` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Service"`, `kind` = `"CLASS"` |
| 4 | Note: The handler does NOT return method-level hierarchy. It walks up the PSI tree to find the containing RClass. The response describes the class, not the method. |

**Note**: Line 2, column 6 points at `def perform` (the `d` in `def`). The
position is inside the method body, but the handler resolves to `Service`.

---

### 13. testFileWithMultipleClasses

**Purpose**: Verify that two top-level classes in one file are each resolvable
independently by pointing at different line/column positions.

**Setup**: File `multi_class.rb` must exist with content:
```
# This file has two top-level classes
class FirstClass
end

class SecondClass
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/multi_class.rb"`, `line = 2`, `column = 7` | Must succeed. `response.element.name` = `"FirstClass"` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/multi_class.rb"`, `line = 5`, `column = 7` | Must succeed. `response.element.name` = `"SecondClass"` |

---

### 15. testInheritsFromStandardLibrary

**Purpose**: Verify that `class MyString < String` (stdlib superclass) reports
the supertype when using `scope = "project_and_libraries"`.

**Setup**: File `my_string.rb` with content `class MyString < String; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/my_string.rb"`, `line = 1`, `column = 7`, `scope = "project_and_libraries"` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"MyString"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"String"` and `kind` = `"CLASS"` |

**Note**: This test requires `scope = "project_and_libraries"` because `String`
is not in the project source — it's a Ruby stdlib class. If run with the default
`project_files` scope, `String` will not be found and supertypes will be empty.

---

### 17. testSelfReferenceGuard

**Purpose**: Verify that the handler does not infinite-loop on self-referencing
hierarchy. The maximum depth is 50. This test creates a chain of classes
that reference each other (simulated by a deep chain) and checks that the
handler terminates without error.

**Setup**: No special fixture needed. Use the existing `child.rb`, `parent.rb`,
and `grand_parent.rb` fixtures from the deep chain test.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/grand_parent.rb"`, `line = 1`, `column = 7` | Must succeed (no timeout, no crash). |
| 3 | Inspect `response.element.name` | `"GrandParent"` |
| 4 | Inspect `response.subtypes` | Must contain `"Parent"` |
| 5 | Inspect `response.supertypes` | Must be an empty array (GrandParent has no explicit superclass) |

**Note**: The guardrail is tested implicitly by the deep chain test showing
correct 3-level traversal. The 50-depth limit is an implementation detail
that is hard to trigger with hand-written fixtures.

---

### 28. testSyntaxErrorInFile

**Purpose**: Verify that a Ruby file with a syntax error does not prevent the
handler from producing a valid hierarchy for the classes/modules that ARE
defined parsably. The Ruby plugin's error recovery should still parse valid
class/module declarations.

**Behavior uncertain** — depends on the Ruby plugin's PSI error recovery.
If error recovery discards the entire file, the test will fail. The purpose
is to document what happens.

**Setup**: Create `syntax_error.rb` with content:
```
class ValidClass
end

class BrokenClass <
end

class AnotherValidClass
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the fixture file above | File created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/syntax_error.rb"`, `line = 1`, `column = 7` | Must succeed (no crash). The response describes `"ValidClass"` or some element |
| 4 | Inspect response — note which elements were found | Log the actual `element` and `supertypes`/`subtypes` values |
| 5 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/syntax_error.rb"`, `line = 7`, `column = 7` | May succeed or error — depends on whether the Ruby plugin parsed `AnotherValidClass` despite the syntax error above it |
| 6 | Note the behavior | This is an informational test, not a pass/fail |

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| Method → class resolution | `"Service"` | `"CLASS"` | — | — |
| Multiple classes in one file | `"FirstClass"` / `"SecondClass"` | `"CLASS"` | `[]` | `[]` |
| Stdlib inheritance (`MyString < String`) | `"MyString"` | `"CLASS"` | `"String"` (with scope=all) | `[]` |
| Self-reference guard | `"GrandParent"` | `"CLASS"` | `[]` | `"Parent"` |
| Syntax error in file | element or error | — | — | — |