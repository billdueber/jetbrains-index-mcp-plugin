# Type Hierarchy Tests — Module Inclusion

Tests 2, 10, 16, 20, 25 from the original `type_hierarchy.md`. Covers
`include` on a class, module-including-module, multiple includes, `prepend`
(passing), and the cyclic include guard.

---

### 2. testIncludedModulesAppearAsSupertypes

**Purpose**: Verify that `class User; include Authenticatable` shows
`Authenticatable` as a supertype with kind `MODULE`.

**Setup**: Files `authenticatable.rb` and `user.rb` must exist.

- `authenticatable.rb`: `module Authenticatable; end`
- `user.rb`: (multi-line)
  ```
  class User
    include Authenticatable
  end
  ```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/user.rb"`, `line = 1`, `column = 7` | Must succeed (returns object, not error) |
| 3 | Inspect `response.element` | `name` = `"User"`, `kind` = `"CLASS"`, `language` = `"Ruby"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"Authenticatable"` and `kind` = `"MODULE"` |
| 5 | Inspect `response.subtypes` | Must be an empty array `[]` |

---

### 10. testModuleIncludingAnotherModule

**Purpose**: Verify that `module ParentModule; include HelperModule` shows
`HelperModule` as a supertype of `ParentModule`, with the element kind being
`MODULE`.

**Setup**: Files `helper_module.rb` and `parent_module.rb` must exist.

- `helper_module.rb`: `module HelperModule; end`
- `parent_module.rb`:
  ```
  module ParentModule
    include HelperModule
  end
  ```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/parent_module.rb"`, `line = 1`, `column = 8` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"ParentModule"`, `kind` = `"MODULE"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"HelperModule"` and `kind` = `"MODULE"` |

---

### 16. testMultipleIncludedModules

**Purpose**: Verify that a class including three modules shows all three in
supertypes.

**Setup**: Create a new fixture file for this test only.

Create `ruby/agent_tests/fixtures/multi_include.rb` with content:

```
class MultiInclude
  include A
  include B
  include C
end
```

Also create `ruby/agent_tests/fixtures/a.rb` with content `module A; end`,
`ruby/agent_tests/fixtures/b.rb` with content `module B; end`,
`ruby/agent_tests/fixtures/c.rb` with content `module C; end`.

If these files already exist, skip creation but still sync.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the four files listed above if they don't exist | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/multi_include.rb"`, `line = 1`, `column = 7` | Must succeed |
| 4 | Inspect `response.element` | `name` = `"MultiInclude"` |
| 5 | Inspect `response.supertypes` | Must be a non-empty array. Collect all supertype `name` values. `"A"`, `"B"`, and `"C"` must all appear. |

---

### 20. testPrependModule

**Purpose**: Verify that `class PrependTest; prepend Auditable` shows
`Auditable` as a supertype (prepend is semantically parallel to include).

**Expected to PASS** — `PREPEND_CALL` is now handled alongside `INCLUDE_CALL`
and `EXTEND_CALL` in the PSI collection path.

**Setup**: Create `prepend_test.rb` with content:
```
class PrependTest
  prepend Auditable
end
```
`auditable.rb` already exists with `module Auditable; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/prepend_test.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"PrependTest"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"Auditable"` and `kind` = `"MODULE"` |

---

### 25. testCyclicIncludeGuard

**Purpose**: Verify that two modules that `include` each other do not cause
infinite recursion. The handler's `visited` set should detect the cycle and
stop.

**Expected to PASS** — the visited set in `getSupertypes` tracks FQNs.
When a module is visited a second time, its supertype traversal returns empty.

**Setup**: Create `cycle_a.rb` with content:
```
module CycleA
  include CycleB
end
```
Create `cycle_b.rb` with content:
```
module CycleB
  include CycleA
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the two fixture files above | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/cycle_a.rb"`, `line = 1`, `column = 8` | Must succeed (no timeout, no crash) |
| 4 | Inspect `response.element` | `name` = `"CycleA"`, `kind` = `"MODULE"` |
| 5 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"CycleB"` |
| 6 | Inspect `response.supertypes[0]` | Its `supertypes` may be null or an empty list. It MUST NOT contain `"CycleA"` (which would indicate a cycle was not broken). |
| 7 | Repeat for `cycle_b.rb` at `line = 1`, `column = 8` | Same symmetry: element is `"CycleB"`, supertypes contain `"CycleA"`, which does NOT contain `"CycleB"`. |

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| Module include (`User < Authenticatable`) | `"User"` | `"CLASS"` | `"Authenticatable"` (MODULE) | `[]` |
| Module including module (`ParentModule`) | `"ParentModule"` | `"MODULE"` | `"HelperModule"` (MODULE) | `[]` |
| Multiple included modules | `"MultiInclude"` | `"CLASS"` | `"A"`, `"B"`, `"C"` | `[]` |
| `prepend` module (`PrependTest`) | `"PrependTest"` | `"CLASS"` | `"Auditable"` (MODULE) | `[]` |
| Cyclic include (`CycleA`↔`CycleB`) | `"CycleA"` / `"CycleB"` | `"MODULE"` | Each other (no transitive cycle) | `[]` |