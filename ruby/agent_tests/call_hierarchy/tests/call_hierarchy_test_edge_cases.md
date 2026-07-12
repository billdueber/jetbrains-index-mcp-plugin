# Call Hierarchy Tests — Edge Cases

Tests edge cases: no callers, no callees, self-referencing methods, and
methods with non-standard names (predicate/bang).

---

### 1. testNoCallers

**Purpose**: Verify that a method never called by any other method returns
an empty `calls` array for the "callers" direction.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/no_callers.rb`
with the following content:

```ruby
def orphan
  true
end

def unrelated
  42
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/no_callers.rb"`, `line = 1`, `column = 5`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"orphan"` |
| 4 | Inspect `response.calls` | Must be an empty array `[]` |

---

### 2. testNoCallees

**Purpose**: Verify that a method calling nothing returns an empty `calls`
array for the "callees" direction.

**Setup**: Same fixture file `no_callers.rb`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/no_callers.rb"`, `line = 5`, `column = 5`, `direction = "callees"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"unrelated"` |
| 4 | Inspect `response.calls` | Must be an empty array `[]` |

---

### 3. testSelfReferencingMethod

**Purpose**: Verify that a recursive method does not cause infinite
recursion or crash. The handler's cycle detection should prevent visiting
the same method twice.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/recursive.rb`
with the following content:

```ruby
def factorial(n)
  return 1 if n <= 1
  n * factorial(n - 1)
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/recursive.rb"`, `line = 1`, `column = 5`, `direction = "callees"`, `depth = 3` | Must succeed (no timeout, no crash) |
| 3 | Inspect `response.element` | `name` = `"factorial"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. At least one entry with `name` = `"factorial"` |
| 5 | Inspect the first level of children | The entry for `"factorial"` should NOT have its own `children` containing `"factorial"` again (cycle was broken by the `visited` set) |

---

### 4. testPredicateMethod

**Purpose**: Verify that a method named with a `?` suffix (e.g., `valid?`)
is found and its callers are correctly reported.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/predicate.rb`
with the following content:

```ruby
def valid?
  true
end

def process
  return unless valid?
  :ok
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/predicate.rb"`, `line = 1`, `column = 5`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"valid?"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. At least one entry with `name` = `"process"` |

---

### 5. testBangMethod

**Purpose**: Verify that a method named with a `!` suffix (e.g., `save!`)
is found and its callers are correctly reported.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/bang.rb`
with the following content:

```ruby
def save!
  true
end

def persist
  save!
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/bang.rb"`, `line = 1`, `column = 5`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"save!"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. At least one entry with `name` = `"persist"` |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Edge case handled |
|----------|---------------|---------------|-------------------|
| No callers | `"orphan"` | `[]` | Empty array, not error |
| No callees | `"unrelated"` | `[]` | Empty array, not error |
| Self-referencing | `"factorial"` | `"factorial"` | Cycle broken (no infinite recursion) |
| Predicate method | `"valid?"` | `"process"` | `?` suffix in name preserved |
| Bang method | `"save!"` | `"persist"` | `!` suffix in name preserved |