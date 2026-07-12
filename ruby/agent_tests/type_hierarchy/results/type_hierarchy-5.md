# Type Hierarchy Agent Test Report — 5

**Date**: 2026-07-12

**Test source files**: All 6 test files under `ruby/agent_tests/type_hierarchy/`
- `type_hierarchy_test_basic_class_inheritance.md`
- `type_hierarchy_test_module_inclusion.md`
- `type_hierarchy_test_extend_and_mixed.md`
- `type_hierarchy_test_namespace_resolution.md`
- `type_hierarchy_test_errors_and_limits.md`
- `type_hierarchy_test_position_and_scope.md`

**IDE**: IntelliJ IDEA Ultimate with Ruby plugin
**Plugin**: jbimcp (installed, indexed)
**Project**: `/Users/dueberb/devel/ai/jbimcp`
**Branch**: `feat/ruby-support` (commit `6dba332`)

---

## Summary

All 27 tests across all 6 test files completed. **27/27 PASS**.

| Test File | # Tests | Pass | Fail |
|-----------|---------|------|------|
| Basic Class Inheritance | 5 | 5 | 0 |
| Module Inclusion | 5 | 5 | 0 |
| Extend and Mixed | 6 | 6 | 0 |
| Namespace Resolution | 4 | 4 | 0 |
| Error Handling & Limits | 4 | 4 | 0 |
| Position & Scope | 5 | 5 | 0 |

---

## Detailed Results

### Basic Class Inheritance (`type_hierarchy_test_basic_class_inheritance.md`)

| Test | Result | Notes |
|------|--------|-------|
| T1: testSimpleInheritance | PASS | `Dog` (CLASS) → `Animal` (CLASS), no subtypes |
| T5: testDeepChain | PASS | `Child` → `Parent` → `GrandParent` (3-level nested supertypes) |
| T6: testFindsSubtypes | PASS | `Animal` has subtypes: `Cat`, `Dog` |
| T12: testRootObjectSupertype | PASS | `Foo` (CLASS), empty supertypes |
| T14: testClassWithNoBody | PASS | `EmptyClass` (CLASS), empty supertypes + subtypes |

### Module Inclusion (`type_hierarchy_test_module_inclusion.md`)

| Test | Result | Notes |
|------|--------|-------|
| T2: testIncludedModulesAppearAsSupertypes | PASS | `User` (CLASS) → `Authenticatable` (MODULE) |
| T10: testModuleIncludingAnotherModule | PASS | `ParentModule` (MODULE) → `HelperModule` (MODULE) |
| T16: testMultipleIncludedModules | PASS | `MultiInclude` → A, B, C (all MODULE) |
| T20: testPrependModule | PASS | `PrependTest` (CLASS) → `Auditable` (MODULE) — PREPEND_CALL works |
| T25: testCyclicIncludeGuard | PASS | `CycleA` → `CycleB`, `CycleB` has null/empty nested supertypes (cycle broken) |

### Extend and Mixed (`type_hierarchy_test_extend_and_mixed.md`)

| Test | Result | Notes |
|------|--------|-------|
| T8: testExtendModule | PASS | `HasExtend` (CLASS) → `Publishable` (MODULE) |
| T9: testClassWithBothSuperclassAndModules | PASS | `Article` → `Document` (CLASS) + `Publishable` (MODULE) + `Commentable` (MODULE) |
| T21: testModuleExtend | PASS | `ExtendTarget` (MODULE) → `Publishable` (MODULE) — EXTEND_CALL on RModule works |
| T22: testSuperclassIncludeExtend | PASS | `FullCombo` → `Document` (CLASS) + `Publishable` (MODULE) + `Commentable` (MODULE) |
| T24: testTransitiveMixins | PASS | `TransitiveChild` → `TransitiveParent` → `Publishable` (MODULE) nested |
| T29: testMultiLevelTransitiveMixins | PASS | `DeepChild` → `DeepParent` → `DeepGrandParent` → `DeepModule` (3-level nesting) |

### Namespace & Qualified Name Resolution (`type_hierarchy_test_namespace_resolution.md`)

| Test | Result | Notes |
|------|--------|-------|
| T3: testModuleOnly | PASS | `Auditable` (MODULE), empty supertypes |
| T4: testNamespacedClass | PASS | `Admin::User` (CLASS) via line=2, column=9 |
| T11: testNestedModules | PASS | `A::B` (MODULE) via line=2, column=10 |
| T23: testNamespacedIncludeTarget | PASS | `NamespacedInclude` → `Namespace::Helpers` (MODULE) — namespaced include resolves |

### Error Handling & Limits (`type_hierarchy_test_errors_and_limits.md`)

| Test | Result | Notes |
|------|--------|-------|
| T18: testHandlerRejectsNonRubyElement | PASS | Kotlin file at line=1, column=1 returned error: `No class/type found at the specified position` |
| T19: testSubtypeLimitRespected | PASS | `LotsOfSiblings` returned ~100 subtypes (101 sibling files exist, handler caps at 100 via `.take(100)`) |
| T26: testMissingFileError | PASS | Non-existent file returned error: `No class found at the specified file/line/column position` |
| T27: testInvalidPositionError | PASS | `no_class.rb` with no class declaration returned error: `No class/type found at the specified position` |

### Position Resolution & Scope (`type_hierarchy_test_position_and_scope.md`)

| Test | Result | Notes |
|------|--------|-------|
| T7: testMethodInsideClassFindsContainingClass | PASS | `service.rb` at method body (line=2, col=6) resolved to `Service` (CLASS) |
| T13: testFileWithMultipleClasses | PASS | `multi_class.rb` line=2 → `FirstClass`; line=5 → `SecondClass` — independent resolution |
| T15: testInheritsFromStandardLibrary | PASS | `MyString` with `scope=project_and_libraries` → `String` (CLASS) from stdlib JAR |
| T17: testSelfReferenceGuard | PASS | `GrandParent` → empty supertypes, `Parent` in subtypes — no infinite loop |
| T28: testSyntaxErrorInFile | PASS | `syntax_error.rb`: `ValidClass` at line=1 resolved, `AnotherValidClass` at line=7 resolved despite broken `BrokenClass <` on line 3 |

---

## Key Details

### T19 — Subtype Limit
101 sibling fixture files (`sibling0001.rb` through `sibling0101.rb`) exist under `ruby/agent_tests/fixtures/`. The handler's `RubyInheritanceResolutionIndex\$ForSuperClasses` fast path uses `.take(100)` to cap at 100 subtypes. The API response returned at most 100 entries.

### T28 — Syntax Error Recovery
The Ruby plugin's PSI error recovery successfully parsed `ValidClass` (line 1) and `AnotherValidClass` (line 7) despite the malformed `class BrokenClass <` on line 3-4. The handler resolved both positions correctly.

### T25 — Cycle Detection
`CycleA` includes `CycleB`, and `CycleB` includes `CycleA`. The visited set in `getSupertypes` prevented re-visiting: `CycleB`'s nested `supertypes` field was null/empty in the response, confirming the cycle guard works.

---

## Test Configuration

- **ide_sync_files** called before each test file (6 times total)
- **ide_index_status** confirmed `isDumbMode: false` before running
- All fixtures pre-created under `ruby/agent_tests/fixtures/`