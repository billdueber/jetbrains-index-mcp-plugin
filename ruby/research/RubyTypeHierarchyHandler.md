# Ruby Type Hierarchy Handler — PSI API Research

## Goal

Implement `RubyTypeHierarchyHandler` to produce type hierarchy trees (supertypes +
subtypes) for Ruby classes and modules, supporting single inheritance (`class Child < Parent`),
module mixins (`include`, `extend`, `prepend`), and recursive traversal with cycle detection.

## Sources

- Decompiled Ruby plugin JARs from IntelliJIdea 2026.2, Ruby plugin 2026.2.x:
  - `intellij.ruby.psi.jar` — PSI interfaces (RClass, RModule, RContainer, FQN, etc.)
  - `intellij.ruby.psi.impl.jar` — PSI implementations (RClassImpl, RClassBase, RModuleImpl)
  - `intellij.ruby.core.jar` — Stub indexes (RubyInheritanceIndex, RubyClassModuleNameIndex,
    RubyIncludedExtendedFQNIndex, RubyFqnStubIndexExtension)
  - `intellij.ruby.backend.jar` — Hierarchy trees (RubySuperHierarchyTreeStructure,
    RubySubTypesHierarchyTreeStructure, RubyTypeHierarchyProvider)
- `plugin.xml` — Index and extension registrations
- Existing handlers: PythonTypeHierarchyHandler (the closest analogue)

---

## PSI Interfaces (used via `Class.forName()` + reflection)

### RClass

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass`

**Full signature:**

```
public interface RClass extends
  PsiNameIdentifierOwner,
  RNamespace,           // → RElementWithFQN → RPsiElement
  RFieldHolder,
  RConstantHolder,
  RMethodHolder,
  RDocumentationSymbolsHolder,
  RubySuppressionHolder
```

| Method | Returns | Purpose |
|--------|---------|---------|
| `getSuperClassFQN()` | `FQN` | FQN of the direct superclass (e.g., `Object`, `ActiveRecord::Base`). Returns `null` for `Object` itself. |
| `getPsiSuperClass()` | `RSuperClass` | PSI node for the `< ParentClass` clause. Resolvable to the actual `RClass`. |
| `getClassName()` | `RName` | Name element of the class declaration. |
| `processCallsOfType(RubyCallType, Consumer<RCall>)` | `void` | Iterates call expressions of a given type within the class body — used for `include`/`extend`/`prepend`. |

### RModule

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule`

**Full signature:**

```
public interface RModule extends
  PsiNameIdentifierOwner,
  RExpression,
  RNamespace,           // → RElementWithFQN → RPsiElement
  RFieldHolder,
  RConstantHolder,
  RMethodHolder,
  RDocumentationSymbolsHolder,
  RubySuppressionHolder
```

| Method | Returns | Purpose |
|--------|---------|---------|
| `getQualifiedName()` | `String` | Full qualified name of the module (e.g., `Admin::Permissions`). Direct string access — unlike RClass which returns an `FQN` object. |
| `getModuleName()` | `RName` | Name element of the module declaration. |
| `isDocumented()` | `boolean` | Whether the module has documentation comments. |
| `isNamespace()` | `boolean` | Returns `true` if the module is used purely as a namespace (no methods/constants). |

### RContainer

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.holders.RContainer`

**Full signature:**

```
public interface RContainer extends
  RPsiStructureElement,
  RElementWithFQN,      // → RPsiElement
  ScopeHolder
```

| Method | Returns | Purpose |
|--------|---------|---------|
| `getName()` | `String` | Short name of the container (class or module). |
| `getStructureElements()` | `List<RPsiStructureElement>` | Direct children (methods, inner classes, constants). |
| `getFQN()` | `FQN` | Full qualified name as an FQN object (inherited from `RElementWithFQN`). |
| `getFQNWithNesting()` | `FQN` | FQN including the class's nesting scope. |

**Why RContainer matters:** The Ruby plugin's own hierarchy system
(`RubySuperHierarchyTreeStructure`, `RubySubTypesHierarchyTreeStructure`) takes
`RContainer` as the base type — NOT `RClass` or `RModule` separately. Both
`RClass` and `RModule` implement `RContainer`. Confirms our handler must accept
both.

### RElementWithFQN

**Path:** `org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.RElementWithFQN`

**Full signature:**

```
public interface RElementWithFQN extends RPsiElement
```

| Method | Returns | Purpose |
|--------|---------|---------|
| `getFQN()` | `FQN` | The element's fully qualified name. |
| `getFQNWithNesting()` | `FQN` | FQN with nesting context (e.g., within a parent namespace). |

**Note:** `RClass` does NOT declare `getFQN()` directly — it inherits it through
`RNamespace → RElementWithFQN`. Access via reflection on the `RClass` instance
will still work since it's inherited.

### FQN

**Path:** `org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN`

```
public interface FQN {
    String getFullPath();       // "Admin::User"
    String getShortName();      // "User"
    FQN normalize();
    boolean isToplevel();
    boolean processNestingResolution(Processor<? super FQN>);
    FQN getCallerFQN();
    FQN getParentFqn();
    List<String> asList();
    Stream<String> asStream();

    static FQN of(String s);
    static FQN of(String s, String... parts);
    static FQN ofNullable(String s);
    static boolean same(FQN fqn, String str);
}
```

| Static Method | Returns | Purpose |
|---------------|---------|---------|
| `FQN.of("Admin::User")` | `FQN` | Parse a `::`-delimited string into an FQN. |
| `FQN.same(fqn, "Admin::User")` | `boolean` | Check FQN equality with string. |
| `FQN.ofNullable(str)` | `FQN` | Create FQN from nullable string. |

### RSuperClass

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.names.RSuperClass`

```
public interface RSuperClass extends RPsiElement
```

Marker interface — no additional methods. The actual `RClass` target is resolved
by following the `PsiReference` on the PSI element. Simpler path: use
`getSuperClassFQN()` + FQN-based index lookup.

### RMethod

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod`

```
public interface RMethod extends
  PsiNameIdentifierOwner,
  RContainer,
  RubySuppressionHolder
```

| Method | Returns | Purpose |
|--------|---------|---------|
| `getName()` | `String` | Method name (e.g., `save`, `admin?`, `validate!`). |
| `getMethodName()` | `RName` | Name element node. |
| `getArgumentList()` | `RArgumentList` | Method parameters. |
| `hasSuperCall()` | `boolean` | Whether the method body contains a `super` call. |
| `isConstructor()` | `boolean` | Whether this is `initialize`. |

---

## Stub Indexes

### RubyInheritanceIndex (FQN → RPsiElement)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyInheritanceIndex`

**Full hierarchy:**

```
RubyInheritanceIndex
  extends RubyFqnStubIndexExtension<RPsiElement>
    extends RubyStringStubIndexExtension<RPsiElement>
```

```
public final class RubyInheritanceIndex
    extends RubyFqnStubIndexExtension<RPsiElement> {

    public static final StubIndexKey<String, RPsiElement> KEY;

    public StubIndexKey<String, RPsiElement> getKey();
    public static RubyInheritanceIndex getInstance();
}
```

**Methods inherited from `RubyFqnStubIndexExtension<RPsiElement>`:**

| Method | Returns | Purpose |
|--------|---------|---------|
| `getElements(Project, SearchScope, FQN)` | `Collection<RPsiElement>` | Look up all elements indexed by the given FQN. |
| `findElement(Project, SearchScope, FQN)` | `RPsiElement` | Find a single element, returns first match. |
| `containsElements(Project, SearchScope, FQN)` | `boolean` | Check if any element is indexed at this FQN. |
| `processElements(Project, SearchScope, FQN, Processor<E>)` | `boolean` | Iterate elements with processor (supports early termination). |

**Usage pattern for superclass resolution:**

```kotlin
val index = callStatic("RubyInheritanceIndex", "getInstance")
val elements = callMethod(index, "getElements",
    Project::class.java, project,
    SearchScope::class.java, searchScope,
    FQN::class.java, fqnObj
) as? Collection<*>
val superClassPsi = elements?.firstOrNull() as? PsiElement
```

**How keys are indexed:** Each class indexes its OWN FQN in `RubyInheritanceIndex`.
When you query `getElements(project, scope, FQN.of("Admin::User"))`, you get
the `RClass` element for `Admin::User` back. This makes it a **lookup-by-FQN**
index, not a subtype index.

### RubyClassModuleNameIndex (short name → RElementWithFQN)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyClassModuleNameIndex`

```
public final class RubyClassModuleNameIndex
    extends RubyStringStubIndexExtension<RElementWithFQN> {

    public static final StubIndexKey<String, RElementWithFQN> KEY;

    public static RubyClassModuleNameIndex getInstance();
    public static Collection<RElementWithFQN> find(
        Project project,
        String name,              // short name, e.g. "User"
        GlobalSearchScope scope
    );
    public static RElementWithFQN findOne(
        Project project,
        String name,
        GlobalSearchScope scope,
        Predicate<? super RPsiElement> predicate
    );
}
```

**Usage:** `RubyClassModuleNameIndex.find(project, "User", scope)` returns ALL
classes and modules named `User` across all namespaces. Filter by FQN to narrow.

**Key insight:** Unlike `RubyInheritanceIndex` which keys by FQN, this indexes
by **short name**. Multiple entries per key are common (namespaced classes
with the same short name).

### RubyIncludedExtendedFQNIndex (FQN → including/extending elements)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyIncludedExtendedFQNIndex`

```
public final class RubyIncludedExtendedFQNIndex
    extends RubyFqnStubIndexExtension<RPsiElement> {

    public static final String INCLUDED;
    public static final String EXTENDED;
    public static final String PREPENDED;

    // Reverse lookups: given a MODULE's FQN, find classes/modules that use it
    public Collection<RPsiElement> getOnIncludedElements(
        Project, SearchScope, FQN moduleFqn);
    public Collection<RPsiElement> getOnExtendedElements(
        Project, SearchScope, FQN moduleFqn);
    public Collection<RPsiElement> getOnPrependedElements(
        Project, SearchScope, FQN moduleFqn);

    public static RubyIncludedExtendedFQNIndex getInstance();

    // Indexing-side: called during stub construction
    public static void sink(FQN fqn, IndexSink sink);
}
```

**Two usage modes:**

1. **Reverse (what we need for mixin supertypes):** Given a class `User`,
   find which modules it includes. This requires walking `include` calls
   within the class body and reading their FQN targets — NOT using this index
   (which goes the opposite direction).

2. **Forward (what we need for mixin subtypes):** Given a module `Authenticatable`,
   `getOnIncludedElements(project, scope, FQN.of("Authenticatable"))` returns
   all classes that `include Authenticatable`. This is the **mixins-as-subtypes**
   direction — classes that use a module are listed as "subtypes" of the module.

### RubyFqnStubIndexExtension (abstract base)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyFqnStubIndexExtension`

```
public abstract class RubyFqnStubIndexExtension<E extends RPsiElement>
    extends RubyStringStubIndexExtension<E> {

    public boolean containsElements(Project, SearchScope, FQN);
    public E findElement(Project, SearchScope, FQN);
    public Collection<E> getElements(Project, SearchScope, FQN);
    public boolean processElements(Project, SearchScope, FQN, Processor<? super E>);
}
```

All FQN-based indexes inherit from this and provide FQN-based lookup directly.

---

## Include/Extend/Prepend Resolution

### RubyIncludeExtendCallTypes (Kotlin object)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RubyIncludeExtendCallTypes`

```kotlin
object RubyIncludeExtendCallTypes {
    const val INCLUDE_COMMAND = "include"
    const val EXTEND_COMMAND = "extend"
    const val PREPEND_COMMAND = "prepend"

    val INCLUDE_CALL: RubyCallType<List<FQN>>
    val PREPEND_CALL: RubyCallType<List<FQN>>
    val EXTEND_CALL: RubyCallType<List<FQN>>
    val ALL: List<RubyCallType<List<FQN>>>
}
```

**Usage via reflection on `RClassBase.processCallsOfType()`:**

```kotlin
// INCLUDE_CALL gets the FQNs of modules included in this class
val includeCallType = field("RubyIncludeExtendCallTypes", "INCLUDE_CALL")
processCallsOfType(rClass, includeCallType) { call: RPsiElement ->
    // call.getCallData() returns List<FQN>
    val fqns = getCallData(call) as? List<*>
    fqns?.filterIsInstance<Any>()?.forEach { fqn ->
        val fqnStr = fqnGetFullPath(fqn)
        // fqnStr is the module's FQN (e.g., "ActiveRecord::Base")
        resolveByFQN(project, fqnStr, searchScope)
    }
}
```

### RubyIncludeExtendCallType

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RubyIncludeExtendCallType`

```
public class RubyIncludeExtendCallType extends FQNCallType {
    public RubyIncludeExtendCallType(String command);  // "include", "extend", "prepend"
    public List<FQN> getCallData(RPossibleCall call);
    public boolean isCompatible(RPossibleCall call);
}
```

**Implementation detail:** `getCallData()` extracts the first argument of the
`include`/`extend`/`prepend` call and resolves it to an FQN. Multiple modules in
one call (`include A, B, C`) produce multiple FQNs.

### RubyIncludeExtendCallType.getCallData() flow

1. Call: `include Authenticatable, Auditable`
2. `getCallData()` resolves each argument to its FQN
3. Returns `[FQN("Authenticatable"), FQN("Auditable")]`

---

## Subtype Discovery Strategies

### Strategy 1: RubyInheritanceIndex (lookup by FQN)

Since `RubyInheritanceIndex.getElements(project, scope, FQN)` returns the element
at that FQN (not subclasses), a direct subtype search requires iterating all
known class names and checking each one's `getSuperClassFQN()`.

**Performance note:** `RubyClassModuleNameIndex` keys are short names, not fully
qualified. To iterate ALL classes, we'd need to enumerate all keys. In practice,
use `StubIndex.getInstance().getAllKeys(RubyClassModuleNameIndex.KEY, project)`
to get all short names, then resolve each and filter by superclass FQN.

### Strategy 2: RubyIncludedExtendedFQNIndex (mixins only)

For module mixins specifically, `getOnIncludedElements(project, scope, moduleFqn)`
is O(1) and returns all classes/modules that include the given module. This
is the ideal path for mixin-based "subtypes" of a module.

### Strategy 3: RubyDeclarationHierarchyIndex (file-based)

**Path:** `org.jetbrains.plugins.ruby.ruby.lang.psi.indexes.RubyDeclarationHierarchyIndex`

```
public final class RubyDeclarationHierarchyIndex
    extends FileBasedIndexExtension<String,
        List<Pair<RubyDeclarationHierarchyIndex.Type, String>>> {

    public static boolean processAllValues(
        Project, SearchScope,
        FQN fqn,
        FileBasedIndex.ValueProcessor<List<Pair<Type, String>>>
    );
}
```

File-based index with complex value types. `processAllValues()` can iterate
hierarchy data, but the value format is opaque (`Type` enum + String values).
Lower priority — use `RubyInheritanceIndex` + `RubyClassModuleNameIndex` first.

### Strategy 4: RubyIncludedExtendedFQNIndex for class subtypes

**Hidden insight:** The `RubyIncludedExtendedFQNIndex` also handles `extend`
calls. `getOnExtendedElements(project, scope, classFQN)` returns classes that
extend the given class. In Ruby, `extend SomeClass` is uncommon but valid.

---

## Implementation Plan

### New reflection methods for `BaseRubyHandler`

```kotlin
// ── FQN resolution ────────────────────────────────────────────────
protected fun resolveRContainerByFQN(
    project: Project, fqnStr: String, searchScope: GlobalSearchScope
): PsiElement?

// ── Superclass ────────────────────────────────────────────────────
protected fun rClassGetSuperClassFQN(element: PsiElement): String?

// ── Mixins (include/extend/prepend) ────────────────────────────────
protected fun getIncludedModuleFQNs(element: PsiElement): List<String>

// ── Subtypes ──────────────────────────────────────────────────────
protected fun findSubtypesByFQN(
    project: Project, element: PsiElement, searchScope: GlobalSearchScope
): List<PsiElement>

// ── Mixin reverse subtype ─────────────────────────────────────────
protected fun findMixinsOfElement(
    project: Project, fqnStr: String, searchScope: GlobalSearchScope
): List<PsiElement>
```

### RubyTypeHierarchyHandler flow

```
getTypeHierarchy(element, project, scope, excludeGenerated)
  │
  ├─ findContainingRClassOrRModule(element) -> rContainer
  │     (handles both RClass and RModule, walks PSI tree)
  │
  ├─ getSupertypes(project, rContainer, visited, depth, searchScope)
  │     │
  │     ├─ IF rContainer is RClass →
  │     │     getSuperClassFQN() -> FQN
  │     │     resolveRContainerByFQN(project, fqnStr, scope) -> PsiElement?
  │     │     → Recurse with depth+1, visited set
  │     │
  │     ├─ getIncludedModuleFQNs(rContainer) -> List<String>
  │     │     Uses processCallsOfType(INCLUDE_CALL) via reflection
  │     │     → Resolve each FQN
  │     │
  │     └─ getIncludedModuleFQNs(rContainer) -> List<String>
  │           Uses processCallsOfType(EXTEND_CALL) via reflection
  │           → Resolve each FQN
  │
  ├─ getSubtypes(project, rContainer, searchScope)
  │     │
  │     ├─ Get element's own FQN: getFQN().getFullPath()
  │     │
  │     ├─ Iterate RubyClassModuleNameIndex keys to find subclasses
  │     │     For each name, resolve and check getSuperClassFQN()
  │     │
  │     └─ RubyIncludedExtendedFQNIndex.getOnIncludedElements()
  │           → Classes that include this module
  │
  └─ Build TypeHierarchyData
        element = TypeElementData for rContainer
        supertypes = recursive TypeElementData list
        subtypes = flat TypeElementData list
```

### Guardrails

| Guard | Value | Why |
|-------|-------|-----|
| Max depth | 50 | Prevent stack overflow on cyclic or very deep hierarchies |
| Visited set | `MutableSet<String>` | Prevent infinite loops (Ruby allows `A < B < A`? No, but modules can include each other) |
| Max subtypes | 100 | Same limit as other language handlers |
| Scope filter | `shouldIncludeNavigationElement` | Respect scope (project files, libraries, etc.) |
| Null name guard | Skip if `getName()` or FQN is null | Anonymous classes / edge cases |

---

## Mirroring PythonTypeHierarchyHandler

The implementation should mirror `PythonTypeHierarchyHandler` (in
`PythonHandlers.kt`) as closely as possible:

| Aspect | Python | Ruby |
|--------|--------|------|
| Base handler | `BasePythonHandler` | `BaseRubyHandler` (already exists) |
| Supertype method | `getSuperClasses(pyClass)` via reflection | `rClassGetSuperClassFQN()` + `getIncludedModuleFQNs()` |
| Subtype method | `PyClassInheritorsSearch.search(pyClass)` via reflection | `findSubtypesByFQN()` via `RubyClassModuleNameIndex` iteration + `RubyIncludedExtendedFQNIndex` |
| Kind | Always `"CLASS"` | `"CLASS"` for `RClass`, `"MODULE"` for `RModule` |
| Language | `"Python"` | `"Ruby"` |
| Max depth | `MAX_HIERARCHY_DEPTH = 50` | Same |
| Visited set | `MutableSet<String>` of FQNs | Same |
| Scope | `createNavigationSearchScope()` | Same (from LanguageHandler API) |
| Filter | `shouldIncludeNavigationElement()` | Same (from ResultVisibilityFilter) |

## Risks & Open Questions

1. **`getSuperClassFQN()` on top-level classes:** Object inherits from nothing
   (or itself). `Object.getSuperClassFQN()` should return null — verify.

2. **RClass.getFQN() vs getQualifiedName():** `RClass` inherits `getFQN()` from
   `RElementWithFQN` (returns `FQN` object). `RModule` has `getQualifiedName()`
   (returns `String` directly). Both need reflection-based access.

3. **Kotlin object field access:** `RubyIncludeExtendCallTypes.INCLUDE_CALL` is
   a Kotlin `val` compiled as a static field. Access via
   `RubyIncludeExtendCallTypes::class.java.getField("INCLUDE_CALL").get(null)`.

4. **`processCallsOfType` return type:** The consumer is
   `com.intellij.util.Consumer<RCall>` where `RCall`
   (`RPossibleCall` → `RPsiElement`). `getCallData()` is on `RubyCallType`,
   returns `List<FQN>`. Access chain: get the `callData` property from the
   call type, not from the call itself.

5. **Cycle scenarios:** Ruby allows:
   ```ruby
   module A
     include B
   end
   module B
     include A   # runtime error, but PSI structure allows it
   end
   ```
   The visited set handles this.

6. **Anonymous classes:** `Class.new { ... }` creates anonymous PSI elements
   with null names. Our handler should skip these gracefully.