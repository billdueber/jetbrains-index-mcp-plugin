# Ruby Plugin Stub Indexes - Empirical Test Plan

## Indexes of Interest for Type Hierarchy

| Index | Package | Purpose | Status |
|-------|---------|---------|--------|
| RubyInheritanceIndex | intellij.ruby.core | Superclass FQN → subclass elements | EXISTS |
| RubyClassModuleNameIndex | intellij.ruby.core | Short name → RElementWithFQN | EXISTS |
| RubyIncludedExtendedFQNIndex | intellij.ruby.core | Module FQN → classes that include/extend it | EXISTS |
| RubyInheritanceResolutionIndex | intellij.ruby.core | Own FQN → element | EXISTS |
| RubySymbolNameIndex | intellij.ruby.core | Symbol names | EXISTS |
| RubyConstantDeclarationFqnIndex | intellij.ruby.core | Constant FQNs | EXISTS |

## RClass Interface Methods (from research documents)

| Method | Returns | Exists | Notes |
|--------|---------|--------|-------|
| `getName()` | `String?` | YES | Class name |
| `getSuperClassFQN()` | `FQN` | YES | Superclass FQN |
| `getPsiSuperClass()` | `RSuperClass` | YES | PSI node for superclass |
| `getClassName()` | `RName` | YES | Name element |
| `processCallsOfType()` | `void` | YES | Iterates calls by type |
| `getIncludedModules()` | `List<PsiElement>` | UNCLEAR | May not exist on interface |

## Key Finding: getIncludedModules() may not exist

The `getIncludedModules()` method is documented in 03-ruby-psi-reference.md but NOT in the main RubyTypeHierarchyHandler.md research document. This suggests it may:
1. Be on RClassBase (implementation) not RClass (interface)
2. Not exist in this Ruby plugin version
3. Have a different name

**Recommendation:** Use `processCallsOfType(RubyIncludeExtendCallTypes.INCLUDE_CALL)` instead, as it's documented in both research documents.

## Test 4 Results (from MCP tools)

### ide_search_text for "include Authenticatable"
- Found in user.rb at line 2, column 3
- Context: `include Authenticatable`
- Total matches: 8

### ide_type_hierarchy for user.rb (test 2)
- element.name = "User"
- element.kind = "CLASS"
- supertypes = [] (EMPTY - BUG!)
- subtypes = []

**Root Cause:** The `getIncludedModuleFQNs` function returns empty list because:
1. `getIncludedModules()` method doesn't exist or returns incompatible type
2. Fallback `processCallsOfType` implementation failing silently

## Next Steps

1. Verify `processCallsOfType` signature with Ruby plugin
2. Check if `INCLUDE_CALL` field is accessible
3. Debug the consumer callback to see if it's being invoked