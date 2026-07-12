# Agent Info — Ruby Plugin Integration

## Quick answers: where to look

| Question                            | Look here                                                                                                |
|-------------------------------------|----------------------------------------------------------------------------------------------------------|
| What Ruby PSI classes exist?        | `/ruby/ruby-plugin/lib/modules/` — extracted XMLs from plugin JARs; look for `psi`, `stub`, `index`      |
| What does the Ruby plugin expose?   | Summary in ruby/03-ruby-psi-reference.md. Plugin xml at `ruby/ruby-plugin/plugin.xml`                    |
| Where is the installed Ruby plugin? | `~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/ruby/lib/` (JARs + .class/.rb files) |
| Where is handler registration?      | `src/main/kotlin/.../handlers/LanguageHandlerRegistry.kt` line 284                                       |
| Where is plugin detection?          | `src/main/kotlin/.../util/PluginDetectors.kt` lines 34-37                                                |

## Critical facts

- **Ruby plugin is closed-source** — no source browsing, only reflection +
  extracted XMLs
- **No compile-time dep** — all PSI access via `Class.forName()` +
  `.invoke()` wrapped in `runCatching`
- **Language ID is `"ruby"`** (lowercase) —
  `element.language.id.equals("ruby", true)`
- **Plugin ID**: `org.jetbrains.plugins.ruby`

## Ruby semantics relevant to handlers

| Concept                            | Mapped to                                                            |
|------------------------------------|----------------------------------------------------------------------|
| `class Child < Parent`             | Single superclass via `getSuperClass()`                              |
| `include Module` / `extend Module` | Mixins as "interfaces" via `getIncludedModules()`                    |
| `module Namespace`                 | Also a namespace container (shown as parent in structure)            |
| `super`                            | Override chain via `RubyOverrideImplementUtil` or manual parent walk |
| `User#admin?`, `User#save!`        | Valid method names (predicate/bang suffixes) in symbol patterns      |

