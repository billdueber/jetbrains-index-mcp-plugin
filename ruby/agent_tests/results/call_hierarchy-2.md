# Ruby Call Hierarchy Test Results

## testCallersWithDepth

**Date**: 2024-01-01
**Test**: Verify that with `depth = 2`, the call hierarchy shows transitive callers (callers of callers).

### Result: PASS

All assertions passed:
- ✅ Response element matches expected: `target` at line 9
- ✅ `response.calls` contains both `"middle"` and `"side"`
- ✅ `middle` entry has children with `"top"` (transitive caller)
- ✅ `side` entry has null/empty children (no transitive callers)

### Response Details:
```json
{
  "element": {
    "name": "target",
    "file": "ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb",
    "line": 9,
    "column": 5,
    "language": "Ruby",
    "children": null
  },
  "calls": [
    {
      "name": "middle",
      "file": "ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb",
      "line": 5,
      "column": 5,
      "language": "Ruby",
      "children": [
        {
          "name": "top",
          "file": "ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb",
          "line": 1,
          "column": 5,
          "language": "Ruby",
          "children": null
        }
      ]
    },
    {
      "name": "side",
      "file": "ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb",
      "line": 13,
      "column": 5,
      "language": "Ruby",
      "children": null
    }
  ]
}
```