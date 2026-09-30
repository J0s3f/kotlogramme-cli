# Changelog

All notable changes to `kotlogramme-cli` are recorded here.

## 0.1.0 — unreleased

Initial project.

### Features

- `config` / `config set` — show or store the Telegram API credentials and the output format.
- `login` (user phone code plus optional 2FA, or a bot token), `logout`, `whoami`.
- Global `--config-dir` and `--version`; output as a table, plain text or JSON.
- Credentials resolve from the config, falling back to `TG_API_ID` / `TG_API_HASH`.

### Project

- Gradle build on the newest LTS JVM (JDK 25), Kotlin 2.4.20 and Gradle 9.8, with a toolchain
  resolver so the JDK is fetched where it is missing.
- Dependencies: `kotlogramme` 0.2.0, Clikt 5, JLine 4, kotlinx.serialization 1.11.
- CI runs `clean test` on Linux, Windows and macOS.
- `AGENTS.md`, [`docs/plan.md`](docs/plan.md), [`docs/architecture.md`](docs/architecture.md),
  [`docs/decisions.md`](docs/decisions.md) and [`docs/features.md`](docs/features.md).
