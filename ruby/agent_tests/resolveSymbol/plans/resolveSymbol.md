# Plan: Build Out `RubySymbolReferenceHandler.resolveSymbol`

## Current State

- **Format validation**: complete (27 unit tests, all pass)
- **Resolution logic**: partially written — uses `RubyClassModuleNameIndex` + `RubyInheritanceResolutionIndex` for class/module, `findMethodByName` + child scan for methods
- **Unit tests**: lagging — they assert `"not yet implemented"` but the code now returns `"could not be resolved"` (mock project has no real indexing, so `find()` returns empty)
- **No platform tests**: no `testData/ruby/` fixtures, no `BasePlatformTestCase` test class
- **No `RubyGotoClassContributor` usage**: the master doc recommends it but it's not wired

---

## 1. Fix unit tests to match the actual resolution code

**Files**: `src/test/kotlin/.../handlers/ruby/RubySymbolReferenceHandlerUnitTest.kt`

Update 3 tests that assert `"not yet implemented"` — with a mock project, index lookups
return null, so the code falls through to `"could not be resolved"`:

| Old test name | New action |
|---|---|
| `testResolveValidBareClassNameReturnsNotImplemented` | Rename to `testResolveValidBareClassNameReturnsCouldNotResolve`, assert `"could not be resolved"` |
| `testResolveValidNamespacedClassReturnsNotImplemented` | Rename to `testResolveValidNamespacedClassReturnsCouldNotResolve`, assert `"could not be resolved"` |
| `testResolveValidMethodReturnsNotImplemented` | Rename to `testResolveValidMethodReturnsCouldNotResolve`, assert `"could not be resolved"` |
| `testResolveValidSymbolWithLeadingWhitespacePassesFormat` | Update assertion from `"not yet implemented"` to `"could not be resolved"` |

All 24 format-validation and registry tests remain unchanged.

## 2. Add `RubyGotoClassContributor` as primary resolution strategy

**File**: `src/main/kotlin/.../handlers/ruby/RubySymbolReferenceHandler.kt`

**Why**: `RubyGotoClassContributor` implements `ChooseByNameContributor` — the Ruby
plugin's own resolution path for "Go to Class/Symbol". It's the most reliable way to
find any class/module in index, covering project files, gems, and stdlib.

**Reflection setup** in `BaseRubyHandler.kt` (new lazy val):

```kotlin
protected val rubyGotoClassContributorClass: Class<*>? by lazy {
    try {
        Class.forName(
            "org.jetbrains.plugins.ruby.ruby.lang.navigation.RubyGotoClassContributor"
        )
    } catch (e: ClassNotFoundException) { null }
}
```

**Resolution logic** (insert before `RubyClassModuleNameIndex` in `resolveClassOrModule`):

```kotlin
// Strategy 1: RubyGotoClassContributor — Ruby plugin's own class resolution
val contributorClass = rubyGotoClassContributorClass
if (contributorClass != null) {
    try {
        val contributor = contributorClass.getDeclaredConstructor().newInstance()
        val getItemsByName = contributorClass.getMethod(
            "getItemsByName",
            String::class.java,
            String::class.java,
            Project::class.java,
            Boolean::class.javaPrimitiveType
        )
        val items = getItemsByName.invoke(contributor, shortName, shortName, project, false) as? Array<*>
        val exactMatch = items?.filterIsInstance<PsiElement>()?.firstOrNull { candidate ->
            getRubyQualifiedName(candidate) == symbol
        }
        if (exactMatch is PsiNamedElement) return Result.success(exactMatch)
    } catch (e: Exception) {
        LOG.warn("RubyGotoClassContributor lookup failed for '$symbol': ${e.message}")
    }
}
```

**Placement**: Strategy 1 (before `RubyClassModuleNameIndex`). If it returns a match,
use it. Fall through to existing strategies if not.

**Edge cases**: `getItemsByName` returns `NavigationItem[]` — filter to `PsiElement`,
check FQN match. Pass `false` for `inclNonProjectFiles` first, then `true` as fallback.

## 3. Create Ruby platform test class + fixtures

### New test class

**File**: `src/test/kotlin/.../handlers/ruby/RubySymbolReferenceHandlerTest.kt`

Extends `BasePlatformTestCase`. Uses `RubyHandlersTest` base pattern (like
`RubyTypeHierarchyPlatformTest`).

### Fixture files

**Directory**: `src/test/testData/ruby/symbol_reference/`

| File | Content |
|---|---|
| `simple_class.rb` | `class User; end` |
| `namespaced_class.rb` | `module Admin; class User; end; end` |
| `deeply_namespaced.rb` | `module A; module B; module C; class User; end; end; end; end` |
| `class_with_methods.rb` | `class User; def admin?; end; def find_by_email(email); end; def save!; end; end` |
| `class_with_class_methods.rb` | `class User; def self.find_by_email; end; end` |
| `module_only.rb` | `module Authenticatable; end` |
| `underscored_names.rb` | `module MyGem; class MyClass; end; end` |
| `class_with_setter.rb` | `class User; def name=(val); @name = val; end; end` |

### Tests (13 tests)

| Test | Symbol | Expected |
|---|---|---|
| `testResolveBareClassName` | `"User"` | PsiNamedElement, name = `"User"` |
| `testResolveNamespacedClass` | `"Admin::User"` | PsiNamedElement, FQN = `"Admin::User"` |
| `testResolveDeeplyNamespacedClass` | `"A::B::C::User"` | PsiNamedElement, FQN = `"A::B::C::User"` |
| `testResolveInstanceMethod` | `"User#admin?"` | RMethod, name = `"admin?"` |
| `testResolveClassMethod` | `"User.find_by_email"` | RMethod (class-level) |
| `testResolveModuleOnly` | `"Authenticatable"` | RModule, name = `"Authenticatable"` |
| `testResolveUnderscoredNames` | `"MyGem::MyClass"` | PsiNamedElement, FQN = `"MyGem::MyClass"` |
| `testResolveSetterMethod` | `"User#name="` | RMethod, name = `"name="` |
| `testResolveNonExistentClass` | `"NonExistent"` | Failure |
| `testResolveNonExistentMethod` | `"User#nonexistent"` | Failure |
| `testResolveMethodInNamespacedClass` | `"Admin::User#admin?"` | RMethod |
| `testResolveBangMethod` | `"User#save!"` | RMethod, name = `"save!"` |
| `testResolveSymbolWithLeadingWhitespace` | `"  User#find_by_email"` | Success after trim |

## 4. Refine `resolveClassOrModule` priority strategy

**File**: `src/main/kotlin/.../handlers/ruby/RubySymbolReferenceHandler.kt`

Current order in `resolveClassOrModule()`:

1. `RubyClassModuleNameIndex.find()` — short name → candidates, filter by FQN
2. `RubyInheritanceResolutionIndex.getElements()` — own-FQN → element
3. Failure

New order:

1. **`RubyGotoClassContributor.getItemsByName()`** — Ruby plugin's own class resolution (new)
2. **`RubyClassModuleNameIndex.find()`** — stub index by short name, filtered by FQN (existing)
3. **`RubyInheritanceResolutionIndex.getElements()`** — own-FQN → element (existing)
4. **Failure** with `"could not be resolved"` (existing, update message to include `RubyGotoClassContributor` in the hint)

## 5. Refine `resolveMethodInClass` for inherited methods

**File**: `src/main/kotlin/.../handlers/ruby/RubySymbolReferenceHandler.kt`

Current: `findMethodByName()` → child scan → failure

**Problem**: `findMethodByName()` is inherited from `RFieldConstantContainerBase` and
only finds methods declared directly on the class. For `User#admin?` where `admin?` is
defined in an included module, it misses.

**Fix**: Add ancestor walk fallback after `findMethodByName()` and child scan fail:

```kotlin
// Fallback: walk superclass and included modules
if (rClassClass?.isInstance(rClassOrModule) == true) {
    val superClassFqn = rClassGetSuperClassFQN(rClassOrModule)
    val includedFqns = getIncludedModuleFQNs(project, rClassOrModule)
    val allAncestorFqns = listOfNotNull(superClassFqn) + includedFqns
    for (ancestorFqn in allAncestorFqns) {
        val ancestor = resolveByFQN(project, ancestorFqn, GlobalSearchScope.allScope(project))
        if (ancestor != null) {
            // Try findMethodByName on the ancestor
            val method = try {
                val findMethod = ancestor.javaClass.getMethod("findMethodByName", String::class.java)
                findMethod.invoke(ancestor, methodName) as? PsiNamedElement
            } catch (_: Exception) { null }
            if (method != null) return Result.success(method)
        }
    }
}
```

**Guard**: Limit ancestor walk depth to 20 to prevent infinite loops on cyclic hierarchies.

## 6. Update KDoc on `RubySymbolReferenceHandler` class

**File**: `src/main/kotlin/.../handlers/ruby/RubySymbolReferenceHandler.kt`

Update the class-level KDoc to document the resolution strategy tiers:

```
 * Resolution strategy (in order):
 * 1. RubyGotoClassContributor.getItemsByName() — Ruby plugin's own Go-to-Class
 * 2. RubyClassModuleNameIndex.find() — stub index by short name, filtered by FQN
 * 3. RubyInheritanceResolutionIndex.getElements() — own-FQN stub index
 * 4. Failure with "could not be resolved"
 *
 * Method resolution (after class is found):
 * 1. findMethodByName() — direct declaration on the class
 * 2. Child PSI scan — RMethod children with matching name
 * 3. Ancestor walk — superclass + included modules, try findMethodByName on each
```

## 7. Verification checklist

| Step | Command | Expected |
|---|---|---|
| Unit tests | `./gradlew test --tests "*RubySymbolReferenceHandlerUnitTest*"` | 27 pass |
| Platform tests | `./gradlew test --tests "*RubySymbolReferenceHandlerTest*"` | 13 pass (CI only) |
| Other Ruby tests | `./gradlew test --tests "*RubyHandlersUnitTest*"` | All pass |
| Compilation | `./gradlew compileKotlin` | No errors |
| End-to-end | `ide_find_definition` with `language: "Ruby", symbol: "User"` in IntelliJ | Resolves to PSI element |