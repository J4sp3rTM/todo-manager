# TODO Manager

A JetBrains IDE plugin that turns scattered `TODO`, `FIXME`, `HACK`, `NOTE`, and `XXX`
comments in your code — in **any language** — into a structured, editable to-do list, with a
dedicated tool window, editor highlighting, and two-way editing.

It understands a structured comment format:

```java
// TODO [tag] (priority) description
// FIXME [auth] (high) token refresh races on logout
```

## Features

- **Tool window** with a tree view of every TODO in the project, grouped by **file**, **tag**, or **priority**.
- **Bidirectional editing** — change a TODO's description, tag, or priority from the panel
  and the source comment is rewritten in place, with full undo support.
- **Editor highlighting** — keywords, tags, priorities, description text, and comment delimiters are colorized right in the editor.
- **Add & complete** — insert new TODOs at the caret position, or mark existing ones done.
  Completing a TODO stamps it with your git user name and the date
  (e.g. `DONE fix login (done by J4sp3r on 2026-06-29)`).
- **Done items, kept in view** — completed TODOs stay in the list, struck through and labelled
  with who finished them and when. Toggle them on or off with **Show done** in the toolbar.
- **General (code-free) TODOs** — track items that aren't tied to any comment. Check
  *General TODO* in the New TODO dialog; they're stored with the project and shown alongside
  code TODOs.
- **Fully configurable** — customize keywords and all colors under *Settings > Tools > TODO Manager*.
- **Auto-refresh** as you edit files.

## Comment format

| Part | Syntax | Example | Notes |
|------|--------|---------|-------|
| Keyword | `TODO`, `FIXME`, … | `TODO` | Configurable list |
| Tag | `[tag]` | `[auth]` | Optional |
| Priority | `(priority)` | `(high)` | `critical` / `high` / `medium` / `low`, optional |
| Description | free text | `fix login` | |

Works in any comment the IDE recognizes — line comments (`//`, `#`, `--`), block comments
(`/* */`, `<!-- -->`), and doc comments — across every supported language.

## Requirements

- Any IntelliJ-based IDE **2025.1+** (IntelliJ IDEA, WebStorm, PyCharm, GoLand, PhpStorm,
  Rider, CLion, RubyMine, Android Studio, …).

## Building from source

```bash
./gradlew buildPlugin
```

The installable zip is written to `build/distributions/`.

## License

[MIT](LICENSE) © J4sp3r
