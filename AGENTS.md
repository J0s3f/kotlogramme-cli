# AGENTS.md

Guidance for AI agents (and humans) working in this repository. It adapts the general engineering
rules used across J0s3f's projects to this application.

Code is clean if it can be understood easily, by everyone on the team. Clean code can be read and
enhanced by a developer other than its original author. With understandability comes readability,
changeability, extensibility and maintainability.

## What this project is

`kotlogramme-cli` is a command-line Telegram client written in Kotlin. It is the reference
consumer of the [`kotlogramme`](https://github.com/J0s3f/kotlogram) facade, which wraps the
grammers Rust client behind a Kotlin/JVM API and bundles the native libraries.

Two goals, in order:

1. Be a genuinely useful, scriptable Telegram client for the terminal.
2. Exercise the whole `kotlogramme` surface so its gaps are found by a real application.

It is modelled on other terminal clients, whose feature sets are the yardstick:

- [`tgt`](https://github.com/FedericoBruzzone/tgt) — a Rust TUI over TDLib.
- [`nchat`](https://github.com/d99kris/nchat) — a mature multi-protocol ncurses client.
- [`telegram-console`](https://github.com/mxmkhv/telegram-console) — a minimal Node client.
- [`tg`](https://github.com/paul-nameless/tg) — a small scriptable client.

## Environment and commands

The primary development machine is Windows; use PowerShell. CI runs on Linux.

- Use **absolute paths** for every file operation. Relative paths in .NET/file APIs resolve against
  the process working directory, not PowerShell's location, which has silently edited the wrong
  checkout before.
- The toolchain is the newest LTS JVM (**JDK 25**, Temurin), the newest stable Kotlin (**2.4.20**)
  and **Gradle 9.8**. This application is not a library anyone links against, so it takes the
  bleeding edge; do not copy the conservative versions the facade is pinned to. The JDK is not on
  `PATH`; set it per shell:
  `$env:JAVA_HOME="C:\Users\micro\.jdks\temurin-25"`.
- Build and test with the wrapper:

  ```
  .\gradlew.bat --no-daemon test
  ```

  Gradle resolves the JDK 25 toolchain from the installed JDKs; the foojay resolver in
  `settings.gradle.kts` can fetch it on a machine that lacks it.
- Run the client locally with `.\gradlew.bat run --args="..."` or `./gradlew installDist` and the
  generated `build/install/kotlogramme/bin/kotlogramme`.
- The dependency is `io.github.j0s3f:kotlogramme` (default `0.7.0`, the current Maven Central
  release). Override with `-PkotlogrammeVersion=...` to test a different one.
- Capture full command output to a temp file when you pipe it (`... | Tee-Object -FilePath $env:TEMP\x.log | Select-Object -Last 40`),
  so diagnosing a failure later does not require a rerun.

### Tests and secrets

- **Never** run live Telegram tests as part of the normal loop. Anything that would contact
  Telegram (login, sending, receiving) must sit behind a port and be exercised with an in-memory
  fake. Real credentials never belong in the repository or in chat.
- The default `test` task must be fast, offline and deterministic.

## Architecture

The code follows a hexagonal (ports and adapters) architecture. Dependencies point inwards: the
domain and application know nothing about Telegram, the terminal, or the filesystem.

```
org.kotlogramme.cli
├── domain                  # entities and value objects: Chat, Message, Account, Session
├── application
│   ├── port.api            # inbound ports: use cases the outside world can invoke
│   ├── port.spi            # outbound ports: what the application needs (TelegramGateway, ConfigStore, Clock)
│   └── service             # use-case implementations, one class per use case or cohesive group
├── adapter
│   ├── telegram            # kotlogramme-backed implementation of TelegramGateway
│   ├── config              # file-backed ConfigStore and session persistence
│   ├── cli                 # Clikt commands and the interactive shell (the entry point)
│   └── format              # rendering DTOs and domain objects as tables, JSON or plain text
└── Main.kt
```

Rules that follow from this:

- A use case depends on ports, never on `TelegramClient` or `System.out` directly.
- Every outbound port has at least two implementations in tests: the real adapter and a fake.
- Data crosses layers as domain types, not as `kotlogramme` models or raw JSON.
- Keep configurable data (paths, timeouts, API id/hash, output mode) at the composition root,
  read once and passed down.

## General rules

1. Follow standard conventions.
2. Keep it simple stupid. Simpler is always better. Reduce complexity as much as possible.
3. Boy scout rule. Leave the campground cleaner than you found it.
4. Always find root cause. Always look for the root cause of a problem.

## Design rules

1. Keep configurable data at high levels.
2. Prefer polymorphism to `if`/`else` or `switch`/`case`.
   ```kotlin
   // Avoid this
   fun describe(kind: String): String = when (kind) {
       "private" -> "private chat"
       "group" -> "group chat"
       else -> "unknown"
   }

   // Prefer this
   fun describe(kind: ChatKind): String = kind.description
   ```
3. Separate multi-threading code. The Telegram client is not thread-safe; touch it from one place
   (the gateway), and hand results back through the ports.
4. Prevent over-configurability.
5. Use dependency injection. Constructor injection everywhere; no singletons or global state.
   ```kotlin
   // Avoid this
   class SendMessage { private val gateway = TelegramGatewayImpl() }

   // Prefer this
   class SendMessage(private val gateway: TelegramGateway)
   ```
6. Follow the Law of Demeter. A class should know only its direct dependencies.

## Understandability tips

1. Be consistent. If you do something a certain way, do all similar things the same way.
2. Use explanatory variables.
3. Encapsulate boundary conditions in one place.
   ```kotlin
   // Avoid this
   if (messages.size > pageSize) { /* ... */ }

   // Prefer this
   val hasMore = messages.size > pageSize
   ```
4. Prefer dedicated value objects to primitive types.
   ```kotlin
   // Avoid this
   fun findChat(dcId: Int, chatId: Long, accessHash: Long): Chat

   // Prefer this
   fun findChat(id: ChatId): Chat
   ```
5. Avoid logical dependency: a method should not depend on unrelated state in the same class.
6. Avoid negative conditionals.
   ```kotlin
   // Avoid this
   if (!session.isAuthorized) { login() }

   // Prefer this
   if (session.isAnonymous) { login() }
   ```

## Names

1. Choose descriptive and unambiguous names.
2. Make meaningful distinctions.
3. Use pronounceable names.
4. Use searchable names.
5. Replace magic numbers with named constants.
6. Avoid encodings. Do not append prefixes or type information.

## Functions

1. Small.
2. Do one thing.
3. Use descriptive names.
4. Prefer fewer arguments.
5. Have no side effects.
6. Do not use flag arguments; split the method into several methods the client can choose between.

## Comments

1. Always try to explain yourself in code.
2. Do not be redundant.
3. Do not add obvious noise.
4. Do not use closing-brace comments.
5. Do not comment out code; delete it.
6. Use comments to explain intent, clarify, or warn of consequences.
7. Do not describe ongoing changes or implementation steps. Comments are permanent explanations of
   complex logic or design choices, focused on *why*, not *what*.

## Source code structure

1. Separate concepts vertically.
2. Related code should appear vertically dense.
3. Declare variables close to their usage.
4. Dependent functions should be close.
5. Similar functions should be close.
6. Place functions in the downward direction.
7. Keep lines short.
8. Do not use horizontal alignment.
9. Use whitespace to associate related things and disassociate weakly related ones.
10. Do not break indentation.

## Objects and data structures

1. Hide internal structure.
2. Prefer data structures.
3. Avoid hybrids: a type is either an object with behaviour or a plain data holder.
4. Keep them small and focused on one thing.
5. Keep the number of instance variables low.
6. A base class knows nothing about its derivatives.
7. Prefer many functions to passing a selector that chooses behaviour.
8. Prefer non-static methods, especially when dependencies or state are involved; this is what keeps
   the code testable and injectable.

## Tests

1. One logical concept per test. Validate a single, specific behaviour; multiple assertions are fine
   when they all serve that concept.
2. Readable.
3. Fast.
4. Independent.
5. Repeatable.

## Code smells, never do the following

1. Rigidity: a small change causes a cascade of changes.
2. Fragility: the software breaks in many places due to a single change.
3. Immobility: parts cannot be reused without high risk or effort.
4. Needless complexity.
5. Needless repetition.
6. Opacity: code that is hard to understand.

## Kotlin conventions

- Standard Kotlin naming: `PascalCase` types, `camelCase` functions and properties,
  `SCREAMING_SNAKE_CASE` constants.
- Prefer immutable `val`, `data class`, sealed hierarchies and expression bodies.
- Use the Kotlin stdlib collection functions over manual loops.
- Match the style of the existing code; `ktlint`-friendly formatting, 4-space indentation,
  120-column lines.
- `when` over a sealed type must be exhaustive (the compiler enforces it; do not add an `else`).

## Development workflow and commits

This project follows Test-Driven Development. Every new piece of functionality is covered by tests
from the outset. Keep changes small, logical and committed frequently.

### TDD cycle: red-green-refactor

1. **Red — write a failing test.** Before any implementation, write a small automated test for one
   piece of new functionality. It must fail for the right reason. Committing at this stage with
   `test: add failing test for …` is acceptable.
2. **Green — make it pass.** Write the *absolute simplest* production code that makes the test pass.
   Do not gold-plate.
3. **Refactor — improve the code.** With the test passing, remove duplication and align with the
   clean-code rules, then re-run the tests. The feature commit is made here, after the code is
   clean and the tests are green, bundling test and implementation.

### Commit rules

- **Verify before committing.** The project must compile and all tests must pass. Never commit broken
  code. A full `clean test` is required for milestone commits and after any build, dependency or
  tooling change; the fast incremental loop is enough for a single red-green-refactor step.
- **Message formatting.** Imperative mood ("Add friend code lookup", not "Added …"). For a new file,
  reflect its creation; for a modification, describe the change. Conventional prefixes
  (`feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`) are welcome.
- Stage by explicit path. Run `git update-index --really-refresh` and confirm `git diff --stat` is
  empty before committing.
- **Never** push, rebase or amend on an agent task branch unless the orchestrator asked for it.

### Agent rules

- Work only inside your assigned worktree/branch. Never touch another agent's files or the main
  worktree.
- Use absolute paths for every file operation and for `git -C <worktree> …`.
- Always find root cause. If a command fails, read the error and change approach; never re-run the
  same failing command twice.
- Prefer searching and reading files over guessing. Do not infer tool or API names.
- If genuinely blocked, stop and report the exact error rather than guessing.

## Documentation workflow

Keep the documentation current:

1. **`docs/features.md`** — after implementing a user-facing feature, add an entry describing it from
   the user's perspective.
2. **Design decisions** — after a significant design decision (new library, architectural pattern,
   non-obvious choice), record the decision *and its rationale*, including what else was considered
   and why it lost, in `docs/decisions.md`.
3. **Status** — after a major feature, update `README.md` so it reflects what actually ships, not
   what was planned.
4. **`CHANGELOG.md`** — every release adds an entry under the new version.
5. **`AGENTS.md`** — general rules for the agent. Update it when the user gives new general guidance.

## Dependencies and third-party notes

- `kotlogramme` bundles the native libraries and loads them from its own jar. If you run from a
  composite or source build, you can point at a local library with
  `-Dkotlogramme.native.path=<path to kotlogramme.dll/.so/.dylib>`.
- Prefer the standard library and the dependencies already declared: Clikt 5 (command line),
  Mordant 3 (terminal rendering, transitively via Clikt), JLine 4 (the interactive shell) and
  kotlinx.serialization 1.11 (config and JSON output). Adding a dependency is a design decision that
  needs a `docs/decisions.md` entry.
