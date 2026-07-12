# Call Hierarchy Tests — Simple Callers

Tests basic "callers" direction: one method called by multiple other methods
in the same file.

---

### 1. testSimpleCallers

**Purpose**: Verify that querying `target` with `direction = "callers"`
returns `alpha` and `beta` as the methods that call it.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/simple_callers.rb`
with the following content:

```ruby
def alpha
  target
end

def beta
  target
end

def target
  true
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | Response has a `syncedPaths` field (array) and `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/simple_callers.rb"`, `line = 9`, `column = 5`, `direction = "callers"`, `depth = 1` | Response is a JSON object with two keys: `element` and `calls` |
| 3 | Inspect `response.element` | Must be: `{"name": "target", "file": "ruby/agent_tests/call_hierarchy/fixtures/simple_callers.rb", "line": 9, "column": 5, "language": "Ruby"}` (children may be null or absent) |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"alpha"` and `"beta"` must appear. |
| 5 | For each entry in `response.calls`, inspect `children` | `children` must be null or absent (depth=1, so no recursion) |

---

### 2. testCallersWithDepth

**Purpose**: Verify that with `depth = 2`, the call hierarchy shows
transitive callers (callers of callers).

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb`
with the following content:

```ruby
def top
  middle
end

def middle
  target
end

def target
  true
end

def side
  target
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/callers_depth.rb"`, `line = 9`, `column = 5`, `direction = "callers"`, `depth = 2` | Must succeed (returns object, not error) |
| 3 | Inspect `response.element` | `name` = `"target"`, `file` ends with `"callers_depth.rb"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"middle"` and `"side"` must appear. |
| 5 | Find the entry in `response.calls` where `name` = `"middle"` | Its `children` must be a non-empty array. At least one child has `name` = `"top"` |
| 6 | Find the entry where `name` = `"side"` | Its `children` must be null or an empty array (no transitive callers of `side`) |
| 5 | Find the entry in `response.calls` where `name` = `"middle"` | Its `children` must be a non-empty array. At least one child has `name` = `"top"` |
| 6 | Find the entry where `name` = `"side"` | Its `children` must be null or an empty array (no transitive callers of `side`) |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Depth behavior |
|----------|---------------|---------------|----------------|
| Two methods call one target | `"target"` | `"alpha"`, `"beta"` | No children at depth=1 |
| Chain with depth=2 | `"target"` | `"middle"`, `"side"` | `"middle"` has child `"top"`; `"side"` has no children |