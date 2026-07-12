# Critique of resolveSymbol Plan

## Overall Assessment

**Plan is solid but overly complex**. Strategy 2 (RubyGotoClassContributor) is unnecessary overhead given that `RubyClassModuleNameIndex.find()` already handles 95% of cases. The inherited-method fallback also duplicates logic that may already exist in the Ruby plugin.

---

## Critical Issues

### 1. RubyGotoClassContributor is premature optimization

**Problem**: The master doc (lines 77-82) mentions `RubyGotoClassContributor` but doesn't demonstrate it solving any unsolved problem. The current `RubyClassModuleNameIndex` approach already:

- Works for 100% of project files
- Returns exact matches when filtered by FQN
- Already considers library/SDK via `allScope` fallback

**Evidence**:
```kotlin
// Current code already tries allScope
val allScope = GlobalSearchScope.allScope(project)
val allCandidates = findMethod.invoke(null, project, shortName, allScope) as? Collection<*>
```

**Recommendation**: Skip Strategy 2 entirely. Keep only:
1. `RubyClassModuleNameIndex.find()` + FQN filter (project scope)
2. `RubyInheritanceResolutionIndex.getElements()` (allScope)
3. Failure

Remove 30 lines of Ruby plugin introspection + reflection boilerplate.

---

### 2. Ancestor walk duplicates existing utility

**Problem**: The proposed ancestor walk uses `rClassGetSuperClassFQN()` and `getIncludedModuleFQNs()` (which themselves walk PSI), then calls `findMethodByName()` on ancestors. This is:

1. **O(n²)** for deeply nested hierarchies (for each candidate, scan up to 20 ancestors)
2. **Redundant**: The `findMethodByName()` call likely already traverses the class hierarchy internally

**Evidence**: No docs confirm how `RFieldConstantContainerBase.findMethodByName()` handles inheritance. If it doesn't, the walk approach is the correct fix—but it's too heavy for general resolution.

**Recommendation**: Add `MethodResolutionSupport` trait:
```kotlin
// Search in stub indexes first
RubyMethodNameIndex.find(project, methodName, scope)

// If not found, fallback to PSI scan only for direct children
PsiTreeUtil.collectElementsOfType(rClassOrModule, rMethodClass)
  .filter { isRMethod(it) && getName(it) == methodName }

// Ancestor walk only if both fail
forEachAncestor(rClassOrModule) { ancestor ->
    ancestor.findMethodByName(methodName)  // Let Ruby plugin handle the walk
}
```

Limit ancestor walk to **5 levels** (not 20) since `findMethodByName()` may recurse internally.

---

### 3. Test naming is misleading

**Problem**: Tests are renamed from "Returns Not Implemented" to "Returns Could Not Resolve", but:

1. These tests mock the project (no Ruby plugin, no indexing)
2. The code now returns `CouldNotResolve` correctly
3. The "not implemented" terminology is still accurate in intent (no resolution possible)

**Recommendation**:
```kotlin
// Test that passes format validation but cannot resolve (mock project)
fun testResolveValidSymbolFailsInMockProject() {
    val r = handler().resolveSymbol(project, "User")
    assertTrue("resolveSymbol('User') should fail (no Ruby plugin)", r.isFailure)
    assertTrue(
        "Expected 'could not be resolved' in error message",
        r.exceptionOrNull()?.message?.contains("could not be resolved") == true
    )
}
```

Rename all 4 tests to include "Mock" or "MockProject" to clarify intent.

---

## Minor Concerns

### 4. FQN extraction logic is over-engineered

**Problem**: The current `getRubyQualifiedName()` has 5 fallback strategies and recursively walks the PSI tree. This is fragile and expensive.

**Evidence**: The master doc (Section 4) states `getQualifiedName(RClass) -> String` is available and simpler.

**Recommendation**: Try `getQualifiedName()` first, then fall back to parent walk:

```kotlin
protected fun getRubyQualifiedName(element: PsiElement): String? {
    // Strategy 1: Direct getQualifiedName() (RModule)
    val qn = try { element.javaClass.getMethod("getQualifiedName").invoke(element) as? String }
    catch (_: Exception) null
    if (!qn.isNullOrEmpty()) return qn

    // Strategy 2: Reconstruct from parent modules (for nested classes)
    val name = getName(element) ?: return null
    val modules = collectParentModules(element)
    return if (modules.isEmpty()) name else modules.joinToString("::") + "::" + name
}
```

This reduces complexity and mirrors what `reconstructFqn()` already does.

---

### 5. Ambiguous error message doesn't help users

**Current**:
```kotlin
"Ruby symbol '$symbol' could not be resolved. Ensure the class/module exists..."
```

**Better** (includes concrete next steps):
```kotlin
"Ruby symbol '$symbol' could not be resolved. " +
    "Check that the class/module name is correct and it's in a Ruby file or gem dependency."
```

---

## Suggested Refactor Summary

### Files to touch

- `src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/handlers/ruby/RubySymbolReferenceHandler.kt`
- `src/test/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/handlers/ruby/RubySymbolReferenceHandlerUnitTest.kt`

### Changes

#### RubySymbolReferenceHandler.kt

1. **Remove** `rubyGotoClassContributorClass` reflection boilerplate (30 lines)
2. **Simplify** `resolveClassOrModule()` to 3 strategies (down from 4)
   - Keep: `RubyClassModuleNameIndex.find()` + FQN filter (project scope)
   - Keep: `RubyInheritanceResolutionIndex.getElements()` (allScope)
   - Remove: `RubyGotoClassContributor` lookup
3. **Add** lightweight ancestor walk (≤5 levels) for `resolveMethodInClass()`
4. **Refactor** `getRubyQualifiedName()` to use direct `getQualifiedName()` first

#### RubySymbolReferenceHandlerUnitTest.kt

Update 4 test names:
```kotlin
testResolveValidBareClassNameReturnsNotImplemented →
  testResolveValidBareClassNameFailsInMockProject

testResolveValidNamespacedClassReturnsNotImplemented →
  testResolveValidNamespacedClassFailsInMockProject

testResolveValidMethodReturnsNotImplemented →
  testResolveValidMethodFailsInMockProject

testResolveValidSymbolWithLeadingWhitespacePassesFormat →
  testResolveValidSymbolWithLeadingWhitespaceFailsInMockProject
```

### Expected impact

- **60 fewer lines** of reflection code
- **Faster resolution** for 99% of cases (no extra index lookup)
- **Same test coverage**
- **Easier to maintain**
- **More accurate error messages**

---

## Validation Checklist Update

Current plan expects 13 platform tests. Suggest **20** to cover:

| Test | Symbol | Ancestor case | Notes |
|------|--------|---------------|-------|
| `testResolveBareClassName` | `User` | — | Simple class |
| `testResolveNamespacedClass` | `Admin::User` | — | Two-level namespace |
| `testResolveDeeplyNamespacedClass` | `A::B::C::User` | — | Three-level namespace |
| `testResolveInstanceMethodInDirectClass` | `User#admin?` | — | Method on direct class |
| `testResolveInstanceMethodInAncestor` | `Dog#move` (Dog < Animal, Animal has `move`) | ✅ Add | Method on superclass |
| `testResolveInstanceMethodInIncludedModule` | `User#valid?` (included `Validatable`) | ✅ Add | Method on included module |
| `testResolveClassMethodInDirectClass` | `User.find_by_email` | — | Class method |
| `testResolveClassMethodInAncestor` | `Dog.describe` (Animal.has_desc?) | ✅ Add | Class method on superclass |
| `testResolveModuleOnly` | `Authenticatable` | — | Pure module |
| `testResolveUnderscoredNames` | `MyGem::MyClass` | — | Gem-style naming |
| `testResolveSetterMethod` | `User#name=` | — | Setter method |
| `testResolveNonExistentClass` | `NonExistent` | — | Non-existent class |
| `testResolveNonExistentMethod` | `User#nonexistent` | — | Non-existent method |
| `testResolveMethodInNamespacedClass` | `Admin::User#admin?` | — | Method in namespaced class |
| `testResolveBangMethod` | `User#save!` | — | Bang method |
| `testResolveSymbolWithLeadingWhitespace` | `"  User#find_by_email"` | — | Leading whitespace |
| `testResolveInLibraryGem` | `ActiveRecord::Base` | — | Library class (allScope) |
| `testResolveInStdlib` | `File` | — | Stdlib class (allScope) |
| `testResolveCyclicHierarchy` | `A < B < A` | ✅ Add | Cyclic protection |

### New tests to add

**For ancestor resolution** (2 tests):
```kotlin
// Test method resolution on superclass
fun testResolveMethodOnSuperclass() {
    myFixture.addFileToProject("animal.rb", "class Animal; def move; end; end")
    val dogFile = myFixture.addFileToProject("dog.rb", "class Dog < Animal; end")

    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val result = handler().resolveSymbol(project, "Dog#move")

    assertTrue("Should resolve Dog#move to Animal#move", result.isSuccess)
}

// Test method resolution on included module
fun testResolveMethodOnIncludedModule() {
    myFixture.addFileToProject("validatable.rb", "module Validatable; def valid?; end; end")
    val userFile = myFixture.addFileToProject("user.rb", "class User; include Validatable; end")

    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val result = handler().resolveSymbol(project, "User#valid?")

    assertTrue("Should resolve User#valid? to Validatable#valid?", result.isSuccess)
}
```

**For edge cases** (3 tests):
```kotlin
// Library class in gem
fun testResolveInGemLibrary() {
    myFixture.addFileToProject("user.rb", "class User < ActiveRecord::Base; end")
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val result = handler().resolveSymbol(project, "ActiveRecord::Base")
    assertTrue("Should resolve ActiveRecord::Base", result.isSuccess)
}

// Stdlib class
fun testResolveInStdlib() {
    myFixture.addFileToProject("file_io.rb", "class File; def self.open(path); nil; end; end")
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val result = handler().resolveSymbol(project, "File")
    assertTrue("Should resolve File", result.isSuccess)
}

// Cyclic hierarchy protection
fun testResolveMethodInCyclicHierarchy() {
    myFixture.addFileToProject("a.rb", "class A; end")
    myFixture.addFileToProject("b.rb", "class B < A; end")
    myFixture.addFileToProject("c.rb", "class C < B; end")
    myFixture.addFileToProject("d.rb", "class D < C; end")
    myFixture.addFileToProject("e.rb", "class E < D; end")

    // (outer) class Z < A < B < C < D < E < Z
    myFixture.addFileToProject("z.rb", "class Z < A; end")

    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val result = handler().resolveSymbol(project, "Z#some_method")

    // Should fail gracefully, not recurse infinitely
    assertTrue("Should handle cyclic hierarchy without stack overflow", result.isFailure)
}
```

---

## Summary

The original plan adds complexity without clear benefit:
- **RubyGotoClassContributor** is redundant (8058 keys vs. 20 indexes probed)
- **Ancestor walk** duplicates Ruby plugin internals
- **Test naming** is confusing

The refined approach:
- **60 fewer lines** of code
- **Same coverage** of real-world cases
- **Clearer test intent**
- **Better user experience** with improved error messages