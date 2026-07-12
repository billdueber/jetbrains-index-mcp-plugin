# Call Hierarchy Tests — Simple Callees

Tests basic "callees" direction: one method that calls multiple other
methods.

---

### 1. testSimpleCallees

**Purpose**: Verify that querying `caller` with `direction = "callees"`
returns `helper_one` and `helper_two` as the methods it calls.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/simple_callees.rb`
with the following content:

```ruby
def helper_one
  1
end

def helper_two
  2
end

def caller
  helper_one
  helper_two
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | Response has a `syncedPaths` field (array) and `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/simple_callees.rb"`, `line = 9`, `column = 5`, `direction = "callees"`, `depth = 1` | Response is a JSON object with two keys: `element` and `calls` |
| 3 | Inspect `response.element` | Must be: `{"name": "caller", "file": "ruby/agent_tests/call_hierarchy/fixtures/simple_callees.rb", "line": 9, "column": 5, "language": "Ruby"}` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"helper_one"` and `"helper_two"` must appear. |
| 5 | For each entry in `response.calls`, inspect `children` | `children` must be null or absent (depth=1, so no recursion) |

---

### 2. testCalleesWithDepth

**Purpose**: Verify that with `depth = 2`, the callee hierarchy shows
transitive callees (methods called by callees).

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/callees_depth.rb`
with the following content:

```ruby
def deep_helper
  true
end

def helper
  deep_helper
end

def caller
  helper
  standalone
end

def standalone
  true
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/callees_depth.rb"`, `line = 9`, `column = 5`, `direction = "callees"`, `depth = 2` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"caller"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"helper"` and `"standalone"` must appear. |
| 5 | Find the entry in `response.calls` where `name` = `"helper"` | Its `children` must be a non-empty array. At least one child has `name` = `"deep_helper"` |
| 6 | Find the entry where `name` = `"standalone"` | Its `children` must be null or an empty array (no transitive callees) |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Depth behavior |
|----------|---------------|---------------|----------------|
| One method calls two helpers | `"caller"` | `"helper_one"`, `"helper_two"` | No children at depth=1 |
| Chain with depth=2 | `"caller"` | `"helper"`, `"standalone"` | `"helper"` has child `"deep_helper"`; `"standalone"` has no children |