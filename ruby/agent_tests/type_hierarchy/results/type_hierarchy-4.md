# Type Hierarchy Test Report — Batch 4

**Date**: 2026-07-12  
**Run by**: Agent (intellij-index MCP via jbimcp)  
**IDE**: IntelliJ IDEA 2025.3 with Ruby plugin  
**Project**: `/Users/dueberb/devel/ai/jbimcp`  

---

## Tests Executed

Ran all test procedures from 6 files under `ruby/agent_tests/type_hierarchy/`:

| # | Test File | Tests | Results |
|---|-----------|-------|---------|
| 1 | `type_hierarchy_test_basic_class_inheritance.md` | 5 | 5/5 PASS |
| 2 | `type_hierarchy_test_errors_and_limits.md` | 4 | 4/4 PASS |
| 3 | `type_hierarchy_test_extend_and_mixed.md` | 5 | 5/5 PASS |
| 4 | `type_hierarchy_test_module_inclusion.md` | 5 | 4/5 PASS, 1 EXPECTED FAILURE |
| 5 | `type_hierarchy_test_namespace_resolution.md` | 4 | 4/4 PASS |
| 6 | `type_hierarchy_test_position_and_scope.md` | 5 | 5/5 PASS |
| **Total** | **6 files** | **28 tests** | **27 PASS, 1 EXPECTED FAILURE** |

---

## Detailed Results

### File 1: Basic Class Inheritance (`type_hierarchy_test_basic_class_inheritance.md`)

| Test | Result | Notes |
|------|--------|-------|
| 1. testSimpleInheritance | **PASS** | Dog < Animal: element name="Dog", supertypes=["Animal"], subtypes=[] |
| 5. testDeepChain | **PASS** | Child < Parent < GrandParent: nested supertypes correct (Parent -> GrandParent) |
| 6. testFindsSubtypes | **PASS** | Animal subtypes contain ["Cat", "Dog"] — both found |
| 12. testRootObjectSupertype | **PASS** | Foo: empty supertypes, correct — Ruby plugin returns null for implicit Object |
| 14. testClassWithNoBody | **PASS** | EmptyClass: empty supertypes and subtypes |

### File 2: Error Handling & Limits (`type_hierarchy_test_errors_and_limits.md`)

| Test | Result | Notes |
|------|--------|-------|
| 18. testHandlerRejectsNonRubyElement | **PASS** | Kotlin file returned "No class/type found at the specified position" — error is acceptable per spec (element.language would not be Ruby) |
| 19. testSubtypeLimitRespected | **PASS** | 101 sibling files on disk; response returned exactly 100 subtypes (Sibling0001–Sibling0101 with one missing due to limit) |
| 26. testMissingFileError | **PASS** | Non-existent path returned "No class found at the specified file/line/column position" error |
| 27. testInvalidPositionError | **PASS** | no_class.rb (no classes) returned "No class/type found at the specified position" error |

### File 3: Extend and Mixed Relationships (`type_hierarchy_test_extend_and_mixed.md`)

| Test | Result | Notes |
|------|--------|-------|
| 8. testExtendModule | **PASS** | HasExtend supertypes contain Publishable (MODULE) |
| 9. testClassWithBothSuperclassAndModules | **PASS** | Article supertypes: Document (CLASS) + Publishable + Commentable (MODULEs) |
| 21. testModuleExtend | **PASS** | ExtendTarget (MODULE) supertypes contain Publishable (MODULE) — `extend` on a module works |
| 22. testSuperclassIncludeExtend | **PASS** | FullCombo: Document (CLASS) + Publishable (MODULE) + Commentable (MODULE) — all three paths work independently |
| 24. testTransitiveMixins | **PASS** | TransitiveChild → TransitiveParent supertypes contain Publishable; TransitiveParent directly also shows Publishable |

### File 4: Module Inclusion (`type_hierarchy_test_module_inclusion.md`)

| Test | Result | Notes |
|------|--------|-------|
| 2. testIncludedModulesAppearAsSupertypes | **PASS** | User includes Authenticatable: supertypes show it with kind MODULE |
| 10. testModuleIncludingAnotherModule | **PASS** | ParentModule includes HelperModule: both element and supertype are MODULE |
| 16. testMultipleIncludedModules | **PASS** | MultiInclude supertypes contain A, B, C (all MODULEs) — note: A resolved to `nested_modules.rb` file, not `a.rb` (likely FQN collision in index) |
| 20. testPrependModule | **EXPECTED FAILURE** | PrependTest supertypes=[] — PREPEND_CALL is not handled by the Ruby handler yet (only INCLUDE_CALL and EXTEND_CALL) |
| 25. testCyclicIncludeGuard | **PASS** | CycleA↔CycleB: each shows the other as supertype with null/empty nested supertypes — cycle broken, no infinite recursion |

### File 5: Namespace Resolution (`type_hierarchy_test_namespace_resolution.md`)

| Test | Result | Notes |
|------|--------|-------|
| 3. testModuleOnly | **PASS** | Auditable: name="Auditable", kind="MODULE", language="Ruby" |
| 4. testNamespacedClass | **PASS** | Admin::User at line 2 col 9 resolves to name="Admin::User", kind="CLASS" |
| 11. testNestedModules | **PASS** | A::B at line 2 col 10 resolves to name="A::B", kind="MODULE" |
| 23. testNamespacedIncludeTarget | **PASS** | NamespacedInclude at line 6 col 7 resolves to class; supertypes contain Namespace::Helpers (MODULE) |

### File 6: Position Resolution & Scope (`type_hierarchy_test_position_and_scope.md`)

| Test | Result | Notes |
|------|--------|-------|
| 7. testMethodInsideClassFindsContainingClass | **PASS** | Pointing at `def perform` inside Service resolves to containing class "Service" |
| 13. testFileWithMultipleClasses | **PASS** | Line 2 → "FirstClass", Line 5 → "SecondClass" — both resolve independently |
| 15. testInheritsFromStandardLibrary | **PASS** | MyString < String with scope=project_and_libraries: supertypes contain String from stdlib jar (`string_ext.rb` in plugin bundle) |
| 17. testSelfReferenceGuard | **PASS** | GrandParent: empty supertypes, subtypes contain "Parent" — no infinite recursion |
| 28. testSyntaxErrorInFile | **PASS** | Line 1 col 7 → "ValidClass"; Line 7 col 7 → "AnotherValidClass" — Ruby plugin's PSI error recovery parses valid declarations despite syntax error on line 4 |

---

## Observations & Notes

1. **Prepend not handled (expected)**: Test 20 confirms that `prepend Auditable` produces no supertype. The handler only processes `INCLUDE_CALL` and `EXTEND_CALL`. This is documented known missing functionality.

2. **A module resolved to nested_modules.rb**: In test 16 (MultiInclude), module `A` resolved to file `nested_modules.rb` instead of `a.rb`. This is likely a FQN collision in the Ruby index — both `A` modules have the same short name. The behavior is acceptable; the PSI resolved to one of the valid `module A` declarations.

3. **Stdlib resolution works**: Test 15 (MyString < String) successfully resolved `String` from the Ruby stdlib via `scope=project_and_libraries`. The file path points into the plugin's stubsgen JAR, which is correct.

4. **Cycle guard effective**: Test 25 confirms the `visited` set in `getSupertypes` prevents infinite recursion — CycleA shows CycleB with null nested supertypes, and vice versa.

5. **All fixture files existed pre-run**: No fixture creation was needed. All files in `ruby/agent_tests/fixtures/` were present with correct content.

---

## Summary

**27 of 28 tests pass.** The single expected failure (testPrependModule) is a known gap — the handler does not resolve `prepend` calls. All other functionality (simple inheritance, deep chains, subtype discovery, extend, include, module-module relationships, namespaced FQN resolution, cycle detection, stdlib resolution, method-to-class resolution, syntax error recovery, limit enforcement, and error handling) works correctly.