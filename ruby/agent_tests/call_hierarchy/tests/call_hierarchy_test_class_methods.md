# Call Hierarchy Tests — Class Methods and Instance Methods

Tests that `ide_call_hierarchy` correctly handles methods defined on classes
(class methods with `self.` prefix) and instance methods.

---

### 1. testInstanceMethodCallers

**Purpose**: Verify that callers of an instance method within a class are
found correctly.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/instance_methods.rb`
with the following content:

```ruby
class Calculator
  def add(x, y)
    x + y
  end

  def compute
    add(1, 2)
  end

  def calculate
    add(3, 4)
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/instance_methods.rb"`, `line = 3`, `column = 7`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Calculator#add"`, `file` ends with `"instance_methods.rb"`, `language` = `"Ruby"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"Calculator#compute"` and `"Calculator#calculate"` must appear. |

---

### 2. testInstanceMethodCallees

**Purpose**: Verify that callees of an instance method are found correctly.

**Setup**: Same fixture file `instance_methods.rb`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/instance_methods.rb"`, `line = 7`, `column = 7`, `direction = "callees"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Calculator#compute"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. At least one entry with `name` = `"Calculator#add"` |

---

### 3. testClassMethodCallers

**Purpose**: Verify that callers of a class method (defined with `self.`)
are found correctly.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/class_methods.rb`
with the following content:

```ruby
class Processor
  def self.parse(input)
    input.strip
  end

  def self.handle(data)
    parse(data)
  end

  def self.process(items)
    items.map { |i| parse(i) }
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/class_methods.rb"`, `line = 3`, `column = 13`, `direction = "callers"`, `depth = 1` | Must succeed |
| 3 | Inspect `response.element` | `name` must be `"Processor.parse"` (dot notation for class method) or `"Processor#parse"` (if Ruby plugin treats all methods as instance). The `file` must end with `"class_methods.rb"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. Collect all `name` values. Both `"Processor.handle"` (or `"Processor#handle"`) and `"Processor.process"` (or `"Processor#process"`) must appear. |

---

### 4. testMixedInstanceAndClassMethods

**Purpose**: Verify that a class method calling an instance method (or vice
versa) shows correct cross-type call relationships.

**Setup**: Create `ruby/agent_tests/call_hierarchy/fixtures/mixed_call_types.rb`
with the following content:

```ruby
class Worker
  def run
    prepare
  end

  def prepare
    setup
  end

  def setup
    true
  end

  def self.start
    worker = new
    worker.run
  end
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/mixed_call_types.rb"`, `line = 2`, `column = 7`, `direction = "callees"`, `depth = 2` | Must succeed |
| 3 | Inspect `response.element` | `name` = `"Worker#run"` |
| 4 | Inspect `response.calls` | Must be a non-empty array. At least one entry with `name` = `"Worker#prepare"` |
| 5 | Inspect children of `"Worker#prepare"` | Must contain `"Worker#setup"` |

---

## Summary of expected behavior

| Scenario | `element.name` | Calls contain | Notes |
|----------|---------------|---------------|-------|
| Instance method callers | `"Calculator#add"` | `"Calculator#compute"`, `"Calculator#calculate"` | Names use `#` notation |
| Instance method callees | `"Calculator#compute"` | `"Calculator#add"` | Single callee found |
| Class method callers | `"Processor.parse"` or `"Processor#parse"` | `"Processor.handle"`, `"Processor.process"` | Dot vs hash notation may vary by Ruby plugin |
| Mixed type chain | `"Worker#run"` | `"Worker#prepare"` → `"Worker#setup"` | Nested chain at depth=2 |