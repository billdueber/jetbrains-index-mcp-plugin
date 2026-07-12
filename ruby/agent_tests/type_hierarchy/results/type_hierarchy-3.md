# Ruby Type Hierarchy Test Results

**Date:** 2026-07-12
**Plugin build:** Current (after module-level include fix)
**IDE:** IntelliJ IDEA Ultimate with Ruby plugin
**Project:** `/Users/dueberb/devel/ai/jbimcp`

## Summary

**27 / 28 tests PASSED** — 1 expected failure.

| Test | Description | Result |
|------|-------------|--------|
| 1 | `testSimpleInheritance` (Dog < Animal) | **PASSED** |
| 2 | `testIncludedModulesAppearAsSupertypes` (User includes Authenticatable) | **PASSED** |
| 3 | `testModuleOnly` (standalone Auditable module) | **PASSED** |
| 4 | `testNamespacedClass` (Admin::User) | **PASSED** |
| 5 | `testDeepChain` (Child < Parent < GrandParent, 3-level) | **PASSED** |
| 6 | `testFindsSubtypes` (Animal -> Dog, Cat) | **PASSED** |
| 7 | `testMethodInsideClassFindsContainingClass` (Service#perform -> Service) | **PASSED** |
| 8 | `testExtendModule` (HasExtend extends Publishable) | **PASSED** |
| 9 | `testClassWithBothSuperclassAndModules` (Article < Document + includes) | **PASSED** |
| 10 | `testModuleIncludingAnotherModule` (ParentModule includes HelperModule) | **PASSED** |
| 11 | `testNestedModules` (A::B) | **PASSED** |
| 12 | `testRootObjectSupertype` (Foo, no supertype) | **PASSED** |
| 13 | `testFileWithMultipleClasses` (FirstClass + SecondClass in one file) | **PASSED** |
| 14 | `testClassWithNoBody` (EmptyClass) | **PASSED** |
| 15 | `testInheritsFromStandardLibrary` (MyString < String with scope=all) | **PASSED** |
| 16 | `testMultipleIncludedModules` (MultiInclude includes A, B, C) | **PASSED** |
| 17 | `testSelfReferenceGuard` (GrandParent -> Parent as subtype) | **PASSED** |
| 18 | `testHandlerRejectsNonRubyElement` (Kotlin file) | **PASSED** |
| 19 | `testSubtypeLimitRespected` (101 siblings -> 100 subtypes) | **PASSED** |
| 20 | `testPrependModule` (PrependTest prepends Auditable) | **FAILED (expected)** |
| 21 | `testModuleExtend` (ExtendTarget extends Publishable) | **PASSED** |
| 22 | `testSuperclassIncludeExtend` (FullCombo < Document + include + extend) | **PASSED** |
| 23 | `testNamespacedIncludeTarget` (NamespacedInclude includes Namespace::Helpers) | **PASSED** |
| 24 | `testTransitiveMixins` (TransitiveChild < TransitiveParent includes Publishable) | **PASSED** |
| 25 | `testCyclicIncludeGuard` (CycleA <-> CycleB) | **PASSED** |
| 26 | `testMissingFileError` (nonexistent.rb) | **PASSED** |
| 27 | `testInvalidPositionError` (no_class.rb at position 1:1) | **PASSED** |
| 28 | `testSyntaxErrorInFile` (ValidClass + BrokenClass + AnotherValidClass) | **PASSED (informational)** |

## Notable improvement

**Test 10 now PASSES** where it previously failed. The module-level include resolution (`module ParentModule; include HelperModule`) correctly shows `HelperModule` as a supertype with kind `MODULE`. This was the only failure in the previous run. The fix that resolved class-level include (tests 2, 8, 9, 16) has now been extended to cover `RModule` elements as well.

## Only failure: Test 20 (expected)

### testPrependModule

**Purpose:** Verify that `class PrependTest; prepend Auditable` shows `Auditable` as a supertype.

**Actual response:**
```json
{
  "element": {"name": "PrependTest", "kind": "CLASS"},
  "supertypes": [],
  "subtypes": []
}
```

**Expected:** `supertypes` should contain `"Auditable"` with `kind = "MODULE"`.

**Root cause:** The handler's `processCallsOfType` only handles `INCLUDE_CALL` and `EXTEND_CALL`. `PREPEND_CALL` is not processed. This is a known gap — the test document notes this is expected to fail.

## Key observations

1. **All core type hierarchy features work correctly (27/28 tests pass):**
   - Simple inheritance (superclass FQN resolution)
   - Module include and extend for both classes and modules
   - Namespaced classes and modules (FQN walking)
   - Deep transitive hierarchy (recursive supertype traversal)
   - Transitive mixins through superclass chain
   - Subtype discovery (reverse index lookup)
   - Method-to-class container resolution
   - Multiple classes in single file
   - Empty classes and classes without explicit superclass
   - Standard library inheritance (with `scope=project_and_libraries`)
   - Subtype limit capping at exactly 100
   - Non-Ruby file rejection
   - Cyclic include guard (visited set prevents infinite recursion)
   - Missing file and invalid position error handling
   - Syntax error recovery (Ruby plugin PSI error recovery handles broken syntax)

2. **The handler now resolves includes for both `RClass` and `RModule`** — this closes the gap identified in the previous session.

3. **The subtype limit** (test 19) confirms exactly 100 entries, correctly enforced.

## Remaining gap

The single remaining gap is `prepend` support (test 20). The handler needs a `PREPEND_CALL` code path in `processCallsOfType` or a reflection-based call processing that handles all three call types (`INCLUDE_CALL`, `EXTEND_CALL`, `PREPEND_CALL`) generically.