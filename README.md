# kotlogramme-cli

A command-line Telegram client written in Kotlin, built on the
[`kotlogramme`](https://github.com/J0s3f/kotlogram) facade.

It aims to be two things at once: a genuinely useful, scriptable client for the terminal, and the
reference consumer that exercises the whole `kotlogramme` surface so its gaps are found by a real
application.

> **Status: early.** The project skeleton is in place (build, CI, architecture). Features land
> phase by phase; see [`docs/plan.md`](docs/plan.md) for the roadmap and [`docs/features.md`](docs/features.md)
> for what actually ships.

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
