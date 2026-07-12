# Ruby File Structure — Agent Integration Tests

## Overview

These tests verify that the `ide_file_structure` MCP tool correctly extracts the hierarchical structure of Ruby source files (classes, modules, methods, and their nesting). Each test creates or uses a fixture `.rb` file, calls `ide_file_structure`, and checks the response.

IMPORTANT: These tests should stay 1-1 with the corresponding platform tests in `RubyStructureHandlerPlatformTest`. When you change one, you must change the other.

## Prerequisites

1. The jbimcp plugin is installed and running in an IntelliJ IDE (IDEA Ultimate or RubyMine) that has the **Ruby plugin** installed.
2. The IDE has indexed all project files. Call `ide_index_status` and confirm `isDumbMode: false` before running any tests.
3. The working project is `/Users/dueberb/devel/ai/jbimcp` (the jbimcp repo). All fixture paths are relative to this root.
4. Fixture files already exist under `ruby/agent_tests/file_structure/fixtures/`. If any file is missing, create it by reading the inline content in the **Setup** section of each test and writing it with the `write` tool.

> **Important**: After creating any fixture file, call `ide_sync_files` with no arguments BEFORE calling `ide_file_structure`. This ensures the IDE's PSI cache sees the new file. Without this step, `ide_file_structure` may return "File not found" errors.

## How to read a test

Each test has the following structure:

```
## testCamelCaseName

**Purpose**: What this test verifies, in one sentence.

**Setup**: Instructions to create any fixture files needed. If fixtures already exist in `ruby/agent_tests/file_structure/fixtures/`, you can skip creation but MUST still call `ide_sync_files` before the test.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Exact tool name and parameters | Exact shape of expected response |
| ... | ... | ... |
```

## Fixture files

All fixture files live in `ruby/agent_tests/file_structure/fixtures/`. Their paths are relative to the project root (`/Users/dueberb/devel/ai/jbimcp`), so when calling `ide_file_structure` with the `file` parameter, use the form `ruby/agent_tests/file_structure/fixtures/<name>.rb`.

## Calling MCP tools

When calling an intellij-index tool:
- you must prefix the tool name with "intellij-index"
- you must include an argument `project_path` that points to the root of the project

## Response format

The `ide_file_structure` tool returns a JSON object with three keys:

- `file`: The file path (relative to project root)
- `language`: The detected language (e.g., `"ruby"`)
- `structure`: A formatted tree string showing the hierarchical structure

For non-empty files, the structure string follows this format:
```
<filename>.rb

class ClassName (line N)
  method method_name (params) (line N)
  method method_name (line N)
module ModuleName (line N)
  class NestedClass (line N)
    method method_name (line N)
```

For empty files, the response is:
```
File is empty or has no parseable structure.

File: <filename>.rb
Language: ruby
```

## Writing the report

After running a test file, create a new file in `ruby/agent_tests/file_structure/reports/` named with the test name and an incrementing number. For example, the first file would be named `ruby/agent_tests/file_structure/reports/fs_01_simple_class-1.md`. If that file exists, increment the number.

Put the date at the top of the report and a summary of each test result below, along with any useful error messages or warnings.

## Finishing

Once the report is written, stop. Do not try to fix anything or edit any other files.