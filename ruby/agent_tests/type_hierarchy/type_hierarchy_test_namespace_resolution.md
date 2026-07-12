# Type Hierarchy Tests — Namespace & Qualified Name Resolution

Tests 3, 4, 11, 23 from the original `type_hierarchy.md`. Covers standalone
module element kind, namespaced class FQN (`Admin::User`), nested modules
(`A::B`), and namespaced include targets.

---

### 3. testModuleOnly

**Purpose**: Verify that a standalone `module Auditable` produces a hierarchy
with kind `MODULE`.

**Setup**: File `auditable.rb` must exist with content `module Auditable; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/auditable.rb"`, `line = 1`, `column = 8` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Auditable"`, `kind` = `"MODULE"`, `language` = `"Ruby"` |

---

### 4. testNamespacedClass

**Purpose**: Verify that `module Admin; class User; end; end` correctly reports
the qualified name as `"Admin::User"`.

**Setup**: File `admin.rb` must exist with content:
```
module Admin
  class User
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/admin.rb"`, `line = 2`, `column = 9` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Admin::User"`, `kind` = `"CLASS"`, `language` = `"Ruby"` |

**Note**: The line=2 points to the `User` identifier inside `module Admin`.
The handler walks up the PSI tree and reconstructs the FQN.

---

### 11. testNestedModules

**Purpose**: Verify that a module inside another module
(`module A; module B; end; end`) correctly resolves the inner module `B`
with the qualified name `"A::B"`.

**Setup**: File `nested_modules.rb` must exist with content:
```
module A
  module B
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/nested_modules.rb"`, `line = 2`, `column = 10` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"A::B"`, `kind` = `"MODULE"`, `language` = `"Ruby"` |

---

### 23. testNamespacedIncludeTarget

**Purpose**: Verify that `include Namespace::Helpers` correctly resolves the
namespaced module and shows it as a supertype.

**Expected to PASS** — the PSI collection path calls `getCallData()` which
returns the resolved FQN, then `resolveByFQN` uses `RubyClassModuleNameIndex`
with short-name lookup + FQN filtering.

**Setup**: Create `namespaced_include.rb` with content:
```
module Namespace
  module Helpers
  end
end

class NamespacedInclude
  include Namespace::Helpers
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/namespaced_include.rb"`, `line = 6`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"NamespacedInclude"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"Namespace::Helpers"` and `kind` = `"MODULE"` |

**Note**: Line 6 points at the `NamespacedInclude` identifier (class is defined
at line 5, `NamespacedInclude` starts at column 7 on line 6).

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| Standalone module (`Auditable`) | `"Auditable"` | `"MODULE"` | `[]` | `[]` |
| Namespaced class (`Admin::User`) | `"Admin::User"` | `"CLASS"` | `[]` | `[]` |
| Nested modules (`A::B`) | `"A::B"` | `"MODULE"` | `[]` | `[]` |
| Namespaced include (`NamespacedInclude`) | `"NamespacedInclude"` | `"CLASS"` | `"Namespace::Helpers"` (MODULE) | `[]` |