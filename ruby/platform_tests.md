# Platform Test Plan: Installed 2025.3 IDEA Ultimate + Ruby Plugin

Goal: run `RubyTypeHierarchyPlatformTest` (and future Ruby platform tests)
green on a developer machine by pointing the Gradle build at a locally
installed **IntelliJ IDEA Ultimate 2025.3** that has the **Ruby plugin**
installed.

## Why this is required (root cause)

The Ruby plugin is a v2 modular plugin whose main descriptor declares:

```xml
<dependencies>
  <plugin id="com.intellij.modules.ruby-capable"/>
  <plugin id="com.intellij.modules.ultimate"/>
</dependencies>
```

`com.intellij.modules.ultimate` ships **only in IntelliJ IDEA Ultimate**.
When the build downloads the platform via `intellijIdea("2025.3")` it gets a
non-Ultimate IDE that lacks this marker, so:

```
Plugin 'Ruby' (org.jetbrains.plugins.ruby) has dependency on
'com.intellij.modules.ultimate' which is not installed
  -> plugin org.jetbrains.plugins.ruby won't be loaded
  -> all intellij.ruby.* content modules disabled
  -> RubyFileType never registers -> .rb parses as PLAIN TEXT (langId=TEXT)
```

Because `localPlugin(...)` still puts the Ruby jars on the classpath,
`PluginDetectors.ruby.isAvailable` returns `true` (via the fallback
`Class.forName(RClass)` probe) even though the plugin is disabled. The
handler then never resolves for a `.rb` element (its language is `TEXT`,
not `ruby`), and every gated test fails with
"plugin present but handler not wired".

Separate prerequisite already fixed in code: `plugin.xml` must declare
`<depends optional="true" config-file="ruby-features.xml">org.jetbrains.plugins.ruby</depends>`
so our plugin classloader can see Ruby PSI classes (`RClass`, `RMethod`)
via reflection. Without it, `RubyHandlers.register()` throws
`ClassNotFoundException` and registration is silently skipped.

## Version-match rule (do not mix)

The IDE build, `platformVersion`, and the Ruby plugin build must all be on
the same branch (253.x for 2025.3). Pointing `localIdeaPath` at a **2026.1**
IDE while the project targets `platformVersion=2025.3` produces Kotlin
compiler failures ("Incompatible classes ... metadata version") because the
newer IDE's Kotlin/platform classes leak onto the compile classpath.

| Component        | Required value                                   |
|------------------|--------------------------------------------------|
| IDE              | IntelliJ IDEA **Ultimate** 2025.3 (build 253.x)  |
| `platformVersion`| `2025.3` (unchanged, in `gradle.properties`)     |
| Ruby plugin      | 2025.3 build `253.31033.53`                      |
| Build JDK        | JDK 17+ (Gradle requirement); JBR runs the tests |

## Setup steps

1. Install **IntelliJ IDEA Ultimate 2025.3** (build 253.x). A 2025.3 config
   dir already exists at
   `~/Library/Application Support/JetBrains/IntelliJIdea2025.3/`; the app
   itself must be a 253.x Ultimate build (not 2026.x).
2. Install the **Ruby plugin** into that IDE. It lands at
   `~/Library/Application Support/JetBrains/IntelliJIdea2025.3/plugins/ruby`
   (verified build `253.31033.53`).
3. Configure `~/.gradle/gradle.properties` (build.gradle.kts reads both):

   ```properties
   # Use the installed Ultimate IDE as the test platform so
   # com.intellij.modules.ultimate is present and the Ruby plugin loads.
   localIdeaPath=/path/to/IntelliJ IDEA 2025.3 Ultimate.app
   # Version-matched Ruby plugin (253.x) added via localPlugin(...).
   localRubyPluginPath=/Users/dueberb/Library/Application Support/JetBrains/IntelliJIdea2025.3/plugins/ruby
   ```

   When `localIdeaPath` is set, the build uses `local(localPath)` and skips
   the Marketplace/download path; `localRubyPluginPath` is added as a
   `localPlugin`.

## Execution

```bash
export JAVA_HOME="$(mise where java@17)"   # any JDK 17+
./gradlew test --tests "*RubyTypeHierarchyPlatformTest*"
```

## Verification / diagnostics

* Success: the sandbox log shows Ruby content modules loading (no
  "won't be loaded" lines), and a `.rb` fixture reports `langId='ruby'`.
* Sandbox log:
  `build/idea-sandbox/<IDE>/log-test/idea.log` — grep for
  `com.intellij.modules.ultimate` and `org.jetbrains.plugins.ruby`.
* If tests still fail with "handler not wired", confirm the fixture file
  language is `ruby` (not `TEXT`); `TEXT` means the plugin is still disabled
  (wrong IDE edition or version mismatch).

## Notes

* Downgrading a daily-driver IDE is **not** required. Only the build's test
  platform must be a 2025.3 Ultimate; a side-by-side install (or Toolbox
  version) pointed at by `localIdeaPath` is sufficient.
* CI (which leaves `localIdeaPath` unset and downloads a non-Ultimate IDE)
  cannot load the Ruby plugin; these platform tests are expected to be
  skipped/guarded there and run locally against Ultimate.

---

# Platform Tests for Ruby Handlers

> Tests that extend `BasePlatformTestCase` and require the Ruby plugin on
> the classpath. Skipped automatically on CI or machines without RubyMine.
>
> Guard pattern: `assumeTrue("Requires Ruby plugin", PluginDetectors.ruby.isAvailable)`
>
> Run: `./gradlew test --tests "*RubyTypeHierarchyPlatformTest*"`
>
> See: `src/test/kotlin/.../handlers/ruby/RubyTypeHierarchyPlatformTest.kt`

## TypeHierarchy

### Existing

Tests already written in `RubyTypeHierarchyPlatformTest`:

| Test | What it covers |
|---|---|
| `testSimpleInheritance` | `class Dog < Animal` — basic single-inheritance supertype link |
| `testIncludedModulesAppearAsSupertypes` | `include Authenticatable` — module mixin as supertype |
| `testModuleOnly` | `module Auditable` — module as hierarchy root |
| `testNamespacedClass` | `Admin::User` inside `module Admin` — FQN reconstruction |
| `testDeepChain` | `Child < Parent < GrandParent` — recursive supertype traversal |
| `testFindsSubtypes` | Inverse: find `Dog`, `Cat` from `Animal` via index |
| `testMethodInsideClassFindsContainingClass` | Method-level PSI resolves to enclosing `RClass` |

### To add

| Test | What it should cover |
|---|---|
| `testExtendModule` | `extend M` — `extend` creates class-level mixin, should appear as supertype |
| `testClassWithBothSuperclassAndModules` | `class C < Parent; include M1; include M2` — mixed hierarchy |
| `testModuleIncludingAnotherModule` | `module B; include A` — modules including modules |
| `testNestedModules` | Pathological: `module A; module B` — hierarchy of inner module |
| `testRootObjectSupertype` | `class C; end` — implicit superclass (Object) is null but should not error |
| `testFileWithMultipleClasses` | Two top-level classes in one file — both resolvable independently |
| `testClassWithNoBody` | `class Foo; end` — empty class, minimal element |
| `testInheritsFromStandardLibrary` | `class MyString < String` — stdlib superclass (requires scope=all) |
| `testSelfReferenceGuard` | Class extending itself? Unlikely in Ruby but guards against infinite recursion |
| `testSubtypeLimitRespected` | 101 classes extending same parent — only 100 subtypes returned |
| `testHandlerRejectsNonRubyElement` | `someJavaMethod` passed to handler — returns null |
| `testGetTypeHierarchyOnUnavailableHandler` | Handler with no Ruby plugin — returns null gracefully |
