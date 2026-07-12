# Type Hierarchy Tests — Extend and Mixed Relationships

Tests 8, 9, 21, 22, 24 from the original `type_hierarchy.md`. Covers
`extend` on a class, superclass + includes combined, `extend` on a module,
all-three combo (superclass + include + extend), and transitive mixins
through a superclass.

---

### 8. testExtendModule

**Purpose**: Verify that `class HasExtend; extend Publishable` shows
`Publishable` as a supertype (class-level mixin via `extend`).

**Setup**: Files `publishable.rb` and `has_extend.rb` must exist.

- `publishable.rb`: `module Publishable; end`
- `has_extend.rb`:
  ```
  class HasExtend
    extend Publishable
  end
  ```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/has_extend.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"HasExtend"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"Publishable"` and `kind` = `"MODULE"` |

---

### 9. testClassWithBothSuperclassAndModules

**Purpose**: Verify that `class Article < Document; include Publishable; include
Commentable` shows both the superclass `Document` and modules `Publishable` and
`Commentable` in the supertypes array.

**Setup**: Files `document.rb`, `publishable.rb`, `commentable.rb`, and
`article.rb` must exist.

- `document.rb`: `class Document; end`
- `publishable.rb`: `module Publishable; end`
- `commentable.rb`: `module Commentable; end`
- `article.rb`:
  ```
  class Article < Document
    include Publishable
    include Commentable
  end
  ```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/article.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Article"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. Collect all supertype `name` values. `"Document"` must appear (the superclass). `"Publishable"` must appear (included module). `"Commentable"` must appear (included module). |
| 5 | Find the supertype entry with `name` = `"Document"` | Its `kind` must be `"CLASS"`. It may have nested `supertypes`. |

---

### 21. testModuleExtend

**Purpose**: Verify that `module ExtendTarget; extend Publishable` shows
`Publishable` as a supertype of `ExtendTarget`, with kind `MODULE`.

**Expected to PASS** — the `getModuleFQNsViaPsiCollection` fallback handles
`EXTEND_CALL` on RModule elements.

**Setup**: Create `extend_target.rb` with content:
```
module ExtendTarget
  extend Publishable
end
```
`publishable.rb` already exists with `module Publishable; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/extend_target.rb"`, `line = 1`, `column = 8` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"ExtendTarget"`, `kind` = `"MODULE"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. At least one entry has `name` = `"Publishable"` and `kind` = `"MODULE"` |

---

### 22. testSuperclassIncludeExtend

**Purpose**: Verify that a class with superclass + include + extend shows all
three in supertypes: the superclass (kind CLASS) and both modules (kind MODULE).

**Expected to PASS** — all three supertype resolution paths are independent
and all work.

**Setup**: Create `full_combo.rb` with content:
```
class FullCombo < Document
  include Publishable
  extend Commentable
end
```
`document.rb`, `publishable.rb`, and `commentable.rb` already exist.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/full_combo.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"FullCombo"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be a non-empty array. Collect all supertype `name` values. `"Document"` must appear (CLASS). `"Publishable"` must appear (MODULE). `"Commentable"` must appear (MODULE). |
| 5 | Find supertype `"Document"` | Its `kind` must be `"CLASS"` |
| 6 | Find supertype `"Publishable"` | Its `kind` must be `"MODULE"` |
| 7 | Find supertype `"Commentable"` | Its `kind` must be `"MODULE"` |

---

### 24. testTransitiveMixins

**Purpose**: Verify that a class whose superclass includes a module shows the
included module as a nested supertype of the superclass (transitive resolution).

**Expected to PASS** — the handler recurses into parent's supertypes via
`getSupertypes(superClass, ...)` and attaches them as nested `.supertypes`
on the parent entry.

**Setup**: Create `transitive_parent.rb` with content:
```
class TransitiveParent
  include Publishable
end
```
Create `transitive_child.rb` with content:
```
class TransitiveChild < TransitiveParent
end
```
`publishable.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the two fixture files above if missing | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/transitive_child.rb"`, `line = 1`, `column = 7` | Must succeed |
| 4 | Inspect `response.element` | `name` = `"TransitiveChild"` |
| 5 | Inspect `response.supertypes` | Must be a non-empty array. Find the entry where `name` = `"TransitiveParent"` |
| 6 | Inspect `response.supertypes[parent].supertypes` | Must be present (not null). At least one entry has `name` = `"Publishable"` and `kind` = `"MODULE"` |
| 7 | Also query `TransitiveParent` directly | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/transitive_parent.rb"`, `line = 1`, `column = 7`. Its `supertypes` must contain `"Publishable"`. |

### 29. testMultiLevelTransitiveMixins

**Purpose**: Verify transitive mixin resolution through 3 levels of class
inheritance: `DeepChild < DeepParent < DeepGrandParent` where
`DeepGrandParent` includes a module. The module should appear as a nested
supertypes entry at the grandparent level.

**Expected to PASS** — the handler recurses into each ancestor's supertypes
via `getSupertypes(superClass, ...)` and nests results at every level.

**Setup**: Create four fixture files.

`deep_module.rb`:
```
module DeepModule; end
```

`deep_grand_parent.rb`:
```
class DeepGrandParent
  include DeepModule
end
```

`deep_parent.rb`:
```
class DeepParent < DeepGrandParent
end
```

`deep_child.rb`:
```
class DeepChild < DeepParent
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the four fixture files above if missing | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/deep_child.rb"`, `line = 1`, `column = 7` | Must succeed |
| 4 | Inspect `response.element` | `name` = `"DeepChild"` |
| 5 | Inspect `response.supertypes` | Must be a non-empty array. Find the entry where `name` = `"DeepParent"` |
| 6 | Inspect `response.supertypes[parent].supertypes` | Must be present (not null). Find the entry where `name` = `"DeepGrandParent"` |
| 7 | Inspect `response.supertypes[parent].supertypes[grandparent].supertypes` | Must be present (not null). At least one entry has `name` = `"DeepModule"` and `kind` = `"MODULE"` |
| 8 | Also query `DeepGrandParent` directly | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/deep_grand_parent.rb"`, `line = 1`, `column = 7`. Its `supertypes` must contain `"DeepModule"`. |

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| `extend` module (`HasExtend`) | `"HasExtend"` | `"CLASS"` | `"Publishable"` (MODULE) | `[]` |
| Superclass + modules (`Article`) | `"Article"` | `"CLASS"` | `"Document"`, `"Publishable"`, `"Commentable"` | `[]` |
| Module `extend` (`ExtendTarget`) | `"ExtendTarget"` | `"MODULE"` | `"Publishable"` (MODULE) | `[]` |
| Superclass + include + extend (`FullCombo`) | `"FullCombo"` | `"CLASS"` | `"Document"` (CLASS), `"Publishable"`, `"Commentable"` (MODULE) | `[]` |
| Transitive mixins (`TransitiveChild < TransitiveParent`) | `"TransitiveChild"` | `"CLASS"` | `"TransitiveParent"` → `"Publishable"` | `[]` |
| Multi-level transitive (`DeepChild < DeepParent < DeepGrandParent`) | `"DeepChild"` | `"CLASS"` | `"DeepParent"` → `"DeepGrandParent"` → `"DeepModule"` | `[]` |