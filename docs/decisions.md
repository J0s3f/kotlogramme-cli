# Design decisions

Each entry records the decision, the alternatives considered, and why they lost. A decision without
its rationale is one the next reader has to re-litigate.

## 0001 — Build on `kotlogramme` rather than TDLib or raw grammers

**Decision.** The client is written in Kotlin/JVM on top of the `kotlogramme` facade.

**Alternatives.** TDLib via JNI (what `tgt` and `nchat` use) offers more features but drags in a
large native build and its own JSON interface; talking to `grammers` directly would mean abandoning
Kotlin, and re-implementing the facade would duplicate the very thing this client exists to test.

**Why.** The stated goal is to exercise `kotlogramme` with a real application. Building on it makes
that automatic, and keeps the whole client in one language.

## 0002 — Bleeding-edge toolchain (JDK 25, Kotlin 2.4.20, Gradle 9.8)

**Decision.** The application targets the newest LTS JVM and the newest stable Kotlin.

**Alternatives.** Match the facade's conservative JDK 17 / Kotlin 2.1.20.

**Why.** The facade is a library other people link against, so it must stay compatible with older
runtimes. This is an application that ships to the user's own machine; it is the right place to eat
the new toolchain and surface issues early. The two are independent Gradle builds.

## 0003 — Hexagonal architecture

**Decision.** Domain and use cases in the middle; Telegram, config, CLI and rendering as adapters.

**Alternatives.** A direct "commands call the facade" structure is simpler for a small client.

**Why.** The application must be testable without a Telegram account. A single outbound port
(`TelegramGateway`) is the seam that makes that possible, and it keeps the facade's models from
leaking across the codebase. The cost is a few interfaces; the benefit is an offline CI.

## 0004 — Clikt for command parsing

**Decision.** Use Clikt 5.

**Alternatives.** picocli is mature and annotation-driven; hand-rolled parsing avoids a dependency.

**Why.** Clikt is idiomatic Kotlin, composes subcommands cleanly, and brings Mordant for terminal
output. picocli is Java-first and its annotation processing is awkward in Kotlin; hand-rolled
parsing would immediately lose `--help` and completion.

## 0005 — JLine for the interactive shell

**Decision.** Use JLine 4 for the interactive mode.

**Alternatives.** Plain stdin with `readLine()` is trivial but has no history, no line editing and no
completion. A full TUI library would be a different product.

**Why.** A REPL without history and completion is unpleasant to use daily. JLine gives both, works
across platforms, and stays out of the way of the one-shot commands.

## 0006 — JSON configuration under a per-OS directory

**Decision.** Configuration is a single JSON document (kotlinx.serialization) in an XDG-aware
directory: `~/.config/kotlogramme` on Linux, `%APPDATA%\kotlogramme` on Windows, `~/Library/Application Support/kotlogramme` on macOS.

**Alternatives.** TOML or YAML are friendlier to hand-edit; an env-var-only configuration avoids
files entirely.

**Why.** kotlinx.serialization is already a dependency for command output, so JSON adds nothing new
and needs no extra library. Hand-editing is a secondary use case; the CLI will offer `config set`.

## 0007 — Offline tests, opt-in live tests

**Decision.** The default test suite never contacts Telegram. A separate, excluded test class may
exercise a real account.

**Alternatives.** Record and replay real traffic, or require a test account for all adapter tests.

**Why.** CI must be fast, deterministic and safe. Fakes at the port boundary cover the logic; the
thin adapter is checked by mapping tests. Anything that needs the network is opt-in, mirroring the
facade's own `LiveTelegramIntegrationTest`.

## 0008 — GradleUp Shadow builds the fat jar

**Decision.** `kotlogramme-all.jar` is built with the GradleUp Shadow plugin
(`com.gradleup.shadow`) 9.6.1. Its `META-INF/services` entries are merged, nothing is relocated, and
the facade's six `native/<platform>/…` libraries keep their paths.

**Alternatives.** The original `com.github.johnrengelman.shadow` plugin is unmaintained and not
tested against Gradle 9.8. A hand-rolled `Jar` with `from(configurations.runtimeClasspath…)` would
have to reimplement duplicate handling, service-file merging and multi-release jars, and would drift
from the standard behaviour. Shipping the `installDist`/`distZip` `lib/` directory avoids shading
entirely, but it is many files to move onto a machine and the point of T8.1 is a single artifact.

**Why.** Shadow is the standard tool and 9.6.1 is the newest published release whose compatibility
matrix covers Gradle 9.8. The requirements decide the configuration:

- `mergeServiceFiles()` keeps ServiceLoader working for JLine, Mordant and SLF4J, which the CLI
  needs at runtime.
- No relocation: the facade (`com.github.badoualy.telegram.api`), the CLI (`org.kotlogramme`) and
  `kotlinx.serialization` are all resolved by their real names, and relocating
  `kotlinx.serialization` would break the `@Serializable` metadata the config store relies on.
- Shadow copies resources verbatim, so `native/<platform>/…` survives; the facade extracts the
  library with `ClassLoader.getResourceAsStream`, so those paths must not move.
- `duplicatesStrategy = INCLUDE`, with an `EXCLUDE` override for files outside
  `META-INF/services/**`: Shadow's default `EXCLUDE` drops duplicate service descriptors before the
  merge transformer sees them, so ServiceLoader would silently lose providers.
- The artifact is named `kotlogramme-all.jar` (no version suffix) and `assemble` depends on
  `shadowJar`, so the standard build produces it. The plain `jar` remains the input to
  `installDist`/`distZip`, and Shadow adds `shadowDistZip`/`installShadowDist`; all four still work.
