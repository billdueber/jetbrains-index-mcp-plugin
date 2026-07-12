# Type Hierarchy Tests — Basic Class Inheritance

Tests 1, 5, 6, 12, 14 from the original `type_hierarchy.md`. Covers simple
`extends`, deep chains, subtype discovery, root object (no superclass), and
empty body class.

---

### 1. testSimpleInheritance

**Purpose**: Verify that `class Dog < Animal` reports `Animal` as a supertype
and that `Dog` has kind `CLASS` and language `Ruby`.

**Setup**: The fixtures `animal.rb` and `dog.rb` must exist in
`ruby/agent_tests/fixtures/`. Contents:

- `animal.rb`: `class Animal; end`
- `dog.rb`: `class Dog < Animal; end`

If missing, create them with `write` before proceeding.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | Response has a `syncedPaths` field (array) and `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/dog.rb"`, `line = 1`, `column = 7` | Response is a JSON object with three keys: `element`, `supertypes`, `subtypes` |
| 3 | Inspect `response.element` | Must be: `{"name": "Dog", "file": "ruby/agent_tests/fixtures/dog.rb", "kind": "CLASS", "language": "Ruby"}` |
| 4 | Inspect `response.supertypes` | Must be an array with exactly 1 entry. That entry's `name` must be `"Animal"`, `kind` must be `"CLASS"` |
| 5 | Inspect `response.subtypes` | Must be an empty array `[]` |

---

### 5. testDeepChain

**Purpose**: Verify recursive supertype traversal through three levels:
`Child < Parent < GrandParent`. The response's `supertypes` must have a
nested `supertypes` field on the immediate parent.

**Setup**: Files `grand_parent.rb`, `parent.rb`, and `child.rb` must exist.

- `grand_parent.rb`: `class GrandParent; end`
- `parent.rb`: `class Parent < GrandParent; end`
- `child.rb`: `class Child < Parent; end`

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/child.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Child"` |
| 4 | Inspect `response.supertypes` | Must be an array with exactly 1 entry. The entry's `name` = `"Parent"` |
| 5 | Inspect `response.supertypes[0].supertypes` | Must be present (not null). At least one entry with `name` = `"GrandParent"` |

---

### 6. testFindsSubtypes

**Purpose**: Verify that querying `Animal` returns `Dog` and `Cat` as subtypes.

**Setup**: Files `animal.rb`, `dog.rb`, and `cat.rb` must exist.

- `animal.rb`: `class Animal; end`
- `dog.rb`: `class Dog < Animal; end`
- `cat.rb`: `class Cat < Animal; end`

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/animal.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Animal"` |
| 4 | Inspect `response.subtypes` | Must be a non-empty array. Collect all `name` values from the array. Both `"Dog"` and `"Cat"` must appear in that list. |

---

### 12. testRootObjectSupertype

**Purpose**: Verify that a bare `class Foo; end` (no explicit superclass)
does not error. The handler should return a valid hierarchy with no
supertypes.

**Setup**: File `foo.rb` with content `class Foo; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/foo.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Foo"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be an empty array `[]` |

**Note**: Ruby's implicit superclass is `Object`, but the Ruby plugin returns
`null` for `getSuperClassFQN()` on bare classes. Our handler returns null
FQN as "no superclass" → empty supertypes. This is correct behavior for
the current implementation.

---

### 14. testClassWithNoBody

**Purpose**: Verify that `class EmptyClass; end` (empty body) produces a valid
type hierarchy without error.

**Setup**: File `empty_class.rb` with content `class EmptyClass; end`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/empty_class.rb"`, `line = 1`, `column = 7` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"EmptyClass"`, `kind` = `"CLASS"` |
| 4 | Inspect `response.supertypes` | Must be an empty array `[]` |
| 5 | Inspect `response.subtypes` | Must be an empty array `[]` |

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| Simple inheritance (`Dog < Animal`) | `"Dog"` | `"CLASS"` | `"Animal"` | `[]` |
| Deep chain (`Child < Parent < GrandParent`) | `"Child"` | `"CLASS"` | `"Parent"` → `"GrandParent"` | `[]` |
| Subtype discovery (`Animal`) | `"Animal"` | `"CLASS"` | `[]` | `"Dog"`, `"Cat"` |
| Empty class (`Foo`) | `"Foo"` | `"CLASS"` | `[]` | `[]` |
| Class with no body | `"EmptyClass"` | `"CLASS"` | `[]` | `[]` |
