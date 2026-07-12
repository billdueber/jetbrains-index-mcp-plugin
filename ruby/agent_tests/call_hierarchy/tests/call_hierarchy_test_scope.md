# Call Hierarchy Tests — Scope and Filtering Coverage

This document converts the Java/Python `scope and filtering coverage` checklist into Ruby call hierarchy test procedures. It describes what to verify, not how the handler implements it.

## Scope and filtering coverage map

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| `project_production_files` scope | Covered for Java callers. | Not covered. | Not separately covered by Ruby tests. |
| `project_and_libraries` scope | Covered for Java callers. | Not covered. | Not separately covered by Ruby tests. |
| `project_files` scope with libraries excluded | Not covered by Java call hierarchy tests. | Not covered. | Not separately covered by Ruby tests. |
| Test-source exclusion | Covered indirectly by Java production-scope test. | Not covered. | Not separately covered by Ruby tests. |
| Language-specific scope filtering | Covered for Java in shared/navigation tests. | Not covered by Python call hierarchy tests. | Not separately covered by Ruby tests. |

---

## 1. testProjectProductionFilesScope

**Purpose**: Verify that `scope = "project_production_files"` returns production callers and excludes callers from test sources.

**Setup**: Create two fixture files

`ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb`:
```ruby
class ScopeTarget
  def target
    true
  end
end

class ProductionCaller
  def call_target
    ScopeTarget.new.target
  end
end
```

`ruby/agent_tests/call_hierarchy/fixtures/spec/scope_project_production_spec.rb`:
```ruby
class ScopeTargetTest
  def exercise
    ScopeTarget.new.target
  end
end
```

| Step | Action | Expected response                                                               |
|------|--------|---------------------------------------------------------------------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true`                                                               |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb"`, `line = 2`, `column = 7`, `direction = "callers"`, `depth = 1`, `scope = "project_production_files"` | Response must succeed                                                           |
| 3 | Inspect `response.element` | `name` = `"ScopeTarget#target"`, `file` ends with `"scope_project_production.rb"`, `language` = `"Ruby"` |
| 4 | Inspect `response.calls` | Must be array with one item.                                                    |
| 5 | Collect caller `name` values | Must contain `"ProductionCaller#call_target"`                                   |
| 6 | Confirm excluded callers | Must not contain `"ScopeTargetTest#exercise"`                                   |

---

## 2. testProjectTestFilesScope

**Purpose**: Verify that `scope = "project_test_files"` returns test-source callers and excludes production callers.

**Setup**: Use the same fixture files from `testProjectProductionFilesScope`.

| Step | Action | Expected response                                                                                        |
|------|--------|----------------------------------------------------------------------------------------------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true`                                                                                        |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/call_hierarchy/fixtures/scope_project_production.rb"`, `line = 2`, `column = 7`, `direction = "callers"`, `depth = 1`, `scope = "project_test_files"` | Response must succeed                                                                                    |
| 3 | Inspect `response.element` | `name` = `"ScopeTarget#target"`, `file` ends with `"scope_project_production.rb"`, `language` = `"Ruby"` |
| 4 | Inspect `response.calls` | Must be array with one item                                                                              |
| 5 | Collect caller `name` values | Must contain `"ScopeTargetTest#exercise"`                                                                |
| 6 | Confirm excluded callers | Must not contain `"ProductionCaller#call_target"`                                                        |

---

## 3. testProjectFilesScopeExcludesLibraries

**Purpose**: Verify that the default `project_files` scope excludes callers located in external Ruby library files.

**Setup**: Create an external library fixture outside the jbimcp project, for example `/tmp/jbimcp_scope_library/lib/scope_library.rb`:

```ruby
module LibraryScope
  class Helper
    def call_target
      helper_inner
    end

    def helper_inner
      true
    end
  end
end
```

Create a project fixture in `ruby/agent_tests/call_hierarchy/fixtures/`:

`scope_project_files_app.rb`:
```ruby
require '/tmp/jbimcp_scope_library/lib/scope_library'

def call_library_target
  LibraryScope::Helper.new.call_target
end
```

In the IDE, add `/tmp/jbimcp_scope_library` or `/tmp/jbimcp_scope_library/lib` as a Ruby library/module library for the jbimcp project, refresh the VFS, and index the library.

| Step | Action                                                                                                                                                                                                                            | Expected response |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| 1 | `ide_sync_files` with no arguments                                                                                                                                                                                                | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "/tmp/jbimcp_scope_library/lib/scope_library.rb"`, `line = 3`, `column = 9`, `direction = "callers"`, `depth = 1`, `scope = "project_files"` | Response must succeed |
| 3 | Inspect `response.element`                                                                                                                                                                                                        | `name` = `"Helper#call_target"` or the Ruby plugin's equivalent qualified method name, `language` = `"Ruby"` |
| 4 | Inspect `response.calls`                                                                                                                                                                                                          | Must be a non-empty array |
| 5 | Collect caller `name` values                                                                                                                                                                                                      | Must contain `call_library_target` |
| 6 | Confirm excluded callers                                                                                                                                                                                                          | Must not contain `Helper#helper_inner` or any caller whose file is under `/tmp/jbimcp_scope_library` |

---

## 4. testProjectAndLibrariesScopeIncludesLibraries

**Purpose**: Verify that `scope = "project_and_libraries"` includes callers located in external Ruby library files.

**Setup**: Use the same external library and project fixtures from `testProjectFilesScopeExcludesLibraries`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "/tmp/jbimcp_scope_library/lib/scope_library.rb"`, `line = 3`, `column = 7`, `direction = "callers"`, `depth = 1`, `scope = "project_and_libraries"` | Response must succeed |
| 3 | Inspect `response.element` | `name` = `"Helper#call_target"` or the Ruby plugin's equivalent qualified method name, `language` = `"Ruby"` |
| 4 | Inspect `response.calls` | Must be a non-empty array |
| 5 | Collect caller `name` values | Must contain `call_library_target` |
| 6 | Confirm library callers are visible | Must contain `Helper#helper_inner` or the Ruby plugin's equivalent qualified method name |

---

## 5. testLanguageSpecificScopeFilteringForRubySymbolLookup

**Purpose**: Document the Ruby language-filtering coverage gap for symbol-based call hierarchy lookup. Ruby symbol lookup is currently a stub, so this test should be updated when Ruby symbol resolution is implemented.

**Setup**: Create `scope_language_filter.rb`:

```ruby
class ScopeLanguageTarget
  def target
    true
  end
end

def call_target
  ScopeLanguageTarget.new.target
end
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_call_hierarchy` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `language = "Ruby"`, `symbol = "ScopeLanguageTarget#target"`, `direction = "callers"`, `depth = 1`, `scope = "project_files"` | Current behavior: symbol resolution may fail because Ruby symbol lookup is a stub. Future behavior: response must succeed |
| 3 | Inspect `response.element` in future behavior | `name` = `"ScopeLanguageTarget#target"`, `file` ends with `"scope_language_filter.rb"`, `language` = `"Ruby"` |
| 4 | Inspect `response.calls` in future behavior | Must contain `"call_target"` |
| 5 | Inspect all returned elements in future behavior | Every returned element must have `language` = `"Ruby"` |

---

## Summary of expected behavior

| Scenario | Scope parameter | Calls should include | Calls must not include |
|----------|-----------------|----------------------|------------------------|
| Production callers only | `"project_production_files"` | `"ProductionCaller#call_target"` | `"ScopeTargetTest#exercise"` |
| Test callers only | `"project_test_files"` | `"ScopeTargetTest#exercise"` | `"ProductionCaller#call_target"` |
| Project files only | `"project_files"` / default | `call_library_target` | callers under `/tmp/jbimcp_scope_library` |
| Project plus libraries | `"project_and_libraries"` | `call_library_target` | none for the library fixture above |
| Ruby symbol language filter | `"project_files"` | `"call_target"` | any non-Ruby language element |
