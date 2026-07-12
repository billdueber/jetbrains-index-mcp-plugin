# Ruby Call Hierarchy Test Results

**Date**: 2026-07-13 (New York time)

## testProjectProductionFilesScope

**Purpose**: Verify that `scope = "project_production_files"` returns production callers and excludes callers from test sources.

### Result: PASS

All assertions passed:
- Response element matched expected: `ScopeTarget#target` at `ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb:2:7`, language `Ruby`.
- `response.calls` was non-empty.
- Caller names contained `ProductionCaller#call_target`.
- Excluded caller `ScopeTargetTest#exercise` was not present.

### Response Details

```json
{
  "element": {
    "name": "ScopeTarget#target",
    "file": "ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb",
    "line": 2,
    "column": 7,
    "language": "Ruby",
    "children": null
  },
  "calls": [
    {
      "name": "ProductionCaller#call_target",
      "file": "ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb",
      "line": 8,
      "column": 7,
      "language": "Ruby",
      "children": null
    }
  ]
}
```

## testProjectTestFilesScope

**Purpose**: Verify that `scope = "project_test_files"` returns test-source callers and excludes production callers.

### Result: PASS

All assertions passed:
- Response element matched expected: `ScopeTarget#target` at `ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb:2:7`, language `Ruby`.
- `response.calls` was non-empty.
- Caller names contained `ScopeTargetTest#exercise`.
- Excluded caller `ProductionCaller#call_target` was not present.

### Response Details

```json
{
  "element": {
    "name": "ScopeTarget#target",
    "file": "ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb",
    "line": 2,
    "column": 7,
    "language": "Ruby",
    "children": null
  },
  "calls": [
    {
      "name": "ScopeTargetTest#exercise",
      "file": "ruby/agent_tests/call_hierarchy/fixtures/spec/scope_project_production_spec.rb",
      "line": 2,
      "column": 7,
      "language": "Ruby",
      "children": null
    }
  ]
}
```
