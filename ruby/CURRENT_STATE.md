# CURRENT STATE — Ruby Plugin Integration

## Stage 1: Stubs + Registration ⬛ (done)

### Created
| File | Status | What it does |
|------|--------|-------------|
| `src/main/kotlin/.../handlers/ruby/RubyHandlers.kt` | ✅ Stub | Registration object + `BaseRubyHandler` + 6 stub handlers (all return null/empty) |
| `src/test/kotlin/.../handlers/ruby/RubyHandlersUnitTest.kt` | ✅ Stub | 19 tests: metadata, availability, registry wiring, stub guardrails |
| `src/test/kotlin/.../handlers/ruby/RubySymbolReferenceHandlerUnitTest.kt` | ✅ Stub | 27 tests: Ruby symbol pattern (`::`, `#`, `#?`, `#!`, `#=`) |
| `ruby/research/mcp_tools/ide_type_hierarchy.md` | ✅ Research | Full analysis of `ide_type_hierarchy` — Java/Python/PHP implementations, Ruby plugin indexes, implementation strategy |
| `ruby/CURRENT_STATE.md` | ✅ | This file |

### Modified (minimal registration wiring)
| File | Change |
|------|--------|
| `PluginDetectors.kt` | Added `val ruby` detector (plugin ID: `org.jetbrains.plugins.ruby`, fallback: `RClass`) |
| `LanguageHandlerRegistry.kt` | Added Ruby to `handlerRegistrations` |

### Handler interfaces stubbed (all return null/emptyList)
- `RubyTypeHierarchyHandler` — `getTypeHierarchy()` → `null`
- `RubyImplementationsHandler` — `findImplementations()` → `null`
- `RubyCallHierarchyHandler` — `getCallHierarchy()` → `null`
- `RubySuperMethodsHandler` — `findSuperMethods()` → `null`
- `RubyStructureHandler` — `getFileStructure()` → `emptyList()`
- `RubySymbolReferenceHandler` — format validation passes, resolution → `"not yet implemented"`

## Stage 2: `ide_type_hierarchy` — Implemented ✅

### Chosen tool
**`ide_type_hierarchy`** — first language-dependent tool to implement. Selected because it's alphabetically first among the 9 tools needing language-specific handlers, and Ruby's single-inheritance + mixin model maps cleanly to the `TypeHierarchyHandler` interface.

### Research complete
| Document | Contents |
|----------|----------|
| `ruby/research/mcp_tools/ide_type_hierarchy.md` | Java/Python/PHP implementation patterns, Ruby plugin indexes (`RubyInheritanceIndex`, `RubyClassModuleNameIndex`, `RubyDeclarationSuperclassIndex`), Ruby PSI methods (`RClass.getSuperClass()`, `RClass.getIncludedModules()`), recommended implementation strategy with fallback |
| `ruby/research/RubyTypeHierarchyHandler.md` | **New** — Decompiled API analysis of RClass, RModule, RContainer, FQN, RubyInheritanceIndex, RubyClassModuleNameIndex, RubyIncludedExtendedFQNIndex, RubyIncludeExtendCallTypes + implementation plan |

### Implementation complete
| Component | Status | Details |
|-----------|--------|--------|
| `BaseRubyHandler` reflection utilities | ✅ | Added `getRubyQualifiedName()`, `rClassGetSuperClassFQN()`, `resolveByFQN()`, `getIncludedModuleFQNs()`, `getExtendedModuleFQNs()`, `findSubtypesForFQN()`, `findIncludingClasses()`, `findExtendingClasses()` + lazy class references for all 5 Ruby indexes/supporting classes |
| `RubyTypeHierarchyHandler.getSupertypes()` | ✅ | Recursive walk via `RClass.getSuperClassFQN()` → `RubyInheritanceIndex.getElements()` for class hierarchy; `processCallsOfType(INCLUDE_CALL)` for module mixins; visited-set cycle detection; 50 max depth guard |
| `RubyTypeHierarchyHandler.getSubtypes()` | ✅ | Two strategies: (1) Class inheritance via `RubyClassModuleNameIndex` iteration + superclass FQN check, (2) Module mixins via `RubyIncludedExtendedFQNIndex.getOnIncludedElements()` |
| `RubyTypeHierarchyHandler.getTypeHierarchy()` | ✅ | Entry point: finds containing class/module, builds TypeHierarchyData with element, supertypes, subtypes |
| `RubyCallHierarchyHandler.getCallHierarchy()` | ✅ | Callers via `ReferencesSearch.search()`, Callees via `PsiTreeUtil.findChildrenOfType(RCall)` + `getPsiCommand().getReference().resolve()`, cycle detection via `getMethodKey()`, 20 max results/level, 50 max stack depth |

### Reflection approach used
All Ruby PSI access is via `Class.forName()` + `.invoke()` — no compile-time dependency on the closed-source Ruby plugin. Key patterns:
- **Index singletons** via `getInstance()` static method
- **FQN creation** via `FQN.of(String)` static method
- **Method lookup** via `element.javaClass.getMethod(name, paramTypes)`
- **Inherited methods** found on `instance.javaClass` (not `indexClass.javaClass`)
- **Kotlin object constants** via `getField(name).get(null)`

### Reflection classes discovered and used
| Class | Reflection Lazy Val | Usage |
|-------|---------------------|-------|
| `FQN` | `fqnClass` | `of()`, `getFullPath()`, `same()` |
| `RubyInheritanceIndex` | `rubyInheritanceIndexClass` | `getInstance()` → `getElements(project, scope, FQN)` for FQN→PSI resolution |
| `RubyClassModuleNameIndex` | `rubyClassModuleNameIndexClass` | Enumeration for subtype discovery via `find()` |
| `RubyIncludedExtendedFQNIndex` | `rubyIncludedExtendedFQNIndexClass` | `getOnIncludedElements()` / `getOnExtendedElements()` for mixin reverse lookup |
| `RubyIncludeExtendCallTypes` | `rubyIncludeExtendCallTypesClass` | `INCLUDE_CALL` / `EXTEND_CALL` constants for `processCallsOfType()` |

### Test results
- **12 unit tests** (`RubyTypeHierarchyHandlerUnitTest`): ✅ All pass — FQN reconstruction, kind detection, cycle detection, max-depth, edge cases
- **8 platform tests** (`RubyTypeHierarchyPlatformTest`): CI-only (require Ruby plugin)
- **7 unit tests** (`RubyCallHierarchyHandlerUnitTest`): ✅ All pass — metadata, registry wiring, mock-based edge cases, stub guardrails
- **Compilation**: ✅ `compileKotlin` passes (1 warning: unchecked cast on StubIndexKey)

## Next Steps
1. ~~Implement `RubyTypeHierarchyHandler`~~ ✅
2. `RubyImplementationsHandler` — `PyOverridingMethodsSearch` equivalent for Ruby
3. `RubyCallHierarchyHandler` — caller/callee tree via Ruby PSI ✅
4. `RubySuperMethodsHandler` — override chain via `RubyOverrideImplementUtil`
5. `RubyStructureHandler` — file structure via `RClass`/`RModule`/`RMethod`
6. `RubySymbolReferenceHandler` — real resolution via `RubyGotoClassContributor`