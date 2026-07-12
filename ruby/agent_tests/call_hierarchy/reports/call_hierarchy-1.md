# Ruby Call Hierarchy — Agent Test Report

**Date**: 2026-07-13
**File**: call_hierarchy-1.md

## Summary

**Overall result: 3 of 25 tests passed, 22 failed**

All position-based call hierarchy lookups fail with "No method/function found at position" — the tool cannot resolve Ruby method PSI elements at the given positions even though `ide_find_definition` successfully locates the same symbol. Symbol-based lookup fails because `RubySymbolReferenceHandler` is a stub. Error-handling tests pass correctly.

---

## Results by test file

### call_hierarchy_test_simple_callers.md — 0/2 passed

| Test | Result | Actual |
|------|--------|--------|
| testSimpleCallers | FAIL | `Error: No method/function found at position` |
| testCallersWithDepth | FAIL | `Error: No method/function found at position` |

**Note**: Position at line 9, column 5 (`def target`) could not be resolved to a method. `ide_find_definition` does locate this position successfully, so the PSI element is found, but `findContainingRMethod()` in the Ruby handler returns null for it.

---

### call_hierarchy_test_simple_callees.md — 0/2 passed

| Test | Result | Actual |
|------|--------|--------|
| testSimpleCallees | FAIL | `Error: No method/function found at position` |
| testCalleesWithDepth | FAIL | `Error: No method/function found at position` |

**Note**: Same issue as above — position at the method definition cannot be resolved to a containing RMethod by the handler.

---

### call_hierarchy_test_cross_file.md — 0/2 passed

| Test | Result | Actual |
|------|--------|--------|
| testCrossFileCallers | FAIL | `Error: No method/function found at position` |
| testCrossFileCallees | FAIL | `Error: No method/function found at position` |

**Note**: Attempted at `utils.rb:1:5` (callers) and `processor.rb:5:5` (callees). Both fail identically — the position is not within a resolvable Ruby method.

---

### call_hierarchy_test_deep_chain.md — 0/3 passed

| Test | Result | Actual |
|------|--------|--------|
| testDeepCallersChain | FAIL | `Error: No method/function found at position` |
| testDeepCalleesChain | FAIL | `Error: No method/function found at position` |
| testDepthLimitAtOne | FAIL | `Error: No method/function found at position` |

**Note**: Depth is irrelevant when the tool cannot locate the method in the first place.

---

### call_hierarchy_test_class_methods.md — 0/4 passed

| Test | Result | Actual |
|------|--------|--------|
| testInstanceMethodCallers | FAIL | `Error: No method/function found at position` |
| testInstanceMethodCallees | FAIL | `Error: No method/function found at position` |
| testClassMethodCallers | FAIL | `Error: No method/function found at position` |
| testMixedInstanceAndClassMethods | FAIL | `Error: No method/function found at position` |

**Note**: Instance methods (`Calculator#add` at `instance_methods.rb:3:7`) and class methods (`Processor.parse` at `class_methods.rb:3:13`) both fail identically.

---

### call_hierarchy_test_edge_cases.md — 0/5 passed

| Test | Result | Actual |
|------|--------|--------|
| testNoCallers | FAIL | `Error: No method/function found at position` |
| testNoCallees | FAIL | `Error: No method/function found at position` |
| testSelfReferencingMethod | FAIL | `Error: No method/function found at position` |
| testPredicateMethod | FAIL | `Error: No method/function found at position` |
| testBangMethod | FAIL | `Error: No method/function found at position` |

**Note**: All fail with the same position resolution error. Cycle detection, predicate/bang suffix handling, and empty-calls behavior could not be observed.

---

### call_hierarchy_test_errors.md — 3/7 passed

| Test | Result | Actual |
|------|--------|--------|
| testInvalidDirection | PASS | `Error: direction must be 'callers' or 'callees'` |
| testMissingDirection | PASS | `Error: Missing required parameter: direction` |
| testNonExistentFile | PASS | `Error: No element found at position ruby/.../nonexistent.rb:1:5` |
| testPositionOutsideMethod | FAIL (partial) | `Error: No method/function found at position` — same error as when the position IS inside a method, so can't distinguish |
| testSymbolBasedLookup | FAIL | `Error: Ruby symbol resolution is not yet implemented. Symbol 'Greeter#hello' passed format validation but cannot be resolved.` |
| testEmptyFile | PASS | `Error: No element found at position` |

**Note**: The error-handling tests that verify non-Ruby-specific failures (missing direction, bad direction, non-existent file) all pass. The symbol-based lookup confirms the stub is present but unimplemented. The "position outside method" test produces the same error as valid positions, making it indistinguishable.

---

## Key observations

1. **`findContainingRMethod()` returns null** for all positions, even though `ide_find_definition` successfully finds the symbol at the same position. This indicates the element returned by `resolveElementFromArguments` for Ruby files is not an RMethod (nor inside one), so `PsiTreeUtil.getParentOfType(element, RMethod.class)` cannot find a parent.

2. **`RubySymbolReferenceHandler` is a stub** — symbol-based lookup is not yet functional. The handler validates the symbol format but always returns "not yet implemented."

3. **Error handling works correctly** for non-Ruby-specific validation: invalid direction, missing direction, non-existent file, and empty file all return appropriate errors.

## Next steps

1. Investigate why `findContainingRMethod()` returns null when `ide_find_definition` can find the symbol. The PSI element at the cursor position may be a reference proxy that doesn't have an RMethod parent in the PSI tree.
2. Implement `RubySymbolReferenceHandler.resolveSymbol()` with real PSI resolution, then retry all tests using `language + symbol` targeting.
3. The `resolveCallHierarchySeed()` method in `CallHierarchyTool.kt` only normalizes JS/TS seeds — Ruby may need similar treatment to locate the containing RMethod from the resolved position element.