# Tests Needed: Agent Test Coverage Gaps

**Status**: All tests written as of 2026-07-14. See below for details.

## Legend

| Column | Meaning |
|--------|---------|
| **UT exists** | Pure-logic unit test exists (runs without IDE/Ruby plugin) |
| **PT exists** | Platform test exists (requires IDE + Ruby plugin) |
| UT | Unit test needed — pure logic, no plugin required |
| PT | Platform test needed — requires Ruby plugin, guard with `Assume.assumeTrue(PluginDetectors.ruby.isAvailable)` |
| N/A | Not applicable (cannot be tested in that mode) |
| COVERED | Test already exists matching the agent test spec |

## Call Hierarchy — 5 platform test files (7 .md files)

| Agent test file | What it tests | UT needed | PT needed |
|---|---|---|---|
| `call_hierarchy_test_simple_callers.md` | Basic caller lookup, depth=1 and depth=2 chains | Method key generation, `visited` set cycle detection, depth clamping | PT: caller resolution from position, depth=2 transitive callers |
| `call_hierarchy_test_simple_callees.md` | Basic callee lookup | Same as above (shared logic) | PT: callee resolution from position |
| `call_hierarchy_test_class_methods.md` | Instance/class method callers/callees, mixed types | Method key generation (dot vs hash notation) | PT: class method (`self.`) resolution, mixed instance/class chains |
| `call_hierarchy_test_cross_file.md` | Cross-file call relationships | N/A | PT: cross-file caller/callee resolution |
| `call_hierarchy_test_deep_chain.md` | Deep chain (depth=5) | Max-depth guardrail (unit test exists in `RubyTypeHierarchyHandlerUnitTest`, adapt for call hierarchy) | PT: depth=5 transitive resolution |
| `call_hierarchy_test_edge_cases.md` | No callers/callees, self-referencing (recursive), predicate/bang methods | `visited` set cycle detection (exists in `RubyTypeHierarchyHandlerUnitTest`), predicate/bang method key handling | PT: empty results for orphans, recursion guard, `?`/`!` name preservation |
| `call_hierarchy_test_errors.md` | Invalid direction, missing params, missing file, position outside method, symbol lookup, empty file | Direction enum validation (`callers`/`callees`), file-not-found routing | PT: position-outside-method error, empty file error, symbol-based lookup |
| `call_hierarchy_test_scope.md` | Scope filtering (project_files, project_and_libraries) | N/A | PT: scope parameter routing |

## File Structure — COVERED

All 10 `fs_*.md` tests are covered by `RubyStructureHandlerPlatformTest` ✅

## Find Super Methods — no tests exist

| Agent test file | What it tests | UT needed | PT needed |
|---|---|---|---|
| `fsm_01_basic_override.md` | `Dog#speak` → `Animal#speak`, position inside body, symbol lookup | Symbol pattern parsing for method extraction | PT: basic override resolution |
| `fsm_02_mixin_override.md` | `User#greet` → `Greetable#greet` (include) | Module resolution key generation | PT: mixin override resolution |
| `fsm_03_deep_chain.md` | `Child#speak` → `Parent#speak` → `GrandParent#speak` | Depth limiting, ancestor chain termination | PT: 3-level depth traversal |
| `fsm_04_no_super.md` | Method that does not override anything | Null/invalid parent return handling | PT: no-super returns empty hierarchy |
| `fsm_05_class_methods.md` | `AdminUser.find_by_email` → `User.find_by_email` | Dot-notation class method resolution | PT: class method super chain |
| `fsm_06_cross_file.md` | Method defined in one file overriding method in another | N/A | PT: cross-file override resolution |
| `fsm_07_predicate_bang.md` | `User#admin?` → `parent#admin?`, `save!` → `parent#save!` | Predicate/bang method name handling in parent lookup | PT: `?`/`!` suffix propagation through chain |
| `fsm_08_edge_cases.md` | No explicit superclass, syntax errors, missing file, empty file, cycle detection in module include, non-Ruby language filter | Language filter in `canHandle` | PT: error cases, cycle guard, language filter |

## Resolve Symbol — COVERED

All 13 T1.1–T2.5 cases are covered by `RubySymbolReferenceHandlerTest` ✅

## Summary of required new test files

### Unit tests (pure logic, no plugin)

| File | What to test | Rationale |
|---|---|---|
| `RubyFindSuperMethodsHandlerUnitTest.kt` | Symbol parsing, method key generation, depth limiting, null parent handling, visited-set cycle detection, predicate/bang name handling, language filter | Handler has pure-logic path through hierarchy traversal that can be exercised without PSI |
| `RubyCallHierarchyHandlerUnitTest.kt` | **Already exists** — add: max-depth guardrail from handler (not just type hierarchy), direction enum validation, method key formatting (dot vs hash), visited-set reuse across callers/callees | Expand existing test |

### Platform tests (require Ruby plugin)

| File | What to test | Maps to agent tests |
|---|---|---|
| `RubyCallHierarchyHandlerPlatformTest.kt` | Full call hierarchy execution: callers, callees, depth, class methods, cross-file, edge cases, error paths, scope | All 7 call hierarchy `.md` files |
| `RubyFindSuperMethodsHandlerPlatformTest.kt` | Full find-super-methods: basic override, mixin, deep chain, no-super, class methods, cross-file, predicate/bang, edge cases | All 8 `fsm_*.md` files |

## Detailed gap breakdown

### Call hierarchy — `RubyCallHierarchyHandlerUnitTest`

**Already covers:**
- `languageId` returns `"Ruby"`
- `isAvailable()` false without plugin
- `canHandle()` false without plugin
- Registry wiring
- `getCallHierarchy()` returns null when `findContainingRMethod` fails
- Handler is not a stub class

**Needs to add:**
- Direction enum validation (reject `"sideways"`, accept `"callers"`/`"callees"`)
- Method key generation format: `"ClassName#method"` vs `"ClassName.method"`
- Max-depth clamping (handler clamps to 5 max)
- Visited-set cycle guard initialization for recursion
- Empty/null result from `findContainingRMethod` (already mostly done)

### Call hierarchy — `RubyCallHierarchyHandlerPlatformTest` (NEW)

| Agent test section | Test method | Guard |
|---|---|---|
| `testSimpleCallers` / 1 | `testSimpleCallers` | `requireRubyPlugin()` |
| `testSimpleCallers` / 2 | `testCallersWithDepth` | `requireRubyPlugin()` |
| `testSimpleCallees` / 1 | `testSimpleCallees` | `requireRubyPlugin()` |
| `testSimpleCallees` / 2 | `testCalleesWithDepth` | `requireRubyPlugin()` |
| `testInstanceMethodCallers` | `testInstanceMethodCallers` | `requireRubyPlugin()` |
| `testInstanceMethodCallees` | `testInstanceMethodCallees` | `requireRubyPlugin()` |
| `testClassMethodCallers` | `testClassMethodCallers` | `requireRubyPlugin()` |
| `testMixedInstanceAndClassMethods` | `testMixedCallTypes` | `requireRubyPlugin()` |
| `testCrossFile` | `testCrossFileCallers` | `requireRubyPlugin()` |
| `testDeepChainDepth5` | `testDeepChainDepth5` | `requireRubyPlugin()` |
| `testNoCallers` | `testNoCallers` | `requireRubyPlugin()` |
| `testNoCallees` | `testNoCallees` | `requireRubyPlugin()` |
| `testSelfReferencingMethod` | `testSelfReferencingRecursive` | `requireRubyPlugin()` |
| `testPredicateMethod` | `testPredicateMethodCallers` | `requireRubyPlugin()` |
| `testBangMethod` | `testBangMethodCallers` | `requireRubyPlugin()` |
| `testInvalidDirection` | `testInvalidDirection` | `requireRubyPlugin()` |
| `testMissingDirection` | `testMissingDirection` | `requireRubyPlugin()` |
| `testNonExistentFile` | `testNonExistentFile` | `requireRubyPlugin()` |
| `testPositionOutsideMethod` | `testPositionOutsideMethod` | `requireRubyPlugin()` |
| `testSymbolBasedLookup` | `testSymbolBasedLookup` | `requireRubyPlugin()` |
| `testEmptyFile` | `testCallHierarchyEmptyFile` | `requireRubyPlugin()` |
| `testScope` (project_files vs libraries) | `testScopeProjectAndLibraries` | `requireRubyPlugin()` |

### Find super methods — `RubyFindSuperMethodsHandlerUnitTest` (NEW)

| What to test | Test method |
|---|---|
| Method key generation | `testMethodKeyForInstanceMethod_usesHash`, `testMethodKeyForClassMethod_usesDot` |
| Symbol pattern parsing from `className#method` | `testParsesSymbolWithHash`, `testParsesSymbolWithDot` |
| Max-depth clamping | `testMaxDepthClampedToFive` |
| Null ancestor returns empty list | `testNullSuperReturnsEmptyHierarchy` |
| No-super returns empty list | `testNoSuperWhenMethodNotOnChain` |
| Visited-set prevents cycles | `testVisitedSetPreventsCycle` |
| Language filter (rejects non-Ruby `PsiElement`) | `testCanHandleRejectsNonRubyLanguage` |
| Predicate/bang method name handling in parent lookup | `testPredicateMethodNamePropagated`, `testBangMethodNamePropagated` |
| Registry wiring | `testRegistryExposesRubyFindSuperMethods` |

### Find super methods — `RubyFindSuperMethodsHandlerPlatformTest` (NEW)

| Agent test section | Test method | Guard |
|---|---|---|
| `fsm_01` / 1 — basic override | `testBasicOverride` | `requireRubyPlugin()` |
| `fsm_01` / 2 — position inside method body | `testPositionInsideMethodBody` | `requireRubyPlugin()` |
| `fsm_01` / 3 — language+symbol lookup | `testLanguageSymbolLookup` | `requireRubyPlugin()` |
| `fsm_02` / 1 — mixin override | `testMixinOverride` | `requireRubyPlugin()` |
| `fsm_02` / 2 — mixin symbol lookup | `testMixinSymbolLookup` | `requireRubyPlugin()` |
| `fsm_03` / 1 — deep chain | `testDeepChain` | `requireRubyPlugin()` |
| `fsm_03` / 2 — deep chain symbol lookup | `testDeepChainSymbolLookup` | `requireRubyPlugin()` |
| `fsm_04` / 1 — no super | `testNoSuper` | `requireRubyPlugin()` |
| `fsm_04` / 2 — no super symbol | `testNoSuperSymbolLookup` | `requireRubyPlugin()` |
| `fsm_05` / 1 — class methods | `testClassMethodOverride` | `requireRubyPlugin()` |
| `fsm_05` / 2 — class method symbol | `testClassMethodSymbolLookup` | `requireRubyPlugin()` |
| `fsm_06` / 1 — cross file | `testCrossFileOverride` | `requireRubyPlugin()` |
| `fsm_07` / 1 — predicate method | `testPredicateMethodOverride` | `requireRubyPlugin()` |
| `fsm_07` / 2 — bang method | `testBangMethodOverride` | `requireRubyPlugin()` |
| `fsm_08` / 1 — no explicit superclass | `testNoExplicitSuperclass` | `requireRubyPlugin()` |
| `fsm_08` / 2 — syntax errors | `testSyntaxErrorFile` | `requireRubyPlugin()` |
| `fsm_08` / 3 — missing file | `testMissingFileError` | `requireRubyPlugin()` |
| `fsm_08` / 4 — empty file | `testEmptyFileError` | `requireRubyPlugin()` |
| `fsm_08` / 5 — cycle in module include | `testModuleCycleGuard` | `requireRubyPlugin()` |
| `fsm_08` / 6 — non-Ruby language filter | `testRejectsNonRubyElement` | `requireRubyPlugin()` |

## Template for platform test guard

```kotlin
private fun requireRubyPlugin() {
    Assume.assumeTrue(
        "Ruby plugin not available — set platformPlugins in gradle.properties",
        PluginDetectors.ruby.isAvailable
    )
}
```

Use `Assume.assumeTrue` (not `fail(...)`) so the test is silently skipped on CI machines without the Ruby plugin, rather than marking the build as failed.

## Files Created

| File | Type | Status |
|------|------|--------|
| `src/test/kotlin/.../ruby/RubyCallHierarchyHandlerUnitTest.kt` | Unit test (expanded) | 744 tests pass |
| `src/test/kotlin/.../ruby/RubyFindSuperMethodsHandlerUnitTest.kt` | Unit test (new) | 744 tests pass |
| `src/test/kotlin/.../ruby/RubyCallHierarchyHandlerPlatformTest.kt` | Platform test (new) | 16 tests, all skip w/o Ruby plugin |
| `src/test/kotlin/.../ruby/RubyFindSuperMethodsHandlerPlatformTest.kt` | Platform test (new) | 14 tests, all skip w/o Ruby plugin |