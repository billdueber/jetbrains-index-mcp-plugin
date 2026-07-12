# Call Hierarchy Test Report — Class Methods and Instance Methods

Date: 2026-07-13

## Summary

Ran `ruby/agent_tests/call_hierarchy/call_hierarchy_test_class_methods.md` against `intellij-index_ide_call_hierarchy` with `project_path=/Users/dueberb/devel/ai/jbimcp`. The Ruby plugin resolved class methods using `#` notation rather than `.` notation.

## Results

### testInstanceMethodCallers — PASS

- Fixture: `ruby/agent_tests/call_hierarchy/fixtures/instance_methods.rb`
- Lookup: line 3, column 7, direction `callers`, depth 1
- `response.element.name`: `Calculator#add`
- `response.calls`:
  - `Calculator#compute`
  - `Calculator#calculate`

### testInstanceMethodCallees — PASS

- Fixture: `ruby/agent_tests/call_hierarchy/fixtures/instance_methods.rb`
- Lookup: line 7, column 7, direction `callees`, depth 1
- `response.element.name`: `Calculator#compute`
- `response.calls`:
  - `Calculator#add`

### testClassMethodCallers — PASS

- Fixture: `ruby/agent_tests/call_hierarchy/fixtures/class_methods.rb`
- Lookup: line 3, column 13, direction `callers`, depth 1
- `response.element.name`: `Processor#parse`
- `response.calls`:
  - `Processor#handle`
  - `Processor#process`

### testMixedInstanceAndClassMethods — FAIL

- Fixture: `ruby/agent_tests/call_hierarchy/fixtures/mixed_call_types.rb`
- Lookup: line 7, column 7, direction `callees`, depth 2
- Expected `response.element.name`: `Worker#run`
- Actual `response.element.name`: `Worker#prepare`
- Actual `response.calls`:
  - `Worker#setup`

The lookup at line 7, column 7 resolves to `Worker#prepare`, not `Worker#run`; the nested chain `Worker#prepare -> Worker#setup` was found, but the expected starting element did not match.
