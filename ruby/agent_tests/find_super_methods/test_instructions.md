# Ruby Find Super Methods — Agent Integration Tests

## Overview

These tests verify that the `ide_find_super_methods` MCP tool correctly identifies the hierarchy of Ruby methods that a method overrides or implements. Each test creates fixture `.rb` files (or uses pre-existing ones), calls `ide_find_super_methods`, and checks the response.

IMPORTANT: These tests should stay 1-1 with the corresponding platform tests. When you change one, you must change the other.

## Prerequisites

1. The jbimcp plugin is installed and running in an IntelliJ IDE (IDEA Ultimate or RubyMine) that has the **Ruby plugin** installed.
2. The IDE has indexed all project files. Call `ide_index_status` and confirm `isDumbMode: false` before running any tests.
3. The working project is `/Users/dueberb/devel/ai/jbimcp` (the jbimcp repo). All fixture paths are relative to this root.
4. Fixture files already exist under `ruby/agent_tests/find_super_methods/fixtures/`. If any file is missing, create it by reading the inline content in the **Setup** section of each test and writing it with the `write` tool.

> **Important**: After creating any fixture file, call `ide_sync_files` with no arguments BEFORE calling `ide_find_super_methods`. This ensures the IDE's PSI cache sees the new file. Without this step, `ide_find_super_methods` may return "No method/function found" errors.

## How to read a test

Each test has the following structure:

```
## testCamelCaseName

**Purpose**: What this test verifies, in one sentence.

**Setup**: Instructions to create any fixture files needed. If fixtures already exist in `ruby/agent_tests/find_super_methods/fixtures/`, you can skip creation but MUST still call `ide_sync_files` before the test.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Exact tool name and parameters | Exact shape of expected response |
| ... | ... | ... |
```

## Fixture files

All fixture files live in `ruby/agent_tests/find_super_methods/fixtures/`. Their paths are relative to the project root (`/Users/dueberb/devel/ai/jbimcp`), so when calling `ide_find_super_methods` with the `file` parameter, use the form `ruby/agent_tests/find_super_methods/fixtures/<name>.rb`.

When using `language + symbol` lookup, use `language = "Ruby"` and the symbol format `ClassName#method_name` (instance methods) or `ClassName.method_name` (class methods).

## Calling MCP tools

When calling an intellij-index tool:
- you must prefix the tool name with "intellij-index"
- you must include an argument `project_path` that points to the root of the project

## Response format

The `ide_find_super_methods` tool returns a JSON object with three keys:

- `method`: A `MethodElement` object describing the target method:
  ```json
  {
    "name": "ClassName#method_name",
    "file": "ruby/agent_tests/find_super_methods/fixtures/example.rb",
    "line": N,
    "column": N,
    "language": "ruby"
  }
  ```
- `hierarchy`: An array of `MethodElement` objects, each with an additional `depth` field (1 = immediate parent, 2 = grandparent, etc.). Ordered from immediate parent to root.
  ```json
  [
    {
      "name": "ParentClass#method_name",
      "file": "ruby/agent_tests/find_super_methods/fixtures/example.rb",
      "line": N,
      "column": N,
      "language": "ruby",
      "depth": 1
    }
  ]
  ```
- `totalCount`: Integer count of parent methods in the hierarchy.

## Writing the report

After running a test file, create a new file in `ruby/agent_tests/find_super_methods/reports/` named with the test name and an incrementing number. For example, the first file would be named `ruby/agent_tests/find_super_methods/reports/fsm_01_basic_override-1.md`. If that file exists, increment the number.

Put the date at the top of the report and a summary of each test result below, along with any useful error messages or warnings.

## Finishing

Once the report is written, stop. Do not try to fix anything or edit any other files.