# Ruby Index Research: Remaining Unknowns — Empirical Test Plan

**Status:** Updated 2026-07-12 after `javap` decompilation of Ruby plugin JARs.

Several claims in earlier documentation (`03-ruby-psi-reference.md`) have been **falsified** by decompilation. These are now resolved and removed from the unknowns list. What remains are questions that **cannot be answered by bytecode inspection** — they require runtime empirical testing inside a live IDE with real Ruby fixtures.

---

## Resolved by Decompilation (no longer unknowns)

| # | Question | Answer | Method |
|---|----------|--------|--------|
| 1 | Does `getIncludedModules()` exist on RClass? | **NO** — not on RClass, RClassBase, or RClassImpl | `javap -p` of all three classes |
| 2 | What's the correct `processCallsOfType` Consumer type? | `Consumer<RCall>` (not `Consumer<RPossibleCall>`) | `javap -p RClassBase` |
| 3 | Can `RubyIncludedExtendedFQNIndex` resolve normal includes? | **NO** — `sink()` only indexes `DELEGATE_HOOK_FQN` | `javap -p RubyIncludedExtendedFQNIndex` |
| 4 | What's the key type for `RubyDeclarationFqnIndex`? | `Type` enum with values `CLASS`, `MODULE`, `CONSTANT` | `javap -p RubyDeclarationFqnIndex$Type` |
| 7 | Does `RubyClassResolveUtil.resolveIncludedModules()` exist? | **NO** — not present on this class | `javap -p RubyClassResolveUtil` |

Additionally, **new methods were discovered** in `RubyClassResolveUtil` that change our approach:
- `findCall(RClass, Predicate<RubyCallType<?>>) -> RPossibleCall` — **promising alternative** for include resolution
- `getQualifiedName(RClass) -> String` — simpler FQN access

---

## Remaining Unknowns (require empirical testing)

### Unknown 1: Does `processCallsOfType` actually invoke the Consumer via reflection?

**Why decompilation can't answer this:** The method signature is confirmed, but runtime behavior depends on method handle lookup, type erasure bridging, and thread context. The Consumer might not be called because:
- The Java lambda-to-Consumer bridge requires exact type matching at the JVM level
- `ReadAction` / EDT requirements might prevent execution
- The `INCLUDE_CALL` field access might return a different object than expected

**Empirical test:**
1. Load `RClass` element from fixture `user.rb`
2. Log whether `processCallsOfType` method exists and its exact parameter types
3. Log the `INCLUDE_CALL` field value and its class
4. Try `processCallsOfType` with `Consumer<RCall>` (cast to `Consumer<Object>`) — run both inside and outside `ReadAction`
5. Count invocations received by the Consumer

### Unknown 2: Does `RubyClassResolveUtil.findCall()` work at runtime?

**Why decompilation can't answer this:** It's a public static method with the right signature on paper. But its behavior on real PSI elements, whether it properly matches include calls, and whether it works in `ReadAction` can only be tested in a live IDE.

**Empirical test:**
1. Load `RubyClassResolveUtil` via reflection
2. Load `RubyIncludeExtendCallTypes` and `RubyIncludeExtendCallType`
3. Call `findCall(class, predicate)` where predicate matches `type -> type == INCLUDE_CALL`
4. If a call is returned, call `RubyIncludeExtendCallType.getCallData(call)` to extract FQNs
5. Log success/failure and the number of FQNs found
6. If it works, this becomes the **preferred path** for include resolution

### Unknown 3: Does `RubyInheritanceResolutionIndex$ForSuperClasses` return transitive or direct subclasses?

**Why decompilation can't answer this:** The `sink()` method writes keys during indexing, but the runtime `FQN` value determines search scope. Whether querying key=`Animal` returns only direct children (`Dog`) or also grandchildren (`Poodle`) depends on what FQNs are stored.

**Empirical test:**
1. Create a 3-level chain: `GrandParent < Parent < GrandChild`
2. Query `ForSuperClasses` with `GrandParent`'s FQN
3. Report whether `Parent` (direct) and/or `GrandChild` (transitive) appear in results
4. Repeat with `Parent`'s FQN — expect only `GrandChild`

### Unknown 4: Does the PSI text walk regex handle edge cases?

**Why decompilation can't answer this:** The regex `\binclude\s+([A-Z][A-Za-z_:]*)\b` is pure string matching. Its behavior on real source text depends on formatting, encoding, and nesting — only testable against actual Ruby files.

**Empirical test:**
1. Create fixture file with edge cases:
   - Multi-line: `include\n  ModuleName`
   - `include` + `prepend` in same class
   - Dynamic: `include module_var`
   - Nested scope: `class A; class B; include M; end; end`
2. Run `getIncludedModuleFQNsViaPsiTextWalk()` on the class
3. Report which cases are resolved correctly and which are missed

### Unknown 5: Are `attr_accessor`/`define_method` methods in `RubyMethodNameIndex`?

**Why decompilation can't answer this:** Index population depends on stub-building logic that runs at IDE index time. The stubs for automatically generated methods may or may not have their method names indexed. This is not inspectable from bytecode.

**Empirical test:**
1. Create fixture file with:
   - `attr_accessor :name, :email` in a class
   - `define_method(:foo) { ... }` in the same class
2. Query `RubyMethodNameIndex` for `name`, `name=`, `email`, `email=`, `foo`
3. Report which keys exist and how many results each returns

### Unknown 6: Can `RubyDeclarationFqnIndex` be probed with the `Type` enum as key?

**Why decompilation can't answer this:** We confirmed the enum exists with values `CLASS`, `MODULE`, `CONSTANT`. But probing via `FileBasedIndex` with an enum key requires a live IDE — the `FileBasedIndex` API takes `ID<String, *>` but the key type is actually the enum. We need to test what API call works.

**Empirical test:**
1. Load `RubyDeclarationFqnIndex$Type` enum
2. Load `RubyDeclarationFqnIndex` and get its `ID`
3. Try `FileBasedIndex.getInstance().processAllKeys(id, processor, project)` with the raw `ID` (which works for String-based file indexes)
4. Try `FileBasedIndex.getInstance().getAllKeys(id, project)` 
5. Report results and any ClassCastExceptions

---

## Test Fixture Requirements

All test fixtures should go in `ruby/agent_tests/fixtures/`.

| Test | Fixtures needed | Status |
|------|----------------|--------|
| Unknown 1 | `user.rb` with `include Authenticatable` + `authenticatable.rb` | EXISTS |
| Unknown 2 | Same as Unknown 1 | EXISTS |
| Unknown 3 | 3-level chain: `grand_parent.rb`, `parent.rb`, `child.rb` | EXISTS (grand_parent.rb, parent.rb, child.rb) |
| Unknown 4 | Edge cases: multi-line, prepend, dynamic, nested | NEED TO CREATE |
| Unknown 5 | `attr_accessor` + `define_method` class | NEED TO CREATE |

---

## Priority

1. **Unknown 2** (`RubyClassResolveUtil.findCall()`) — highest priority, if it works it unblocks include resolution
2. **Unknown 1** (`processCallsOfType` Consumer) — if findCall fails, this is the fallback
3. **Unknown 3** (transitive vs direct) — affects type hierarchy depth
4. **Unknown 4-6** — secondary issues, affect edge cases and alternative probes