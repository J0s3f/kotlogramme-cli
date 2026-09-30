# kotlogramme-cli

A command-line Telegram client written in Kotlin, built on the
[`kotlogramme`](https://github.com/J0s3f/kotlogram) facade.

It aims to be two things at once: a genuinely useful, scriptable client for the terminal, and the
reference consumer that exercises the whole `kotlogramme` surface so its gaps are found by a real
application.

> **Status: usable.** Phases 1–6 are implemented: authentication, configuration, dialogs, history,
> sending and editing, media, contacts, search, chat members, dialog folders, live updates, and an
> interactive shell. See [`docs/features.md`](docs/features.md) for what ships and
> [`docs/plan.md`](docs/plan.md) for what is left (rights editing, stream uploads, stickers/inline,
> packaging and release).

## Quick start

```bash
kotlogramme config set --api-id <id> --api-hash <hash>   # from my.telegram.org
kotlogramme login --phone +491700000000                  # prompts for the code, then 2FA if set
kotlogramme dialogs --limit 20
kotlogramme history @some_chat --limit 50
kotlogramme send @some_chat "hello from the terminal"
kotlogramme listen                                       # follow new messages
kotlogramme shell                                        # interactive REPL
```

Every command renders as a table by default and can be switched to `--format plain` or `--format
json` (via `config set --format`) for scripting. `kotlogramme doctor` checks the installation,
including that the bundled native library loads.

## Requirements

- **JDK 25** (the newest LTS). Gradle can fetch it automatically via the toolchain resolver.
- Nothing else: the native Telegram libraries are bundled inside the `kotlogramme` jar and the right
  one is loaded for the host at startup.

## Build and run

```bash
./gradlew build          # compile and run the offline test suite
./gradlew installDist    # build the runnable distribution
build/install/kotlogramme/bin/kotlogramme --help
```

On Windows, use `gradlew.bat` and the generated `build\install\kotlogramme\bin\kotlogramme.bat`.

## Picking the facade version

The client depends on the current Maven Central release of the facade. To test a different one:

```bash
./gradlew test -PkotlogrammeVersion=0.2.0
```

## Documentation

- [`docs/plan.md`](docs/plan.md) — the implementation plan of record.
- [`docs/architecture.md`](docs/architecture.md) — how the code is organised, and why.
- [`docs/decisions.md`](docs/decisions.md) — design decisions and their rationale.
- [`docs/features.md`](docs/features.md) — user-facing features that actually ship.
- [`AGENTS.md`](AGENTS.md) — engineering rules for humans and agents.
