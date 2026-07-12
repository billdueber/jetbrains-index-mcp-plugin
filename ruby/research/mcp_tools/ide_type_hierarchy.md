# ide_type_hierarchy — Get the complete inheritance hierarchy for a class or interface

> **Tool file:** `src/main/kotlin/.../tools/navigation/TypeHierarchyTool.kt`
> **Handler interfaces used:** `TypeHierarchyHandler`
> **Ruby handler:** `RubyTypeHierarchyHandler` (stub, returns `null`)

## What it does

Returns the complete inheritance hierarchy for a class/interface: the element itself, its supertypes (recursively), and its subtypes in the project. Supports both `className` (FQN) and `file`+`line`+`column` lookup.

## How each language implements it

| Language | Cleanliness | Mechanism | Key classes/calls |
|----------|-------------|-----------|-------------------|
| Java | clean | Direct PSI API | `PsiClass.getSuperClass()`, `PsiClass.getInterfaces()`, `ClassInheritorsSearch.search()` |
| Python | semi-clean | Reflection + language-specific search | `PyClass.getSuperClass()` (reflection), `PyClassInheritorsSearch.search()` (reflection) |
| PHP | clean | Reflection + PhpIndex API | `PhpClass.getSuperClass()` (reflection), `PhpIndex.getAllSubclasses()` (reflection) |

**Cleanliness scale:**
- **clean** = direct 1-1 call to a plugin-provided PSI method or search utility
- **semi-clean** = generic platform API — works for any language but less optimized
- **messy** = manual PSI tree walk, multiple fallbacks, workarounds for bugs

### Java details

```kotlin
// Supertypes: direct PSI API (no reflection needed)
val superClass = psiClass.superClass                          // PsiClass?
val interfaces = psiClass.interfaces                          // PsiClass[]

// Subtypes: platform search utility (Processor pattern)
ClassInheritorsSearch.search(psiClass, searchScope, true).forEach(Processor { subClass ->
    results.add(convertToTypeElement(subClass))
    results.size < 100  // early termination
})
```

### Python details

```kotlin
// Supertypes: reflection-based getSuperClass
// getSuperClasses() is a method on PyClass accessed via reflection
val superClasses = getSuperClasses(pyClass)  // reflection call to PyClass.getSuperClasses()

// Subtypes: PyClassInheritorsSearch (reflection)
val searchClass = Class.forName("com.jetbrains.python.psi.search.PyClassInheritorsSearch")
val searchMethod = searchClass.getMethod("search", pyClassClass, java.lang.Boolean.TYPE)
val query = searchMethod.invoke(null, pyClass, true)
val findAllMethod = query.javaClass.getMethod("findAll")
val inheritors = findAllMethod.invoke(query) as? Collection<*>
```

### PHP details

```kotlin
// Supertypes: reflection-based getSuperClass + getImplementedInterfaces + getTraits
val superClass = getSuperClass(phpClass)           // PhpClass.getSuperClass() via reflection
val interfaces = getImplementedInterfaces(phpClass) // PhpClass.getImplementedInterfaces() via reflection
val traits = getTraits(phpClass)                    // PhpClass.getTraits() via reflection

// Subtypes: PhpIndex.getAllSubclasses(FQN) via reflection
val phpIndex = getPhpIndex(project)  // PhpIndex.getInstance(project) via reflection
val getAllSubclassesMethod = phpIndex.javaClass.getMethod("getAllSubclasses", String::class.java)
val subclasses = getAllSubclassesMethod.invoke(phpIndex, fqn)
```

## Ruby implementation plan

### Plugin capabilities relevant to this tool

| Index/Contributor | What it provides | Relevance |
|-------------------|-----------------|-----------|
| `RClass.getSuperClass()` | Direct superclass via PSI method | **direct** — primary for supertype chain |
| `RClass.getIncludedModules()` | Modules included via `include`/`extend` | **direct** — shown as mixin "supertypes" |
| `RModule.getIncludedModules()` | Modules included in module | **direct** — for module-to-module mixin relationships |
| `RubyClassModuleNameIndex` (stubIndex) | All classes/modules by name | **direct** — enumerate all classes for subtype discovery |
| `RubyInheritanceIndex` (stubIndex) | Inheritance relationships | **direct** — can be used to find subclasses of a given class |
| `RubyDeclarationSuperclassIndex` (fileBasedIndex) | Superclass for each declaration | **direct** — alternative to find subclasses via FQN lookup |
| `RubyDeclarationHierarchyIndex` (fileBasedIndex) | Hierarchy relationships | **partial** — may provide hierarchy data but format unknown |
| `RubyInheritanceResolutionIndex` (stubIndex) | Resolution of inherited members | **partial** — may include subtype information |
| `RubyInheritanceResolutionIndex$ForSuperClasses` (stubIndex) | Superclass resolution | **indirect** — could be used for subtype discovery |
| `RubyGotoClassContributor` | Go to Class (Ctrl+N) by name | **partial** — can verify class existence, not used for hierarchy |

### Recommended approach — Primary: Ruby PSI methods + StubIndex for subtypes

```kotlin
override fun getTypeHierarchy(
    element: PsiElement,
    project: Project,
    scope: BuiltInSearchScope,
    excludeGenerated: Boolean
): TypeHierarchyData? {
    val rClass = findContainingRClassOrRModule(element) ?: return null
    val searchScope = createNavigationSearchScope(project, scope, excludeGenerated)

    val supertypes = getSupertypes(project, rClass, searchScope = searchScope)
    val subtypes = getSubtypes(project, rClass, searchScope)

    return TypeHierarchyData(
        element = TypeElementData(
            name = getQualifiedName(rClass) ?: getName(rClass) ?: "unknown",
            qualifiedName = getQualifiedName(rClass),
            file = rClass.containingFile?.virtualFile?.let { getRelativePath(project, it) },
            line = getLineNumber(project, rClass),
            kind = if (isRClass(rClass)) "CLASS" else "MODULE",
            language = "Ruby"
        ),
        supertypes = supertypes,
        subtypes = subtypes
    )
}

// Supertypes: walk up using RClass.getSuperClass() + RClass.getIncludedModules()
private fun getSupertypes(
    project: Project,
    element: PsiElement,
    visited: MutableSet<String> = mutableSetOf(),
    depth: Int = 0,
    searchScope: GlobalSearchScope
): List<TypeElementData> {
    if (depth > 50) return emptyList()
    val className = getQualifiedName(element) ?: getName(element) ?: return emptyList()
    if (className in visited) return emptyList()
    visited.add(className)

    val supertypes = mutableListOf<TypeElementData>()

    try {
        // Get superclass via RClass.getSuperClass()
        if (isRClass(element)) {
            val superClass = rClassGetSuperClass(element)
            if (superClass != null && shouldIncludeNavigationElement(searchScope, superClass)) {
                val superName = getQualifiedName(superClass) ?: getName(superClass)
                if (superName != null && superName !in visited) {
                    val superSupertypes = getSupertypes(project, superClass, visited, depth + 1, searchScope)
                    supertypes.add(TypeElementData(
                        name = superName,
                        qualifiedName = getQualifiedName(superClass),
                        file = superClass.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, superClass),
                        kind = "CLASS",
                        language = "Ruby",
                        supertypes = superSupertypes.takeIf { it.isNotEmpty() }
                    ))
                }
            }
        }

        // Get included modules via getIncludedModules() (works for both RClass and RModule)
        val includedModules = getIncludedModules(element)
        includedModules?.filterIsInstance<PsiElement>()?.forEach { module ->
            val modName = getQualifiedName(module) ?: getName(module)
            if (modName != null && modName !in visited && shouldIncludeNavigationElement(searchScope, module)) {
                supertypes.add(TypeElementData(
                    name = modName,
                    qualifiedName = getQualifiedName(module),
                    file = module.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                    line = getLineNumber(project, module),
                    kind = "MODULE",
                    language = "Ruby"
                ))
            }
        }
    } catch (e: Exception) {
        // Handle gracefully
    }

    return supertypes
}

// Subtypes: use RubyClassModuleNameIndex to find all classes + check superclass
private fun getSubtypes(
    project: Project,
    element: PsiElement,
    searchScope: GlobalSearchScope
): List<TypeElementData> {
    val targetFqn = getQualifiedName(element) ?: getName(element) ?: return emptyList()
    val results = mutableListOf<TypeElementData>()

    try {
        // Approach: Query RubyClassModuleNameIndex for all class/module names,
        // then resolve each one and check if its superclass matches targetFqn
        val stubIndex = com.intellij.psi.stubs.StubIndex.getInstance()
        val indexKey = Class.forName("org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyClassModuleNameIndex")
        val getKeyMethod = indexKey.getMethod("getKey")
        val key = getKeyMethod.invoke(null)
        // key is a StubIndexKey<String, ...>

        // Get all names from the index
        val allNames = stubIndex.getAllKeys(key as com.intellij.psi.stubs.StubIndexKey<String, *>, project)
        allNames.forEach { name ->
            val items = stubIndex.get(key, name, project, searchScope)
            items.forEach { psi ->
                if (psi != element && shouldIncludeNavigationElement(searchScope, psi)) {
                    // Check if this class extends targetFqn
                    val superClass = getSuperClass(psi)
                    if (superClass != null) {
                        val superFqn = getQualifiedName(superClass) ?: getName(superClass)
                        if (superFqn == targetFqn) {
                            results.add(TypeElementData(...))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        LOG.warn("RubyInheritanceIndex approach failed: ${e.message}")
    }

    return results.take(100)
}
```

### Reflection utility methods needed in BaseRubyHandler

```kotlin
protected fun rClassGetSuperClass(element: PsiElement): PsiElement? {
    return try {
        val method = element.javaClass.getMethod("getSuperClass")
        method.invoke(element) as? PsiElement
    } catch (e: Exception) { null }
}

protected fun getIncludedModules(element: PsiElement): List<*>? {
    return try {
        val method = element.javaClass.getMethod("getIncludedModules")
        method.invoke(element) as? List<*>
    } catch (e: Exception) { null }
}
```

### Fallback approach (if StubIndex query fails)

```kotlin
// Fallback: Use DefinitionsScopedSearch + platform APIs to find subclasses
// This is slower but guaranteed to work
val elementDef = ... // Get a PsiElement for targetFqn
if (elementDef != null) {
    val search = com.intellij.psi.search.searches.DefinitionsScopedSearch.search(elementDef, searchScope)
    // ... process results
}
```

### Implementation steps
1. Add `rClassGetSuperClass()` and `getIncludedModules()` reflection methods to `BaseRubyHandler`
2. Implement `getSupertypes()` in `RubyTypeHierarchyHandler` — recursive walk up superclass chain + included modules
3. Implement `getSubtypes()` in `RubyTypeHierarchyHandler` — query `RubyClassModuleNameIndex`, check each class's superclass
4. Add import for `StubIndex` and related classes (platform imports, OK to use directly)
5. Update tool description string to include `"Ruby"` in supported languages
6. Write platform tests in CI (never run locally)

### Ruby language semantics to handle
- **Single inheritance**: `class Child < Parent` — only one superclass, unlike Java/Python's multiple
- **Modules as mixins**: `include ModuleName` / `extend ModuleName` — shown as "supertypes" alongside the class superclass
- **Modules as namespaces**: `module Admin` containing `class User` — `Admin::User` has FQN but `RClass.getFullyQualifiedName()` may return `null`; FQN reconstruction needed
- **No interface keyword**: Modules serve the role of both interfaces and abstract classes
- **Anonymous classes**: `Class.new` creates anonymous classes — these should be skipped or have `nil` FQN handled
- **`getFullyQualifiedName()` returns null**: PSI reference doc says this is a known gotcha for module-namespaced classes; fallback to reconstructing FQN via PSI ancestor walk

### Risks and unknowns
- **`RubyClassModuleNameIndex` key type**: The `getKey()` method may return a `StubIndexKey` with different type parameters than expected. Needs `Class.forName()` confirmation via live test.
- **`getIncludedModules()` signature**: May return `List<PsiElement>`, `Collection<PsiElement>`, or `Array<PsiElement>`. Needs reflection validation.
- **`getSuperClass()` on RModule**: May throw or return null (modules don't have superclasses in Ruby). Always check `isRClass()` first.
- **Performance**: Enumerating ALL classes from `RubyClassModuleNameIndex` and checking each one's superclass could be slow on large projects. The PHP approach uses `PhpIndex.getAllSubclasses(fqn)` which is O(1) via the index. Ruby may not have a direct equivalent — the `RubyInheritanceIndex` stub index might provide this directly.

## Search commands for further investigation

```bash
# Find Ruby plugin classes related to inheritance
jar tf ~/Library/Application\ Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/ruby.jar | grep -iE "Inheritance|Subclass|Hierarchy|Inherit"

# Check RubyInheritanceIndex methods via reflection exploration
# (requires running IDE with Ruby plugin)
```