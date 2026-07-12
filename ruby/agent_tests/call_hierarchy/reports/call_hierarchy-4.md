# Ruby Call Hierarchy Agent Integration Report

Date: 2026-07-13 (New York time, GMT -5)

Report file: `ruby/agent_tests/call_hierarchy/reports/call_hierarchy-4.md`

## Test Run Summary

- Project: `/Users/dueberb/devel/ai/jbimcp`
- Tool: `ide_call_hierarchy` via intellij-index MCP
- Index status checked before tests: `isDumbMode: false`, `isIndexing: false`
- Sync status: `syncedAll: true`
- Total test cases run: 24
- Passed: 23
- Failed: 1
- Warnings: 1

## Results

| Test | Result | Notes |
|---|---:|---|
| `testInstanceMethodCallers` | PASS | `Calculator#add` callers included `Calculator#compute` and `Calculator#calculate`. |
| `testInstanceMethodCallees` | PASS | `Calculator#compute` callee included `Calculator#add`. |
| `testClassMethodCallers` | PASS | `Processor#parse` callers included `Processor#handle` and `Processor#process`; Ruby plugin reported class methods with `#` notation, which the test allows. |
| `testMixedInstanceAndClassMethods` | FAIL | Expected `element.name` = `Worker#run`, but query at `line=7,column=7` returned `Worker#prepare`. The fixture text places `setup` on line 7, so the expected target appears to require the `def run` line instead. |
| `testSimpleCallees` | PASS | `caller` callees included `helper_one` and `helper_two`; no children at depth 1. |
| `testCalleesWithDepth` | PASS | `caller` callees included `helper` and `standalone`; `helper` child included `deep_helper`; `standalone` had no children. |
| `testSimpleCallers` | PASS | `target` callers included `alpha` and `beta`; no children at depth 1. |
| `testCallersWithDepth` | PASS | `target` callers included `middle` and `side`; `middle` child included `top`; `side` had no children. |
| `testCrossFileCallers` | PASS | `format_name` callers included `greet` and `announce`, both in `main.rb`. |
| `testCrossFileCallees` | PASS | `process` callees included `double` and `triple`, both in `operations.rb`. |
| `testDeepCallersChain` | PASS | Caller chain returned `d` -> `c` -> `b` -> `a`, with no fourth caller level at depth 3. |
| `testDeepCalleesChain` | PASS | Callee chain returned `a` -> `b` -> `c` -> `d`. |
| `testDepthLimitAtOne` | PASS | Caller and callee depth 1 queries returned immediate results with no children. |
| `testNoCallers` | PASS | `orphan` callers returned `[]`. |
| `testNoCallees` | PASS | `unrelated` callees returned `[]`. |
| `testSelfReferencingMethod` | PASS | Recursive `factorial` returned itself without repeating itself as a child. |
| `testPredicateMethod` | PASS | `valid?` caller included `process`. |
| `testBangMethod` | PASS | `save!` caller included `persist`. |
| `testInvalidDirection` | PASS | Returned error: `direction must be 'callers' or 'callees'`. |
| `testMissingDirection` | PASS | Returned error: `Missing required parameter: direction`. |
| `testNonExistentFile` | PASS | Returned error: `No element found at position ruby/agent_tests/call_hierarchy/fixtures/nonexistent.rb:1:5`. |
| `testPositionOutsideMethod` | PASS | Returned error containing `No method/function found at position`. |
| `testSymbolBasedLookup` | PASS | Expected stub failure returned: `Ruby symbol resolution is not yet implemented. Symbol 'Greeter#hello' passed format validation but cannot be resolved.` |
| `testEmptyFile` | PASS with warning | Returned error: `No element found at position ruby/agent_tests/call_hierarchy/fixtures/empty.rb:1:1`; wording differs from the instruction's preferred `No method` wording. |

## Notable Warnings

1. `testMixedInstanceAndClassMethods` has a coordinate mismatch against the fixture text:
   - Instruction query: `line=7,column=7`
   - Expected element: `Worker#run`
   - Actual element: `Worker#prepare`
   - Fixture line 7 is inside `def setup`, while `def run` is on line 2.
2. `testEmptyFile` returned an error object, but the message says `No element found` rather than `No method`; this may be acceptable, but it is a wording mismatch against the instruction text.

## Errors Observed During Expected-Error Tests

- Invalid direction: `direction must be 'callers' or 'callees'`
- Missing direction: `Missing required parameter: direction`
- Non-existent file: `No element found at position ruby/agent_tests/call_hierarchy/fixtures/nonexistent.rb:1:5`
- Position outside method: `No method/function found at position`
- Symbol lookup: `Ruby symbol resolution is not yet implemented. Symbol 'Greeter#hello' passed format validation but cannot be resolved.`
- Empty file: `No element found at position ruby/agent_tests/call_hierarchy/fixtures/empty.rb:1:1`

## Conclusion

The Ruby call hierarchy agent integration tests are mostly green. One test fails because the expected target does not match the query position in the fixture, and one test has a warning about error-message wording. No code was edited.
