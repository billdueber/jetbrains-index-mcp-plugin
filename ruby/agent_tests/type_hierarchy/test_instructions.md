# Ruby Type Hierarchy — Agent Integration Tests

## Overview

These tests verify that the `ide_type_hierarchy` MCP tool works correctly
for
Ruby classes and modules. Each test creates fixture `.rb` files (or uses
pre-existing ones), calls `ide_type_hierarchy`, and checks the response.

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
> no arguments BEFORE calling `ide_type_hierarchy`. This ensures the IDE's
> PSI
> cache sees the new file. Without this step, `ide_type_hierarchy` may
> return
> "No class found" errors.

---

## How to read a test

Each test has the following structure:

```
## testCamelCaseName

**Purpose**: What this test verifies, in one sentence.

**Setup**: Instructions to create any fixture files needed. If fixtures already
exist in `ruby/agent_tests/fixtures/`, you can skip creation but MUST still call
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
`ide_type_hierarchy` with the `file` parameter, use the form
`ruby/agent_tests/fixtures/<name>.rb`.

---

## Calling MCP tools

When calling an intellij-index tool:

- you must prefix the tool name with "intellij-index"
- you must include an argument "project_path" that points to the root of
  the project

## Writing the report

After running a test file, you must create a new file in
ruby/agent_tests/results
named with  the name of the test
file and an incrementing number (start at 1), separated by dashes. For example, the first file would be named `ruby/agent_tests/results/type_hierarchy-1.md`

Put the date at the top of the report and a summary of the test results
below, along with any useful
error messages or warning that might help figure out what went wrong for
failures.

## Finishing

Once the report is written, stop. Do not try to fix anything or edit
any other files.
