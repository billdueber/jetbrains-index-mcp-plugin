# Ruby Plugin Index Explorer

**Location:** `ruby/scripts/ruby-index-explorer.kts`

A runtime reflection script that probes all handler-relevant Ruby plugin indexes
from inside IntelliJ IDEA (or RubyMine) and outputs a structured Markdown report.

---

## How to Run

1. **Open this project** in IntelliJ IDEA Ultimate / RubyMine with the Ruby plugin enabled
2. **Open the script:** `ruby/scripts/ruby-index-explorer.kts`
3. **Run it:**
   - Right-click the file → **Run 'ruby-index-explorer.kts'** (Kotlin script support)
   - OR: **Tools → Internal Actions → IDE Scripting Console**, load and evaluate
4. The script prints Markdown-formatted results to stdout — pipe to a file if desired

**Requirements:** Ruby plugin installed, a project with Ruby files open and indexed.

---

## Index Catalog (from bytecode analysis)

All key/value type signatures extracted via `javap` on the Ruby plugin's
`intellij.ruby.core.jar`. The hierarchy is:

```
StringStubIndexExtension<E>        (IntelliJ Platform)
  └── RubyStringStubIndexExtension<E extends RPsiElement>
        ├── RubyFqnStubIndexExtension<E extends RPsiElement>   ← FQN-keyed
        └── (direct: RubyClassModuleNameIndex, etc.)           ← simple-name-keyed
```

### Class/Module Resolution

| Index | Key | Value | Base |
|-------|-----|-------|------|
| `RubyClassModuleNameIndex` | `String` (simple name) | `RElementWithFQN` | `RubyStringStubIndexExtension` |
| `RubyMethodNameIndex` | `String` (method name) | `RMethod` | `StringStubIndexExtension` (direct) |
| `RubySymbolNameIndex` | `String` (symbol name) | `RPsiElement` | `RubyStringStubIndexExtension` |
| `RubyConstantDeclarationFqnIndex` | `String` (FQN) | `RConstant` | `RubyFqnStubIndexExtension` |
| `RubyGlobalVariableDeclarationNameIndex` | `String` (name) | `RPsiElement` | `RubyStringStubIndexExtension` |

### Inheritance / Type Hierarchy

| Index | Key | Value | Base |
|-------|-----|-------|------|
| `RubyInheritanceIndex` | `String` (subclass FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyInheritanceResolutionIndex` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyInheritanceResolutionIndex$ForSuperClasses` | `String` (superclass FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyIncludedExtendedFQNIndex` | `String` (module FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyAnonymousDefiningCallIndex` | `String` (FQN) | `RPossibleCall` | `RubyFqnStubIndexExtension` |
| `RubyAnonymousDeclarationSuperclassIndex` | `String` | (file-based) | `FileBasedIndexExtension` |

### General Resolution

| Index | Key | Value | Base |
|-------|-----|-------|------|
| `RubyResolutionIndex` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyResolutionIndex$ForCompletion` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyResolutionIndex$ForDocumentation` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |
| `RubyDynamicMethodsDeclarationsIndex` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |

### Require / Variable Tracking

| Index | Key | Value | Base |
|-------|-----|-------|------|
| `RubyRequireLoadIndex` | `String` (path) | `RPossibleCall` | `RubyStringStubIndexExtension` |
| `RubyAllInstanceVariablesIndex` | `String` (FQN) | `RPsiElement` | `RubyFqnStubIndexExtension` |

### File-Based Declaration Indexes

| Index | Key | Value | Notes |
|-------|-----|-------|-------|
| `RubyDeclarationFqnIndex` | `Type` (enum: `CLASS`/`MODULE`/`CONSTANT`) | `List<String>` (FQNs) | Per-file multi-value |
| `RubyDeclarationSuperclassIndex` | `String` (class FQN) | `List<Pair<FQN, String>>` | Superclass + metadata |
| `RubyDeclarationHierarchyIndex` | `String` (FQN) | `List<Pair<Type, String>>` | Hierarchy edges |

---

## API Surface: RubyStringStubIndexExtension<E>

All Ruby stub indexes inherit these methods (usable via reflection):

```kotlin
fun containsElements(project: Project, scope: SearchScope, key: String): Boolean
fun findElement(project: Project, scope: SearchScope, key: String): E?
fun findElement(project: Project, scope: SearchScope, key: String, predicate: Predicate<E>): E?
fun getAllElements(project: Project, scope: SearchScope): Collection<E>
fun getAllValidKeys(project: Project, scope: SearchScope): SequencedSet<String>
fun getElements(project: Project, scope: SearchScope, key: String): Collection<E>
fun processAllElements(project: Project, scope: SearchScope, processor: Processor<E>): Boolean
fun processElements(project: Project, scope: SearchScope, key: String, processor: Processor<E>): Boolean
```

## API Surface: RubyFqnStubIndexExtension<E>

Inherits all of the above, plus FQN-specific convenience methods:

```kotlin
fun containsElements(project: Project, scope: SearchScope, fqn: FQN): Boolean
fun findElement(project: Project, scope: SearchScope, fqn: FQN): E?
fun getElements(project: Project, scope: SearchScope, fqn: FQN): Collection<E>
fun processElements(project: Project, scope: SearchScope, fqn: FQN, processor: Processor<E>): Boolean
```

---

## Full Index Inventory (all 65 indexes across the Ruby plugin)

For completeness, here is every index registered in `plugin.xml` across all modules:

### Ruby Core (intellij.ruby.core) — 17 indexes

| # | Index | Type | Category |
|---|-------|------|----------|
| 1 | `RubyAnonymousDeclarationSuperclassIndex` | fileBased | hierarchy |
| 2 | `RubyDeclarationFqnIndex` | fileBased | declarations |
| 3 | `RubyDeclarationHierarchyIndex` | fileBased | hierarchy |
| 4 | `RubyDeclarationSuperclassIndex` | fileBased | hierarchy |
| 5 | `RubyConstantDeclarationFqnIndex` | stub | constants |
| 6 | `RubyGlobalVariableDeclarationNameIndex` | stub | variables |
| 7 | `RubyClassModuleNameIndex` | stub | classes/modules |
| 8 | `RubyAnonymousDefiningCallIndex` | stub | anonymous calls |
| 9 | `RubySymbolNameIndex` | stub | symbols |
| 10 | `RubyDynamicMethodsDeclarationsIndex` | stub | dynamic methods |
| 11 | `RubyInheritanceIndex` | stub | inheritance |
| 12 | `RubyRequireLoadIndex` | stub | requires |
| 13 | `RubyAllInstanceVariablesIndex` | stub | instance variables |
| 14 | `RubyInheritanceResolutionIndex` | stub | inheritance |
| 15 | `RubyInheritanceResolutionIndex$ForSuperClasses` | stub | inheritance |
| 16 | `RubyIncludedExtendedFQNIndex` | stub | mixins |
| 17 | `RubyResolutionIndex` | stub | resolution |
| 18 | `RubyResolutionIndex$ForCompletion` | stub | resolution |
| 19 | `RubyResolutionIndex$ForDocumentation` | stub | resolution |
| 20 | `RubyMethodNameIndex` | stub | methods |

### Rails (intellij.ruby.backend) — 8 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RenderCallIndex` | stub | Rails render calls |
| 2 | `RailsAttributeMethodFqnIndex` | stub | ActiveRecord attribute methods |
| 3 | `RailsPolymorphicAssociationNameIndex` | stub | Polymorphic associations |
| 4 | `RailsPolymorphicAssociationRefereeIndex` | stub | Polymorphic referees |
| 5 | `RailsStoreAccessorIndex` | stub | `store_accessor` declarations |
| 6 | `RakeTaskDeclarationIndex` | stub | Rake task names |
| 7 | `DeviseForNameIndex` | stub | Devise `for` declarations |
| 8 | `DeviseNameIndex` | stub | Devise module names |

### FactoryBot (intellij.ruby.backend) — 3 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RubyFactoryBotFactoryNameIndex` | stub | Factory definitions |
| 2 | `RubyFactoryBotSequenceNameIndex` | stub | Sequence definitions |
| 3 | `RubyFactoryBotTraitNameIndex` | stub | Trait definitions |

### RSpec (intellij.ruby.backend) — 2 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RubyRSpecSharedContextIndexName` | stub | Shared contexts |
| 2 | `RSpecSharedGroupRefIndex` | stub | Shared group references |

### Cucumber (intellij.ruby.cucumber) — 2 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RubyCucumberCallIndex` | stub | Cucumber step definitions |
| 2 | `RubyCucumberParameterTypeIndex` | stub | Cucumber parameter types |

### RBS (intellij.ruby.rbs.backend) — 17 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RbsDeclarationFqnIndex` | fileBased | RBS declarations by FQN |
| 2 | `RbsDeclarationByParentFqnIndex` | fileBased | RBS declarations by parent |
| 3 | `RbsGlobalVariableNameIndex` | fileBased | RBS global variables |
| 4 | `RbsMethodNamePropertiesIndex` | fileBased | RBS method properties |
| 5 | `RbsAttributeDeclarationNameIndex` | stub | RBS attributes |
| 6 | `RbsClassDeclarationFqnIndex` | stub | RBS classes |
| 7 | `RbsClassVariableDeclarationNameIndex` | stub | RBS class variables |
| 8 | `RbsConstantDeclarationFqnIndex` | stub | RBS constants |
| 9 | `RbsContainerStatementParentFqnIndex` | stub | RBS container parents |
| 10 | `RbsGlobalVariableDeclarationNameIndex` | stub | RBS global variables |
| 11 | `RbsInstanceVariableDeclarationNameIndex` | stub | RBS instance variables |
| 12 | `RbsInterfaceDeclarationFqnIndex` | stub | RBS interfaces |
| 13 | `RbsMethodDeclarationNameIndex` | stub | RBS methods |
| 14 | `RbsModuleDeclarationFqnIndex` | stub | RBS modules |
| 15 | `RbsSingletonStatementParentFqnIndex` | stub | RBS singleton parents |
| 16 | `RbsTypeDeclarationFqnIndex` | stub | RBS type aliases |
| 17 | `RbsIncludedContainerNameIndex` | stub | RBS includes |
| 18 | `RbsExtendedContainerNameIndex` | stub | RBS extends |

### I18n / YAML (intellij.ruby.yaml) — 4 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `I18nYAMLTranslationIndex` | fileBased | Translation keys |
| 2 | `I18nYAMLPossibleLocaleIndex` | fileBased | Locale detection |
| 3 | `I18nTranslationKeysIndex` | fileBased | All translation keys |
| 4 | `YamlTopLevelKeysIndex` | fileBased | YAML top-level keys |

### RDoc / YARD — 3 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RDocFormatIndex` | fileBased | RDoc format detection |
| 2 | `YardMacroDescriptionsIndex` | fileBased | YARD macro descriptions |
| 3 | `YardMacroNamesIndex` | fileBased | YARD macro names |

### Import Map (JavaScript) — 2 indexes

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RailsImportMapModuleDynamicDeclarationIndex` | stub | Dynamic import map entries |
| 2 | `RailsImportMapModuleNameIndex` | stub | Import map module names |

### Stimulus (JavaScript) — 1 index

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `StimulusControllerNameIndex` | fileBased | Stimulus controllers |

### JRuby — 1 index

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `JRubyImportIndex` | stub | Java imports in JRuby |

### ERB — 1 stub registry (not an index)

| # | Extension | Type | Purpose |
|---|-----------|------|---------|
| 1 | `ErbStubRegistryExtension` | registry | ERB stub types |

### Database — 1 index

| # | Index | Type | Purpose |
|---|-------|------|---------|
| 1 | `RailsSqlSchemaVersionIndex` | fileBased | Schema version tracking |

---

## Relationship to Existing MCP Handlers

| MCP Handler | Relevant Index | How |
|------------|---------------|-----|
| `TypeHierarchy` | `RubyInheritanceIndex`, `RubyIncludedExtendedFQNIndex`, `RubyInheritanceResolutionIndex$ForSuperClasses` | Walk super/subtype relations |
| `FindImplementations` | `RubyInheritanceIndex`, `RubyInheritanceResolutionIndex$ForSuperClasses` | Find subclass implementations |
| `SuperMethods` | `RubyMethodNameIndex`, `RubyResolutionIndex` | Locate methods with same name in hierarchy |
| `CallHierarchy` | `RubyResolutionIndex`, `RubyMethodNameIndex` | Resolve method call targets |
| `FindUsages` | `RubySymbolNameIndex`, `RubyResolutionIndex$ForCompletion` | Alternative to `ReferencesSearch` |
| `FindDefinition` | `RubyClassModuleNameIndex`, `RubyDeclarationFqnIndex` | Skip direct StubIndex, use `DefinitionsScopedSearch` |