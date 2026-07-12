# Call Hierarchy Tests — Cross-File Callers and Callees

Tests that `ide_call_hierarchy` correctly resolves call relationships across
multiple Ruby files.

---

### 1. testCrossFileCallers

**Purpose**: Verify that a method defined in one file (`utils.rb`) is found
as a caller target when called from another file (`main.rb`).

**Setup**: Create two files in `ruby/agent_tests/call_hierarchy/fixtures/`:

`utils.rb`:
```ruby
def format_name(first, last)
  "#{first} #{last}"
end
```

`main.rb`:
```ruby
require_relative 'utils'

def greet(first, last)
  name = format_name(first, last)
  "Hello, #{name}!"
end

def announce(first, last)
  name = format_name(first, last)
  "Announcing #{name}!"
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create both files if they don't exist | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/utils.rb"`, `line = 1`, `column = 5`, `direction = "callers"`, `depth = 1` | Must succeed |
| 4 | Inspect `response.element` | `name` = `"format_name"`, `file` ends with `"utils.rb"` |
| 5 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"greet"` and `"announce"` must appear. |
| 6 | For each caller, inspect `file` | Each entry's `file` must end with `"main.rb"` |

---

### 2. testCrossFileCallees

**Purpose**: Verify that a method calling helpers defined in another file
reports those helpers as callees.

**Setup**: Create two files in `ruby/agent_tests/call_hierarchy/fixtures/`:

`operations.rb`:
```ruby
def double(x)
  x * 2
end

def triple(x)
  x * 3
end
```

`processor.rb`:
```ruby
require_relative 'operations'

def process(value)
  double(value)
  triple(value)
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create both files if they don't exist | Files created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/processor.rb"`, `line = 5`, `column = 5`, `direction = "callees"`, `depth = 1` | Must succeed |
| 4 | Inspect `response.element` | `name` = `"process"`, `file` ends with `"processor.rb"` |
| 5 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"double"` and `"triple"` must appear. |
| 6 | For each callee, inspect `file` | Each entry's `file` must end with `"operations.rb"` |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Cross-file resolution |
|----------|---------------|---------------|----------------------|
| Cross-file callers | `"format_name"` | `"greet"`, `"announce"` | Callers in `main.rb`, element in `utils.rb` |
| Cross-file callees | `"process"` | `"double"`, `"triple"` | Callees in `operations.rb`, element in `processor.rb` |