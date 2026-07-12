# RubySymbolReferenceHandler.resolveSymbol — Live IDE Test Procedures

## Overview

These procedures verify the live behavior of `RubySymbolReferenceHandler.resolveSymbol()` when called via the intellij-index MCP server.

**Files**:
- Implementation: `src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/handlers/ruby/RubySymbolReferenceHandler.kt`
- Platform test: `src/test/kotlin/.../handlers/ruby/RubySymbolReferenceHandlerTest.kt`
- Platform test fixtures: Created inline via `myFixture.addFileToProject()` in tests
- Unit tests: `src/test/kotlin/.../handlers/ruby/RubySymbolReferenceHandlerUnitTest.kt`

## Prerequisites

1. **Ruby plugin installed** (`org.jetbrains.plugins.ruby` in `gradle.properties` platformPlugins)
2. **MCP server running** (`mvn -pl jetbrains-index-mcp-plugin compile` then start MCP server)
3. **IntelliJ/IDEA with RubyMine** (Ruby plugin required for symbol resolution)

## Test Procedure: resolveSymbol Strategy Verification

### Goal

Verify that `RubySymbolReferenceHandler.resolveSymbol()` successfully resolves Ruby classes, modules, and methods using the new strategy order:
1. RubyGotoClassContributor (primary)
2. RubyClassModuleNameIndex + FQN filter (fallback)
3. RubyInheritanceResolutionIndex (final fallback)

### Test Cases

#### T1: Bare Class Name (Simple)

```bash
# In Ruby file:
puts User
```

**Expected**: Resolves to `RClass` element, name = "User"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User"`
- Verify success
- Check element type is `RClass`

---

#### T2: Namespaced Class

```ruby
module Admin
  class User
  end
end

# In Ruby file:
puts Admin::User
```

**Expected**: Resolves to `RClass` element, FQN = "Admin::User"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "Admin::User"`
- Verify success
- Verify `getQualifiedName()` returns "Admin::User"

---

#### T3: Deeply Namespaced Class

```ruby
module A
  module B
    module C
      class User
      end
    end
  end
end

# In Ruby file:
puts A::B::C::User
```

**Expected**: Resolves to `RClass` element, FQN = "A::B::C::User"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "A::B::C::User"`
- Verify success
- Verify element name and FQN match

---

#### T4: Instance Method

```ruby
class User
  def admin?
  end
end

# In Ruby file:
User#admin?
```

**Expected**: Resolves to `RMethod` element, name = "admin?"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User#admin?"`
- Verify success
- Verify element type is `RMethod`

---

#### T5: Class Method (Dot Notation)

```ruby
class User
  def self.find_by_email
  end
end

# In Ruby file:
User.find_by_email
```

**Expected**: Resolves to `RMethod` element (class-level)

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User.find_by_email"`
- Verify success
- Verify element type is `RMethod`

---

#### T6: Module Only

```ruby
module Authenticatable
end

# In Ruby file:
puts Authenticatable
```

**Expected**: Resolves to `RModule` element, name = "Authenticatable"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "Authenticatable"`
- Verify success
- Verify element type is `RModule`

---

#### T7: Underscored Names (Gem-Style)

```ruby
module MyGem
  class MyClass
  end
end

# In Ruby file:
puts MyGem::MyClass
```

**Expected**: Resolves to `RClass` element, FQN = "MyGem::MyClass"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "MyGem::MyClass"`
- Verify success
- Verify FQN matches

---

#### T8: Setter Method

```ruby
class User
  def name=(val)
  end
end

# In Ruby file:
User#name=
```

**Expected**: Resolves to `RMethod` element

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User#name="`
- Verify success
- Verify element type is `RMethod`

---

#### T9: Non-Existent Class

```ruby
# No User class in project
puts User
```

**Expected**: Returns failure with message containing "could not be resolved"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User"`
- Verify `isFailure == true`
- Verify error message contains "could not be resolved"

---

#### T10: Non-Existent Method

```ruby
class User
  def empty_method
  end
end

# Method name is different
User#nonexistent
```

**Expected**: Returns failure with message mentioning "not found" or "could not be resolved"

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User#nonexistent"`
- Verify `isFailure == true`

---

#### T11: Method in Namespaced Class

```ruby
module Admin
  class User
    def admin?
    end
  end
end

# In Ruby file:
Admin::User#admin?
```

**Expected**: Resolves to `RMethod` element

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "Admin::User#admin?"`
- Verify success
- Verify element type is `RMethod`

---

#### T12: Bang Method

```ruby
class User
  def save!
  end
end

# In Ruby file:
User#save!
```

**Expected**: Resolves to `RMethod` element

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "User#save!"`
- Verify success
- Verify element type is `RMethod`

---

#### T13: Symbol with Leading Whitespace

```ruby
class User
  def find_by_email
  end
end

# Leading whitespace:
  User#find_by_email
```

**Expected**: Trims whitespace, resolves successfully

**Verification**:
- Call `ide_resolve_symbol` with `language: "Ruby", symbol: "  User#find_by_email"`
- Verify `isSuccess == true`

---

## Run Platform Tests

```bash
# Run all RubySymbolReferenceHandler platform tests
./gradlew test --tests "*RubySymbolReferenceHandlerTest*"

# Run specific test (requires Ruby plugin)
./gradlew test --tests "*RubySymbolReferenceHandlerTest.testResolveBareClassName*"

# Run on CI (tests skipped if Ruby plugin unavailable)
# CI runs full test suite with proper CI environment
```

**Expected result**: 13/13 tests pass (CI only — unit tests run locally on machines without Ruby plugin)

---

## Verification Checklist

| Step | Command | Expected |
|---|---|---|
| Unit tests | `./gradlew test --tests "*RubySymbolReferenceHandlerUnitTest*"` | 29/29 pass |
| Platform tests | `./gradlew test --tests "*RubySymbolReferenceHandlerTest*"` | 13/13 pass (CI) |
| Resolution order | Check logs for `RubyGotoClassContributor lookup failed` | May see warning if no-arg constructor missing |
| RubyGotoClassContributor | Verify it's called before stub indexes in logs | Look for "Strategy 1: RubyGotoClassContributor" |
| FQN extraction | Test T2/T3 — verify `getQualifiedName()` returns correct FQN | No errors |
| Error messages | Test T9/T10 — verify proper error messages | "could not be resolved" or "not found" |

---

## Troubleshooting

**Issue**: Tests fail with "Ruby plugin not available"

**Fix**: Add to `gradle.properties`:
```
platformPlugins=org.jetbrains.plugins.ruby:253.31033.53
```

**Issue**: Tests pass locally but fail on CI

**Fix**: CI should have Ruby plugin configured. Check CI logs for skipped test warnings.

**Issue**: `RubyGotoClassContributor lookup failed` in logs

**Explanation**: If RubyGotoClassContributor has no no-arg constructor, the code catches the exception and falls back to stub indexes. This is expected behavior.

**Issue**: Symbol resolution fails for gem classes (e.g., `ActiveRecord::Base`)

**Explanation**: Requires `allScope` fallback. The code already tries `projectScope` then falls back to `allScope` in Strategy 2.

---

## Next Steps (Optional)

From original resolveSymbol plan:

1. **Ancestor walk for inherited methods** — When `findMethodByName()` doesn't find method, walk superclass/included modules
2. **Enhanced error messages** — Add concrete next steps for users
3. **FQN extraction simplification** — Use `getQualifiedName()` directly instead of multi-strategy fallback

These are extensions, not requirements for initial implementation.