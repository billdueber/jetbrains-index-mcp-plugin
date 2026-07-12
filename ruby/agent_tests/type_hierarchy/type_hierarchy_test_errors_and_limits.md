# Type Hierarchy Tests — Error Handling & Limits

Tests 18, 19, 26, 27 from the original `type_hierarchy.md`. Covers non-Ruby
file rejection, 101-subtype limit enforcement, missing file errors, and
invalid position errors.

---

### 18. testHandlerRejectsNonRubyElement

**Purpose**: Verify that calling `ide_type_hierarchy` on a non-Ruby file
returns an error, not a Ruby hierarchy.

**Setup**: Use any non-Ruby file in the project, such as one of the Kotlin
source files. For example, `src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/handlers/LanguageHandler.kt`.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_type_hierarchy` with `file = "src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/handlers/LanguageHandler.kt"`, `line = 1`, `column = 1` | Must return an error or a result whose `element.language` is NOT `"Ruby"` |

**Note**: The tool will find the Kotlin class at that position and delegate to
the Kotlin/Java type hierarchy handler (which exists). So the response may
succeed but the `element.language` will be `"Kotlin"` or `"JAVA"` — not
`"Ruby"`. This is acceptable — the point is that the Ruby handler does NOT
claim a non-Ruby file.

---

### 19. testSubtypeLimitRespected

**Purpose**: Verify that when more than 100 classes extend the same parent,
only 100 subtypes are returned (the max limit).

**Setup**: Create 101 fixture files programmatically. Each file defines a class
that extends `LotsOfSiblings`:

```
class Sibling0001 < LotsOfSiblings; end
class Sibling0002 < LotsOfSiblings; end
...
class Sibling0101 < LotsOfSiblings; end
```

Also create `ruby/agent_tests/fixtures/lots_of_siblings.rb` with content:
`class LotsOfSiblings; end`

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create `lots_of_siblings.rb` if it doesn't exist | File created |
| 2 | Create files `sibling0001.rb` through `sibling0101.rb`, each with content `class SiblingXXXX < LotsOfSiblings; end` (replace XXXX with zero-padded number) | 101 files created |
| 3 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 4 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/lots_of_siblings.rb"`, `line = 1`, `column = 7` | Must succeed |
| 5 | Inspect `response.subtypes` | Must be an array. Its length must be at most 100. |

**Cleanup**: After this test, delete the 101 sibling files and
`lots_of_siblings.rb` (optional).

---

### 26. testMissingFileError

**Purpose**: Verify that calling `ide_type_hierarchy` on a non-existent file
returns an appropriate error rather than an empty/invalid hierarchy.

**Expected to PASS** — the platform MCP tool framework returns an error
for non-existent paths.

**Setup**: No fixtures needed. Use a path that does not exist.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/nonexistent.rb"`, `line = 1`, `column = 1` | Must return an error (MCP error or structured error response), not a valid TypeHierarchyData object |

**Note**: The exact error format depends on the IDE's file resolution behavior.
Acceptable: "File not found", MCP-level error, or a response where `element`
is null/missing.

---

### 27. testInvalidPositionError

**Purpose**: Verify that calling `ide_type_hierarchy` on a position within a
Ruby file that does not correspond to a class/module returns an appropriate
error.

**Expected to PASS** — the handler walks up the PSI tree to find the
containing class/module. If none is found, it returns null/error.

**Setup**: Create `no_class.rb` with content:
```
# This file has no classes or modules
puts "hello"
1 + 1
```

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | Create the fixture file above if missing | File created |
| 2 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 3 | `ide_type_hierarchy` with `file = "ruby/agent_tests/fixtures/no_class.rb"`, `line = 1`, `column = 1` | Must return an error (no class/module found at position), not a valid hierarchy |

---

## Summary of expected behavior

| Scenario | `element.name` | `element.kind` | Supertypes contain | Subtypes contain |
|----------|---------------|----------------|-------------------|-----------------|
| Non-Ruby file | — | — | — | — |
| Subtype limit exceed | `"LotsOfSiblings"` | `"CLASS"` | `[]` | max 100 entries |
| Missing file | error | — | — | — |
| Invalid position | error | — | — | — |