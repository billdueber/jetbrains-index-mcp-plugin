# Call Hierarchy Tests — Deep Chain and Depth Limits

Tests that the `depth` parameter correctly limits recursive traversal in
both directions.

---

### 1. testDeepCallersChain

**Purpose**: Verify a 4-level caller chain `A → B → C → D` (where A calls
B, B calls C, C calls D). With `depth = 3`, the hierarchy should show
3 levels of callers.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/deep_callers.rb`
with the following content:

```ruby
def a
  b
end

def b
  c
end

def c
  d
end

def d
  true
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/deep_callers.rb"`, `line = 13`, `column = 5`, `direction = "callers"`, `depth = 3` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"d"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Exactly one entry with `name` = `"c"` |
| 5 | Inspect `response.calls[0].children` | Must be a non-empty array. Exactly one entry with `name` = `"b"` |
| 6 | Inspect `response.calls[0].children[0].children` | Must be a non-empty array. Exactly one entry with `name` = `"a"` |
| 7 | Inspect `response.calls[0].children[0].children[0].children` | Must be null or an empty array (depth=3 means no deeper level) |

---

### 2. testDeepCalleesChain

**Purpose**: Verify a 4-level callee chain `A → B → C → D` (A calls B, B
calls C, C calls D). With `depth = 3`, the hierarchy should show 3 levels
of callees.

**Setup**: Same fixture file `deep_callers.rb` as above.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/deep_callers.rb"`, `line = 1`, `column = 5`, `direction = "callees"`, `depth = 3` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"a"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Exactly one entry with `name` = `"b"` |
| 5 | Inspect `response.calls[0].children` | Must be a non-empty array. Exactly one entry with `name` = `"c"` |
| 6 | Inspect `response.calls[0].children[0].children` | Must be a non-empty array. Exactly one entry with `name` = `"d"` |

---

### 3. testDepthLimitAtOne

**Purpose**: Verify that `depth = 1` returns only immediate callers/callees
with no children.

**Setup**: Same fixture file `deep_callers.rb`.

**Test A — callers with depth=1**:

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/deep_callers.rb"`, `line = 13`, `column = 5`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"d"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Exactly one entry with `name` = `"c"` |
| 5 | Inspect `response.calls[0].children` | Must be null or an empty array (depth=1, no recursion) |

**Test B — callees with depth=1**:

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/deep_callers.rb"`, `line = 1`, `column = 5`, `direction = "callees"`, `depth = 1` | Must succeed |
| 2 | Inspect `response.element` | `name` = `"a"` |
| 3 | Inspect `response.calls` | Must be a non-empty array. Exactly one entry with `name` = `"b"` |
| 4 | Inspect `response.calls[0].children` | Must be null or an empty array |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Depth behavior |
|----------|---------------|---------------|----------------|
| Callers chain depth=3 | `"d"` | `"c"` → `"b"` → `"a"` | 3 levels deep, no level 4 |
| Callees chain depth=3 | `"a"` | `"b"` → `"c"` → `"d"` | 3 levels deep |
| Callers depth=1 | `"d"` | `"c"` (no children) | Immediate only |
| Callees depth=1 | `"a"` | `"b"` (no children) | Immediate only |