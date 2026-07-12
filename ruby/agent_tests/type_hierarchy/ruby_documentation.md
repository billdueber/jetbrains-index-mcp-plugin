# Ruby Type Hierarchy Documentation

**Date**: 2026-07-12
**Plugin**: jbimcp — Ruby Type Hierarchy Handler
**IDE**: IntelliJ IDEA Ultimate / RubyMine with Ruby plugin

---

## 1. Introduction

The Ruby type hierarchy handler (implemented in `RubyHandlers.kt` as `RubyTypeHierarchyHandler`) provides an `ide_type_hierarchy` MCP tool that returns the type hierarchy of a Ruby class or module. The handler uses reflection to access the Ruby plugin's PSI (Program Structure Interface) and stub indexes — there is no compile-time dependency on the Ruby plugin.

The response shape is a `TypeHierarchyData` object with three top-level keys:

```json
{
  "element": { "name": "...", "kind": "CLASS|MODULE", "language": "Ruby", ... },
  "supertypes": [ /* array of TypeElementData, recursively nested */ ],
  "subtypes":   [ /* flat array of TypeElementData */ ]
}
```

---

## 2. How the Type Hierarchy is Constructed

### 2.1 Element Resolution

When `ide_type_hierarchy` receives a file path + line + column position, the handler:

1. Locates the PSI element at the given position in the Ruby file.
2. Walks up the PSI tree via `PsiTreeUtil.getParentOfType()` to find the **containing `RClass` or `RModule`** element. This means even if the cursor is inside a method body (`def some_method`), the handler returns the hierarchy for the enclosing class/module.
3. Determines the **kind** (`CLASS` for `RClass`, `MODULE` for `RModule`) using reflection-based `instanceof` checks.
4. Computes the **qualified name** using a multi-strategy approach (see section 2.5).

### 2.2 Supertype Traversal — Core Algorithm

The handler builds the supertype chain recursively:

```
getSupertypes(element, visited_set, depth):
    if depth > 50: return empty
    if element already visited: return empty  (cycle detection)
    mark element as visited

    supertypes = []

    // 1. Superclass (RClass only)
    if element is an RClass:
        fqn = getSuperClassFQN(element)          via reflection
        if fqn exists:
            superclass = resolveByFQN(fqn)        via indexes
            if found:
                nested = getSupertypes(superclass, depth+1)
                supertypes.add(superclass with nested supertypes)

    // 2. Included modules (include)
    for each included_fqn in getIncludedModuleFQNs(element):
        module = resolveByFQN(included_fqn)
        supertypes.add(module with kind=MODULE)

    // 3. Extended modules (extend)
    for each extended_fqn in getExtendedModuleFQNs(element):
        module = resolveByFQN(extended_fqn)
        supertypes.add(module with kind=MODULE)

    // 4. Prepended modules (prepend)
    for each prepended_fqn in getPrependedModuleFQNs(element):
        module = resolveByFQN(prepended_fqn)
        supertypes.add(module with kind=MODULE)

    return supertypes
```

Key design decisions:

- **Supertype recursion only follows `extends` (inheritance)**, not `include`/`extend`/`prepend`. Included modules appear as flat entries in the supertypes array without nested supertype traversal. This prevents infinite recursion on cyclic includes (cycle detection via the visited set handles the `extends` recursion).
- **Each supertype relationship type is resolved independently.** They do not interfere with each other — a failure in one path does not affect the others.
- **Resolution uses multiple fallback strategies**, attempting the most precise index first and falling back to broader indexes if the element is not found.

### 2.3 Subtype Discovery

Subtypes are discovered via two strategies:

1. **Class inheritance** (`findSubtypesForFQN`): Uses `RubyInheritanceResolutionIndex$ForSuperClasses` (fast path) or `RubyInheritanceIndex` (fallback) to find classes whose superclass FQN matches the target element's FQN. Post-filters by exact superclass FQN match when using the short-name-based index.

2. **Module mixins** (`findIncludingClasses` / `findExtendingClasses`): Uses `RubyIncludedExtendedFQNIndex` to find classes/modules that include or extend the target module.

Both strategies are capped at `MAX_SUBTYPES = 100` entries.

### 2.4 Resolution of Include/Extend/Prepend Calls

Each mixin type uses a priority-ordered resolution pipeline:

| Priority | Strategy | Works on | Mechanism |
|----------|----------|----------|-----------|
| 1 (Primary) | `RubyClassResolveUtil.findCall()` | RClass only | Purpose-built internal API, checks predicates on call type |
| 2 (Secondary) | `processCallsOfType()` | RClass only | Uses reflection to invoke Ruby's internal call-type consumer |
| 3 (Tertiary) | `PsiTreeUtil.collectElementsOfType()` with `RCall` | RClass + RModule | Walks PSI tree for all matching call expressions |
| 4 (Fallback) | PSI text walk with regex | RClass + RModule | Scans source text for `include ModuleName` patterns |

Priority 1 (`findCall`) is tried first because it is the most precise — it uses the Ruby plugin's own resolution mechanism. Priorities 3 and 4 exist because `processCallsOfType` is only available on `RClass`, not `RModule`.

### 2.5 Qualified Name Resolution

The handler's `getRubyQualifiedName()` uses a multi-strategy fallback chain to compute the fully-qualified name for any Ruby class or module:

| Strategy | Method | Example Result | Note |
|----------|--------|----------------|------|
| 1 | `getFQN().getFullPath()` (RClass) | `"Admin::User"` | Only returned if FQN contains `::` |
| 2 | `getQualifiedName()` (RModule) | `"A::B"` | Only returned if qualified |
| 3 | `getFullyQualifiedName()` (generic) | `"Admin::User"` | Fallback for RModule |
| 4 | PSI parent walk | `"Admin::User"` | Reconstructs from containing module hierarchy |
| 5 | `getName()` | `"User"` | Last resort, short name only |

Strategy 4 (PSI parent walk) is important for namespaced classes where the Ruby plugin returns only the short name for inner classes. The handler walks up the PSI tree collecting containing module names, then reconstructs the full path.

### 2.6 Search Scopes

The handler supports all `BuiltInSearchScope` values:

| Scope | Behavior |
|-------|----------|
| `project_files` | (Default) Only resolves types defined in project source files |
| `project_and_libraries` | Includes Ruby stdlib and gem dependencies (requires Ruby SDK) |
| `project_production_files` | Source files only, no test files |
| `project_test_files` | Test files only |

The scope parameter affects both supertype resolution (finding the parent class) and subtype discovery (listing children). Standard library superclasses (e.g., `class MyString < String`) only resolve when using `project_and_libraries`.

---

## 3. Relationship Types Supported

The handler accurately reports the following Ruby type relationships:

### 3.1 Single Class Inheritance (`extends`)

```
class Animal; end
class Dog < Animal; end
```

- **Dog → supertypes**: `[Animal (CLASS)]` (Animal has no nested supertypes)
- **Animal → subtypes**: `[Dog (CLASS)]`

The superclass FQN is obtained via `RClass.getSuperClassFQN()`, which returns an `FQN` object. The handler checks for an `INVALID` sentinel value (empty FQN) to distinguish "no explicit superclass" from "resolution error."

### 3.2 Module Inclusion (`include`)

```
module Authenticatable; end
class User
  include Authenticatable
end
```

- **User → supertypes**: `[Authenticatable (MODULE)]`

Included modules appear as supertype entries with `kind: "MODULE"`. Modules including other modules also work:

```
module HelperModule; end
module ParentModule
  include HelperModule
end
```

### 3.3 Module Extension (`extend`)

```
module Publishable; end
class HasExtend
  extend Publishable
end
```

- **HasExtend → supertypes**: `[Publishable (MODULE)]`

`extend` on a module (class-level mixin on a module) also works:

```
module ExtendTarget
  extend Publishable
end
```

### 3.4 Module Prepending (`prepend`)

```
module Auditable; end
class PrependTest
  prepend Auditable
end
```

- **PrependTest → supertypes**: `[Auditable (MODULE)]`

`prepend` support was added alongside `INCLUDE_CALL` and `EXTEND_CALL` in all three resolution strategies (`findCall`, `processCallsOfType`, PSI collection, and text walk).

### 3.5 Combined Relationships

Multiple relationship types can coexist:

```
class FullCombo < Document          # superclass
  include Publishable                # included module
  extend Commentable                 # extended module
end
```

- **FullCombo → supertypes**: `[Document (CLASS), Publishable (MODULE), Commentable (MODULE)]`

Each path is resolved independently and all supertypes are merged into a single flat array at the first level.

### 3.6 Transitive (Nested) Supertype Chain

```
class GrandParent; end
class Parent < GrandParent; end
class Child < Parent; end
```

- **Child → supertypes[0]**: `{ name: "Parent", supertypes: [{ name: "GrandParent" }] }`

The handler recurses into each ancestor's supertypes, producing a nested structure:

```
Child
  └── Parent (CLASS)
        └── GrandParent (CLASS)
  └── [any included/extended/prepended modules]
```

Only class inheritance (`extends`) is traversed recursively; modules are flat entries at the class level where they are declared.

### 3.7 Transitive Mixins Through Inheritance

```
module Publishable; end
class TransitiveParent
  include Publishable
end
class TransitiveChild < TransitiveParent
end
```

- **TransitiveChild → supertypes[0]** (TransitiveParent):
  - **supertypes → [Publishable (MODULE)]**

Mixins are inherited through the class hierarchy — `TransitiveChild` shows `TransitiveParent` containing `Publishable` in its nested supertypes, even though `TransitiveChild` itself does not directly `include Publishable`.

### 3.8 Multi-Level Transitive Mixins

Three levels of class inheritance with a module included at the top:

```
module DeepModule; end
class DeepGrandParent; include DeepModule; end
class DeepParent < DeepGrandParent; end
class DeepChild < DeepParent; end
```

- **DeepChild → DeepParent (CLASS)** → **DeepGrandParent (CLASS)** → **DeepModule (MODULE)**

### 3.9 Multiple Included Modules

```
class MultiInclude
  include A
  include B
  include C
end
```

- **MultiInclude → supertypes**: `[A (MODULE), B (MODULE), C (MODULE)]`

All three modules appear in the supertypes array.

### 3.10 Namespaced Classes/Modules

```
module Admin
  class User; end
end
```

- **Admin::User** → element with `name: "Admin::User"`, `kind: "CLASS"`

The handler reconstructs the qualified name from the PSI parent chain. Pointing at position inside `class User` (inside `module Admin`) resolves to `Admin::User`, not `User`.

### 3.11 Namespaced Include Targets

```
module Namespace
  module Helpers; end
end

class NamespacedInclude
  include Namespace::Helpers
end
```

- **NamespacedInclude → supertypes**: `[Namespace::Helpers (MODULE)]`

The `getCallData` API returns the resolved FQN for namespaced include targets, and `resolveByFQN` finds the element via `RubyClassModuleNameIndex`.

### 3.12 Module as Root Element

```
module Auditable; end
```

- **Auditable** → element with `name: "Auditable"`, `kind: "MODULE"`, no supertypes

Standalone modules produce valid hierarchies like classes.

### 3.13 Root Object (No Explicit Superclass)

```
class Foo; end
```

- **Foo** → element with `name: "Foo"`, `kind: "CLASS"`, empty supertypes

Ruby's implicit superclass is `Object`, but the Ruby plugin returns `null` for `getSuperClassFQN()` on bare classes. The handler treats a null FQN as "no superclass" and returns an empty supertypes array. This is correct for the current implementation.

### 3.14 Cycle Detection

When two modules include each other:

```
module CycleA; include CycleB; end
module CycleB; include CycleA; end
```

The handler's visited set prevents re-visiting. `CycleA` shows `CycleB` as a supertype, but `CycleB` does not show `CycleA` in its nested supertypes (the cycle is broken). The handler terminates without infinite recursion or stack overflow.

### 3.15 Method-to-Class Resolution

Pointing `ide_type_hierarchy` at a position inside a method body resolves to the containing class/module, not the method:

```
class Service
  def perform
    # cursor at line 2, column 6
  end
end
```

- **Result**: element = `Service` (CLASS), not `perform`

### 3.16 Multiple Classes in One File

```
class FirstClass; end   # line 2
class SecondClass; end  # line 5
```

Each position resolves to its respective class independently:
- Line 2, column 7 → `FirstClass`
- Line 5, column 7 → `SecondClass`

### 3.17 Syntax Error Recovery

```
class ValidClass; end       # line 1 — resolves correctly
class BrokenClass <         # line 3 — parsed error
end
class AnotherValidClass; end # line 7 — resolves correctly
```

The Ruby plugin's PSI error recovery typically parses valid class/module declarations even when other parts of the file contain syntax errors. Both `ValidClass` and `AnotherValidClass` resolve successfully.

---

## 4. Guardrails and Limits

| Guardrail | Value | Mechanism |
|-----------|-------|-----------|
| `MAX_HIERARCHY_DEPTH` | 50 | Recursion depth in `getSupertypes()` |
| `MAX_SUBTYPES` | 100 | `.take(100)` on each subtype discovery strategy |
| Cycle detection | Visited set (FQN-based) | `MutableSet<String>` passed through recursion |

### 4.1 Depth Limit

If a class hierarchy exceeds 50 levels of nesting, supertype traversal stops. This is primarily a safety net against infinite recursion; practical Ruby hierarchies rarely exceed 10 levels.

### 4.2 Subtype Limit

Subtype discovery is capped at 100 entries across all strategies. If more than 100 classes inherit from the same parent, only 100 are returned. The missing entries are dropped — there is no pagination.

### 4.3 Cycle Detection

The visited set tracks FQNs encountered during supertype traversal. When a cycle is detected (element already visited), `getSupertypes` returns `emptyList()` for that branch. This prevents infinite recursion from:
- `A extends B; B extends A` (impossible in Ruby, but the guard exists)
- `A includes B; B includes A` (possible in Ruby modules)
- Self-referential hierarchies

---

## 5. Index Utilization

The handler uses several Ruby plugin indexes for resolution:

| Index | Class | Purpose |
|-------|-------|---------|
| `RubyClassModuleNameIndex` | Short-name → elements | Primary element resolution by name + FQN filtering |
| `RubyInheritanceResolutionIndex$ForSuperClasses` | FQN → subclass elements | Fast subtype lookup, no post-filter needed |
| `RubyInheritanceIndex` | Short-name → subclass elements | Fallback subtype lookup, requires FQN post-filter |
| `RubyIncludedExtendedFQNIndex` | Module FQN → includers/extenders | Reverse mixin lookup for subtype discovery |
| `RubyInheritanceResolutionIndex` | FQN → element | Element resolution by own FQN |

### 5.1 Index Interaction

```
    Query by file+line+col
            │
            ▼
    Find PSI element at position
            │
            ▼
    Walk up to containing RClass/RModule
            │
            ├──► getRubyQualifiedName(element) — FQN
            │
            ├──► getSupertypes
            │       │
            │       ├──► rClassGetSuperClassFQN → resolveByFQN
            │       ├──► getIncludedModuleFQNs → findCall → processCalls → collect → text
            │       ├──► getExtendedModuleFQNs → findCall → processCalls → collect → text
            │       └──► getPrependedModuleFQNs → findCall → processCalls → collect → text
            │
            └──► getSubtypes
                    │
                    ├──► findSubtypesForFQN (ForSuperClasses / InheritanceIndex)
                    ├──► findIncludingClasses (IncludedExtendedFQNIndex)
                    └──► findExtendingClasses (IncludedExtendedFQNIndex)
```

---

## Appendix: Known Limitations

This appendix is reproduced from `ruby/agent_tests/type_hierarchy/limitations.md`.

### A.1 MRO Ordering Not Verified

Ruby's method resolution order (MRO) is strictly ordered. For
`class C < S; include A; include B; end`, the ancestor chain is
`[C, A, B, S, Object, Kernel, BasicObject]`. Every test in the suite
checks only **presence** of supertypes in the array (e.g., `"A" must
appear`), never **position**.

**Impact**: The handler could return supertypes in any order and the tests
would pass. Key relations that are not verified:

- `include A; include B` produces `[A, B]` in that order
- `include`d modules appear **after** the superclass in the array
- `prepend`ed modules appear **before** the class
- The relative position of CLASS vs MODULE entries

**Fix**: Add position-aware assertions to existing tests (e.g., check
`supertypes[0].name == "Superclass"`, `supertypes[1].name == "A"`).

### A.2 Dynamic Class/Module Creation Not Resolvable

Ruby allows class and module creation through method calls. The PSI tree
shows an assignment, not a `class`/`module` keyword, so the handler cannot
resolve the supertype.

**Patterns not supported**:

| Pattern | Example | Why it fails |
|---------|---------|--------------|
| `Struct.new` | `Person = Struct.new(:name, :age)` | Creates `Person < Struct` anonymously |
| `Data.define` | `Point = Data.define(:x, :y)` | Creates `Point < Data` anonymously |
| `Class.new` | `MyClass = Class.new(Parent)` | Creates `MyClass < Parent` via method call |
| `Module.new` | `MyMod = Module.new` | Creates module via method call |
| `const_set` | `const_set(:Foo, Class.new)` | Dynamic constant assignment |

These are very common in Ruby (Rails models use `Struct.new`, test factories
use `Class.new`). The type hierarchy handler will not show any supertypes
for elements defined this way.

**Workaround**: None purely via PSI. Requires full constant-tracking and
data-flow analysis beyond what a stub-based index provides.

### A.3 Singleton Class / Eigenclass Pattern Not Handled

Ruby's `class << self` creates a singleton class (eigenclass) that sits in
the ancestor chain. This is pervasive in Ruby for defining class-level
methods via `class << self; ...; end` blocks.

**Impact**: The handler has no test coverage for:

- Resolving the containing class when the cursor is inside a
  `class << self` block
- Whether the Ruby plugin exposes singleton classes as PSI elements
- Whether `extend` (which works via singleton class inclusion) has any
  interaction with explicit `class << self` definitions

**Note**: `extend` on a class *does* work because the Ruby plugin's
stub index resolves it. But `class << self` blocks inside class bodies
are a separate PSI construct and may not resolve to the same element.

### A.4 Stdlib Scope Test Has Environment Preconditions

The test verifying stdlib supertype resolution (`class MyString < String`)
depends on:

- A Ruby SDK being configured in the IntelliJ project
- The Ruby plugin having indexed stdlib classes
- The Ruby SDK not being broken or incomplete
- The test environment not being headless without SDK

**If any precondition is unmet**: the test silently passes with empty
`supertypes` — which is indistinguishable from a correct result when the
scope *should* exclude stdlib.

**Mitigation**: The test should either (a) verify the SDK is configured
before running, or (b) test both scopes explicitly:
`scope = "project_files"` → empty supertypes (stdlib excluded),
`scope = "project_and_libraries"` → `"String"` present.

### A.5 Summary

| Limitation | Severity | Root cause | Likely fix |
|------------|----------|------------|------------|
| MRO ordering not verified | Medium | Test assertions too loose | Add position checks to existing tests |
| Dynamic class/module creation | Medium | PSI cannot resolve method-call types | Requires data-flow analysis (out of scope) |
| Singleton class / eigenclass | Low | No PSI coverage for `class << self` blocks | Investigate Ruby plugin PSI; add handler support |
| Stdlib SDK preconditions | Low | Test environment assumptions | Add precondition checks or dual-scope assertions |