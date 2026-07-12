# Call Hierarchy Tests — Errors and Symbol Lookup

Tests error handling: invalid parameters, missing files, and the
language + symbol lookup mode (which is currently a stub for Ruby).

---

### 1. testInvalidDirection

**Purpose**: Verify that passing an invalid `direction` value returns an
error message.

**Setup**: No fixture files needed.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/simple_callers.rb"`, `line = 1`, `column = 5`, `direction = "sideways"`, `depth = 1` | Response must be an error object. The error message must contain `"direction"` and indicate that only `"callers"` or `"callees"` are valid. |

---

### 2. testMissingDirection

**Purpose**: Verify that omitting the required `direction` parameter returns
an error.

**Setup**: No fixture files needed.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/simple_callers.rb"`, `line = 1`, `column = 5`, `depth = 1` (no `direction` parameter) | Response must be an error object. The error message must indicate that `direction` is missing or required. |

---

### 3. testNonExistentFile

**Purpose**: Verify that referencing a non-existent file returns an error.

**Setup**: No fixture files needed.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/nonexistent.rb"`, `line = 1`, `column = 5`, `direction = "callers"`, `depth = 1` | Response must be an error object. The error message must indicate that the file was not found or could not be resolved. |

---

### 4. testPositionOutsideMethod

**Purpose**: Verify that positioning the cursor at a location that is not
inside any method returns an error (no "No method/function found" message).

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/outside_method.rb`
with the following content:

```ruby
class Calculator
  def add(x, y)
    x + y
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/outside_method.rb"`, `line = 1`, `column = 1`, `direction = "callers"`, `depth = 1` | Response must be an error object. The error message must contain `"No method/function found at position"` (the specific error text). |

---

### 5. testSymbolBasedLookup

**Purpose**: Verify that using `language = "Ruby"` + `symbol` targeting
returns the expected behavior. (Note: Ruby symbol resolution is currently
a stub — this test documents the expected failure.)

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/symbol_target.rb`
with the following content:

```ruby
class Greeter
  def hello(name)
    "Hello, #{name}"
  end

  def greet
    hello("world")
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "Greeter#hello"`, `direction = "callers"`, `depth = 1` | This test documents the current behavior. **Expected**: The call may succeed or fail depending on whether Ruby symbol resolution is implemented. If it fails, the error message should indicate that symbol resolution is not yet implemented. If it succeeds, `response.element.name` must be `"Greeter#hello"` and `response.calls` must contain at least one entry with `name` = `"Greeter#greet"`. |

---

### 6. testEmptyFile

**Purpose**: Verify that an empty file returns an error when queried.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/empty.rb`
with content: (empty file, zero bytes).

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/empty.rb"`, `line = 1`, `column = 1`, `direction = "callers"`, `depth = 1` | Response must be an error object. The error message should indicate that no element was found at the position. |

---

## Summary of expected behavior

| Scenario | Expected outcome | Error message hints |
|----------|-----------------|---------------------|
| Invalid direction | Error | `"direction"`, `"callers"`, `"callees"` |
| Missing direction | Error | `"direction"`, `"missing"`, `"required"` |
| Non-existent file | Error | `"file"`, `"not found"`, `"could not resolve"` |
| Position outside method | Error | `"No method/function found"` |
| Symbol-based lookup | May fail or succeed | `"not yet implemented"` (if stub) |
| Empty file | Error | `"No element found"` |