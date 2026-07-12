# 03 — Ruby PSI Reference

## Core PSI Classes

The Ruby plugin (closed-source) exposes these PSI types. All are accessed via reflection.

### RClass — `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass`
| Method (via reflection) | Return type | Description |
|-------------------------|-------------|-------------|
| `getName()` | `String?` | Class name (e.g., `"UserService"`) |
| `getFullyQualifiedName()` | `String?` | FQN (e.g., `"Services::UserService"`) — **returns null for module-namespaced classes** |
| `getSuperClass()` | `PsiElement?` | Direct superclass RClass |
| `getSuperClassName()` | `String?` | Superclass name before resolution |
| `getIncludedModules()` | `List<PsiElement>` | Modules included via `include`/`extend` |

### RModule — `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule`
| Method (via reflection) | Return type | Description |
|-------------------------|-------------|-------------|
| `getName()` | `String?` | Module name (e.g., `"Authenticatable"`) |
| `getFullyQualifiedName()` | `String?` | FQN (e.g., `"Concerns::Auditable"`) |
| `getIncludedModules()` | `List<PsiElement>` | Modules included in this module |

### RMethod — `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod`
| Method (via reflection) | Return type | Description |
|-------------------------|-------------|-------------|
| `getName()` | `String?` | Method name (e.g., `"find"`, `"admin?"`, `"save!"`) |

### RCallExpression — multiple possible class names
The exact FQN varies across Ruby plugin versions. Try in order:
1. `org.jetbrains.plugins.ruby.ruby.lang.psi.callExpressions.RCallExpression`
2. `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.RCallExpression`
3. `org.jetbrains.plugins.ruby.ruby.lang.psi.RCallExpression`

## Ruby Plugin Resolution Utilities (reflection-only)

### RubyClassResolveUtil
```
org.jetbrains.plugins.ruby.ruby.lang.psi.impl.RubyClassResolveUtil
  .resolveSuperClass(RClass, PsiElement anchor) → List<PsiElement>
  .resolveIncludedModules(RClass/RModule, anchor) → List<PsiElement>
```

### RubyOverrideImplementUtil
```
org.jetbrains.plugins.ruby.ruby.lang.psi.impl.RubyOverrideImplementUtil
  .getOverriddenMethods(RMethod) → Collection<RMethod>
  .getOverridingElements(RMethod) → Collection<RMethod>
  .getOverridingContainers(RClass/RModule, RMethod) → Collection<RClass|RModule>
```

### RubyOverridingMethodsSearch
```
org.jetbrains.plugins.ruby.ruby.lang.search.overriding.RubyOverridingMethodsSearch
  .search(RMethod) → Query<RMethod>
```

## What the various indexes contain

### Ruby's own stub indexes (NOT directly used by our handlers)
The Ruby plugin registers stub indexes (found in extracted XMLs at `/tmp/ruby/lib/modules/`). These are similar to Python's `PyClassNameIndex`. However, our implementation does **not** use them directly — we rely on the platform's generic search APIs:

| Our API | What it searches | Equivalent Ruby-specific index |
|---------|-----------------|-------------------------------|
| `DefinitionsScopedSearch` | Subclasses of a class | `RubyClassInheritorsIndex` (hypothesized, not confirmed) |
| `ReferencesSearch` | Callers of a method | `RubyMethodReferencesIndex` |
| `OptimizedSymbolSearch` | Symbols by name | `RubySymbolIndex` + `ChooseByNameContributor` |

### Platform indexes (always available)
These are IntelliJ platform indexes, not Ruby-specific, but they work for Ruby PSI:
- **Word index** — text-based search (`ide_search_text`)
- **File name index** — file search (`ide_find_file`)
- **Symbol index** — Go to Symbol contributors (`ide_find_symbol`)
- **Reference index** — find usages (`ide_find_references`)
- **Definition index** — find definition (`ide_find_definition`)

### RubyGotoClassContributor (used for symbol resolution)
The Ruby plugin registers a `RubyGotoClassContributor` that implements `ChooseByNameContributor`. This is used by `RubySymbolReferenceHandler` to resolve class/module names to PSI elements:

```kotlin
// In RubySymbolReferenceHandler.resolveSymbol():
val contributor = Class.forName(
    "org.jetbrains.plugins.ruby.ruby.lang.navigation.RubyGotoClassContributor"
)
// contributor.getNames(project) → array of all class/module names (simple names)
// contributor.getItemsByName(name, pattern, project, inclNonProject) → NavigationItem[]
```

## Ruby Language Semantics

### Single inheritance + mixins
- `class Child < Parent` — single superclass only
- `include MyModule` — adds module methods as instance methods (acts like interface implementation)
- `extend MyModule` — adds module methods as class methods

### How we map Ruby concepts to handler operations

| Ruby concept | TypeHierarchy | Implementations | SuperMethods | CallHierarchy |
|-------------|---------------|-----------------|--------------|---------------|
| `class` | supertypes = parent + included modules; subtypes = subclasses | subclasses of this class | methods this overrides | methods this calls |
| `module` (as namespace) | supertypes = included modules; subtypes = classes including it | classes that include/extend this module | — | — |
| `module` (as mixin) | shown as "supertype" of including class | — | — | — |
| `method` | — | methods in subtypes with same name | overridden methods (parent chain + modules) | callers/callees |
| `include` | treated like "implements interface" | — | — | — |
| `super` call | — | — | resolves to parent class or module method | shown in call hierarchy |

### Container resolution (findContainer)
```kotlin
// For an RMethod, walks up parent chain to find nearest RClass or RModule
fun findContainer(element: PsiElement): PsiElement? {
    var current = element.parent
    while (current != null) {
        if (isRClass(current) || isRModule(current)) return current
        current = current.parent
    }
    return null
}
```

### Ancestor collection (for type hierarchy)
```kotlin
// BFS walk: for each class, add superclass + included modules
// Continues until maxAncestors (default 20) or queue exhausted
fun collectAncestors(container: PsiElement, maxAncestors: Int = 20): List<PsiElement>
```
