# Ruby Call Hierarchy — Agent Integration Tests

## Overview

These tests verify that the `ide_call_hierarchy` MCP tool works correctly
for
Ruby methods and their call relationships. Each test creates fixture `.rb`
files
(or uses pre-existing ones), calls `ide_call_hierarchy`, and checks the
response.

IMPORTANT RULE: These tests should stay 1-1 with the corresponding platform
tests.
When you change one, you must change the other.

These instructions are written for a very dumb agent. Follow every step
literally. Do not skip, assume, or infer.

---

## Pre-requisites

1. The jbimcp plugin is installed and running in an IntelliJ IDE (IDEA
   Ultimate
   or RubyMine) that has the **Ruby plugin** installed.
2. The IDE has indexed all project files. Call `ide_index_status` and
   confirm
   `isDumbMode: false` before running any tests.
3. The working project is `/Users/dueberb/devel/ai/jbimcp` (the jbimcp
   repo).
   All fixture paths are relative to this root.
4. Fixture files already exist under `ruby/agent_tests/fixtures/`. If any
   file
   is missing, create it by reading the inline content in the **Setup**
   section
   of each test and writing it with the `write` tool.

> **Important**: After creating any fixture file, call `ide_sync_files`
> with
> no arguments BEFORE calling `ide_call_hierarchy`. This ensures the IDE's
> PSI
> cache sees the new file. Without this step, `ide_call_hierarchy` may
> return
> "No method/function found" errors.

---

## How to read a test

Each test has the following structure:

```
## testCamelCaseName

**Purpose**: What this test verifies, in one sentence.

**Setup**: Instructions to create any fixture files needed. If fixtures already
exist in `ruby/agent_tests/call_hierarchy/fixtures/`, you can skip creation but MUST still call
`ide_sync_files` before the test.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Exact tool name and parameters | Exact shape of expected response |
| ... | ... | ... |
```

---

## Fixture files

All fixture files live in `ruby/agent_tests/fixtures/`. Their paths are
relative
to the project root (`/Users/dueberb/devel/ai/jbimcp`), so when calling
`ide_call_hierarchy` with the `file` parameter, use the form
`ruby/agent_tests/call_hierarchy/fixtures/<name>.rb`.

When using `language + symbol` lookup, use `language = "Ruby"` and the
symbol format `ClassName#method_name` (instance methods) or
`ClassName.method_name` (class methods).

---

## Calling MCP tools

When calling an intellij-index tool:

- you must prefix the tool name with "intellij-index"
- you must include an argument "project_path" that points to the root of
  the project

## Response format

The `ide_call_hierarchy` tool returns a JSON object with two keys:

- `element`: A `CallElement` object describing the target method:
  `{"name": "ClassName#method_name", "file": "...", "line": N, "column": N,
  "language": "Ruby"}`
- `calls`: An array of `CallElement` objects, each of which may have a
  nested `children` array for deeper hierarchy levels.

Each `CallElement` has:
- `name` (String): Display name of the method
- `file` (String): Relative file path
- `line` (Int): 1-based line number
- `column` (Int): 1-based column number
- `language` (String?): Language identifier
- `children` (Array<CallElement>?): Nested calls (for depth > 1)

---

## Writing the report

After running a test file, you must create a new file in
ruby/agent_tests/results
named with the name of the test
file and an incrementing number (start at 1), separated by dashes. For
example, the first file would be named
`ruby/agent_tests/call_hierarchy/reports/call_hierarchy-1.md`. if
`ruby/agent_tests/call_hierarchy/reports/call_hierarchy-1.md`
exists, call it
`ruby/agent_tests/call_hierarchy/reports/call_hierarchy-2.md`, etc.

Put the date at the top of the report and a summary of the test results
below, along with any useful
error messages or warning that might help figure out what went wrong for
failures.

## Finishing

Once the report is written, stop. Do not try to fix anything or edit
any other files.
