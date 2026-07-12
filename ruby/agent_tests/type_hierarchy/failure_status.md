# Type Hierarchy Test Status

Source: `ruby/agent_tests/results/type_hierarchy-3.md` (28/28 PASS, 0 FAIL; test 29 added)
Platform test: `RubyTypeHierarchyPlatformTest.kt`

| # | Test | Agent Result | Platform Test | Notes |
|---|------|-------------|---------------|-------|
| 1 | `testSimpleInheritance` | PASS | `testSimpleInheritance` | |
| 2 | `testIncludedModulesAppearAsSupertypes` | PASS | `testIncludedModulesAppearAsSupertypes` | |
| 3 | `testModuleOnly` | PASS | `testModuleOnly` | |
| 4 | `testNamespacedClass` | PASS | `testNamespacedClass` | |
| 5 | `testDeepChain` | PASS | `testDeepChain` | |
| 6 | `testFindsSubtypes` | PASS | `testFindsSubtypes` | |
| 7 | `testMethodInsideClassFindsContainingClass` | PASS | `testMethodInsideClassFindsContainingClass` | |
| 8 | `testExtendModule` | PASS | `testExtendModule` | |
| 9 | `testClassWithBothSuperclassAndModules` | PASS | `testClassWithBothSuperclassAndModules` | |
| 10 | `testModuleIncludingAnotherModule` | PASS | `testModuleIncludingAnotherModule` | |
| 11 | `testNestedModules` | PASS | `testNestedModules` | |
| 12 | `testRootObjectSupertype` | PASS | `testRootObjectSupertype` | |
| 13 | `testFileWithMultipleClasses` | PASS | `testFileWithMultipleClasses` | |
| 14 | `testClassWithNoBody` | PASS | `testClassWithNoBody` | |
| 15 | `testInheritsFromStandardLibrary` | PASS | `testInheritsFromStandardLibrary` | |
| 16 | `testMultipleIncludedModules` | PASS | `testMultipleIncludedModules` | |
| 17 | `testSelfReferenceGuard` | PASS | `testSelfReferenceGuard` | |
| 18 | `testHandlerRejectsNonRubyElement` | PASS | `testHandlerRejectsNonRubyElement` | Kotlin file: RubyTypeHierarchyHandler should not claim it |
| 19 | `testSubtypeLimitRespected` | PASS | `testSubtypeLimitRespected` | |
| 20 | `testPrependModule` | **PASS** | `testPrependModule` | `PREPEND_CALL` now handled; platform test exists |
| 21 | `testModuleExtend` | PASS | `testModuleExtend` | |
| 22 | `testSuperclassIncludeExtend` | PASS | `testSuperclassIncludeExtend` | |
| 23 | `testNamespacedIncludeTarget` | PASS | `testNamespacedIncludeTarget` | |
| 24 | `testTransitiveMixins` | PASS | `testTransitiveMixins` | |
| 25 | `testCyclicIncludeGuard` | PASS | `testCyclicIncludeGuard` | |
| 26 | `testMissingFileError` | PASS | `testMissingFileError` | Empty Ruby file → null hierarchy (file-not-found is tool-level, tested agent-side) |
| 27 | `testInvalidPositionError` | PASS | `testInvalidPositionError` | |
| 28 | `testSyntaxErrorInFile` | PASS (info) | `testSyntaxErrorInFile` | Informational only |
| 29 | `testMultiLevelTransitiveMixins` | PASS | `testMultiLevelTransitiveMixins` | 3-hop transitive: DeepChild → DeepParent → DeepGrandParent → DeepModule |

## Summary

| Status | Count | Tests |
|--------|-------|-------|
| PASS + platform test present | 28 | 1-29 |
| PASS + platform test **missing** | 0 | — |
| **FAIL** (expected) | 0 | — |
| **FAIL** (unexpected) | 0 | — |