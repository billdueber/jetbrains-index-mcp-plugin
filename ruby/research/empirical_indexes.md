# Ruby Plugin Stub Indexes - Empirical Testing Results

## Test Summary

| Test | Description | Result |
|------|-------------|--------|
| Test 1 | `class Dog < Animal` - Simple inheritance | **PASSED** |
| Test 2 | `class User; include Authenticatable` - Module include | **FAILED** |

## Test 1: Simple Inheritance

**Setup:**
- `animal.rb`: `class Animal; end`
- `dog.rb`: `class Dog < Animal; end`

**Result:**
```json
{
  "element": {"name": "Dog", "file": "ruby/agent_tests/fixtures/dog.rb", "kind": "CLASS", "language": "Ruby"},
  "supertypes": [{"name": "Animal", "kind": "CLASS"}],
  "subtypes": []
}
```

**Analysis:** Superclass resolution via `getSuperClassFQN()` + FQN index lookup works correctly.

## Test 2: Module Include

**Setup:**
- `authenticatable.rb`: `module Authenticatable; end`
- `user.rb`: `class User\n  include Authenticatable\nend`

**Result:**
```json
{
  "element": {"name": "User", "file": "ruby/agent_tests/fixtures/user.rb", "kind": "CLASS", "language": "Ruby"},
  "supertypes": [],
  "subtypes": []
}
```

**Expected:** `supertypes` should contain `Authenticatable` (kind="MODULE")

**Analysis:** Module include resolution is broken. The `getIncludedModuleFQNs()` function returns empty list.

## Root Cause Analysis

### Available RClass Methods (from research documents)

| Method | Returns | Status |
|--------|---------|--------|
| `getName()` | `String?` | EXISTS |
| `getSuperClassFQN()` | `FQN` | EXISTS |
| `getPsiSuperClass()` | `RSuperClass` | EXISTS |
| `getClassName()` | `RName` | EXISTS |
| `processCallsOfType()` | `void` | EXISTS |
| `getIncludedModules()` | `List<PsiElement>` | **UNCLEAR** |

### Key Finding

The `getIncludedModules()` method is documented in `03-ruby-psi-reference.md` but NOT in the main `RubyTypeHierarchyHandler.md` research document. This suggests:

1. It may be on `RClassBase` (implementation) not `RClass` (interface)
2. It may not exist in this Ruby plugin version (2025.3)
3. It may have a different name or signature

### Available Indexes

| Index | Purpose | Works for Include? |
|-------|---------|-------------------|
| RubyInheritanceIndex | FQN → element | No (superclass lookup) |
| RubyClassModuleNameIndex | Short name → element | No (name lookup only) |
| RubyIncludedExtendedFQNIndex | Module FQN → classes that include it | **YES** - reverse direction |
| RubyInheritanceResolutionIndex | Own FQN → element | No (own element lookup) |

### The Catch-22

For `include` resolution, we need to:
1. Get the list of included modules from the class
2. Resolve each module's FQN to a PSI element

Option A: Use `getIncludedModules()` directly - **UNVERIFIED**
Option B: Use `processCallsOfType(INCLUDE_CALL)` - **FAILING**
Option C: Use stub indexes - **REQUIRES DIFFERENT APPROACH**

## Recommended Fix

Based on empirical testing, the `processCallsOfType` approach is failing. Two options:

### Option 1: Use RubyIncludedExtendedFQNIndex for subtypes only
Instead of trying to get included modules from the class, use the index to find which classes include a given module. This gives us "subtypes" of the module, not "supertypes" of the class.

### Option 2: Implement proper processCallsOfType reflection
Debug why the consumer callback is not being invoked. The issue may be:
- Wrong method signature
- Wrong field name for `INCLUDE_CALL`
- Type mismatch in the callback

## MCP Tools Used

1. `intellij_index_ide_sync_files` - Sync project files
2. `intellij_index_ide_type_hierarchy` - Get type hierarchy
3. `intellij_index_ide_search_text` - Search for "include Authenticatable"
