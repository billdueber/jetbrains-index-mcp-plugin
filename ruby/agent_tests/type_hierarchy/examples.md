# Ruby Type Hierarchy — Examples of Detected and Undetected Relationships

## Detected Relationships

### Single class inheritance (`extends`)
- `class Dog < Animal` → `Dog.supertypes = [Animal (CLASS)]`

### Module inclusion (`include`)
- `class User; include Authenticatable; end` → `User.supertypes` contains `Authenticatable (MODULE)`

### Module extension (`extend`)
- `class HasExtend; extend Publishable; end` → `HasExtend.supertypes` contains `Publishable (MODULE)`
- `module ExtendTarget; extend Publishable; end` → `ExtendTarget.supertypes` contains `Publishable (MODULE)`

### Module prepending (`prepend`)
- `class PrependTest; prepend Auditable; end` → `PrependTest.supertypes` contains `Auditable (MODULE)`

### Module including another module
- `module ParentModule; include HelperModule; end` → `ParentModule.supertypes` contains `HelperModule (MODULE)`

### Transitive (nested) supertypes
- `class Animal; end; class Dog < Animal; end; class Puppy < Dog; end`
  → `Puppy.supertypes[0] = { name: "Dog", supertypes: [{ name: "Animal" }] }`

### Transitive mixins through inheritance
- `class TP; include Publ; end; class TC < TP; end`
  → `TC.supertypes[0] = { name: "TP", supertypes: [{ name: "Publ" }] }`

### Multi-level transitive mixins
- `class DGP; include DM; end; class DP < DGP; end; class DC < DP; end`
  → `DC → DP → DGP → DM` (3 nested levels)

### Multiple included modules
- `class M; include A; include B; include C; end` → `M.supertypes` contains `A, B, C` (all MODULE)

### Namespaced class resolution
- `module Admin; class User; end; end` → element at `User` position resolves to `"Admin::User" (CLASS)`

### Namespaced include targets
- `class NC; include Namespace::Helpers; end` → `NC.supertypes` contains `Namespace::Helpers (MODULE)`

### Method-body to containing class
- `class Service; def perform; end; end` at cursor on `def perform` → resolves to `Service (CLASS)`

### Multiple classes in one file
- Two `class` declarations → each position resolves independently to its class

### Cycle detection
- `module A; include B; end; module B; include A; end`
  → `A.supertypes = [B]`, B's nested supertypes are empty/null (no infinite loop)

### Syntax error recovery
- Valid class before/after a parse error → both resolve; broken class is silently skipped

### Module as root element
- `module Auditable; end` → element = `Auditable (MODULE)`, no supertypes

### Class with no explicit superclass
- `class Foo; end` → element = `Foo (CLASS)`, supertypes = `[]`

### Stdlib superclass (with `scope = "project_and_libraries"`)
- `class MyString < String; end` → `MyString.supertypes` contains `String (CLASS)` (from stdlib JAR)

### Subtype discovery (max 100 entries)
- 101 classes extending `LotsOfSiblings` → `LotsOfSiblings.subtypes` = 100 entries max
- Module with 10 includers → each appears in subtypes

---

## Multiple Parents (Supertypes)

When multiple supertype relationships exist, they are all returned as a flat array at the first level of `response.supertypes`. Only class inheritance (`extends`) nests recursively — modules are flat.

```
class Article < Document
  include Publishable
  include Commentable
end
```

```json
{
  "element": { "name": "Article", "kind": "CLASS" },
  "supertypes": [
    { "name": "Document",    "kind": "CLASS",  "supertypes": null },
    { "name": "Publishable",  "kind": "MODULE", "supertypes": null },
    { "name": "Commentable",  "kind": "MODULE", "supertypes": null }
  ]
}
```

```
class FullCombo < Document
  include Publishable
  extend Commentable
end
```

```json
{
  "supertypes": [
    { "name": "Document",    "kind": "CLASS"  },
    { "name": "Publishable",  "kind": "MODULE" },
    { "name": "Commentable",  "kind": "MODULE" }
  ]
}
```

Classes and modules are interleaved in the array. **MRO ordering is not guaranteed** — the handler may return supertypes in any order (see Limitations below).

---

## Undetected Relationships

### Dynamic class/module creation
Not detected because the PSI tree shows an assignment, not a `class`/`module` keyword.

| Pattern | Example | Why |
|---------|---------|-----|
| `Struct.new` | `Person = Struct.new(:name, :age)` | Anonymous class creation |
| `Data.define` | `Point = Data.define(:x, :y)` | Anonymous class creation |
| `Class.new` | `MyClass = Class.new(Parent)` | Method call, not `class` keyword |
| `Module.new` | `MyMod = Module.new` | Method call, not `module` keyword |
| `const_set` | `const_set(:Foo, Class.new)` | Dynamic constant assignment |

### Singleton class / eigenclass (`class << self`)
Not tested / not handled. No test coverage for:
- Cursor inside a `class << self` block
- Whether `class << self` elements connect to their containing class/module
- Relationship between `extend` (works) and explicit `class << self` blocks

### MRO ordering
The handler returns supertypes in array order, but **that order is not verified to match Ruby's actual MRO**. Key untested order rules:
- `include A; include B` should produce A before B
- Included modules should appear after the superclass
- Prepended modules should appear before the class

### Stdlib resolution depends on SDK
`class MyString < String` with `scope = "project_and_libraries"` requires:
- Ruby SDK configured in the IntelliJ project
- Ruby plugin having indexed stdlib classes
If these are missing, the test silently returns empty supertypes — indistinguishable from a correct `project_files` scope result.