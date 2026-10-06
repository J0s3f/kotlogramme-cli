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

## 0009 — Tag-driven release workflow

**Decision.** A release is cut by pushing a `v*` tag, or by a manual `workflow_dispatch` that takes
the version; `.github/workflows/release.yml` then builds the fat jar and the `distZip` distribution
under that version, verifies that the fat jar still bundles all six native libraries, and publishes
both on a GitHub Release.

**Alternatives.** Building and uploading a release from a local machine keeps the artifact off the
runners but makes who-published-what untraceable, and the native-library check would still have to
happen by hand. A per-push job that always publishes would ship every commit as a release.

**Why.** The build and the check that matters — that shading did not drop `native/<platform>/…` —
run on the same runner that publishes, so a release cannot go out with a jar that lost its `.dll`
or `.so`. The release is independent of the library's own build, because the client consumes the
facade from Maven Central and never needs its sources.

## 0010 — Keep the `grammers-*` git dependencies on Codeberg

**Decision.** The facade's three `grammers-*` git dependencies stay pinned to their Codeberg
source, and the release is retried there. The `github.com/Lonami/grammers` mirror is not used.

**Alternatives.** Switching the pins to the GitHub mirror. It was checked and is byte-identical for
the pinned revision (tree `19634363…`, and `cargo fetch` resolves through it), which would unblock
the current release.

**Why.** The mirror is archived with a February-2026 `master` and no branch containing the pinned
revision, so it would unblock this release and likely fail the next grammers bump. Codeberg was
intermittently returning 503 to the runners, which is a transient failure to retry, not a reason to
move onto a dead mirror. The client's own release does not depend on this: it resolves the facade
from Maven Central.

## 0011 — Render formatting only in the table, and mask spoilers

**Decision.** A received message's entities are carried into the domain and rendered as ANSI styling
in the table's text column only. Colour is emitted only when the format is the table, stdout is a
terminal, `NO_COLOR` is unset and `--no-color` is off; `--color` forces it on when something other
than a terminal is consuming the output, such as a pager, and `--no-color` wins over it. A spoiler is
rendered as a run of `█` of the
same length; an out-of-range span is dropped. The table measures a column by its *visible* width
(ANSI SGR sequences excluded) and pads on that, so the borders cannot drift.

**Alternatives.** Rendering styling in every format would put escapes into the JSON and plain output
that scripts consume, which is wrong for both. Applying `padEnd` to the styled string would count the
escapes, so the borders would move exactly on the cells that carry formatting. ANSI's concealed mode
(`SGR 8`) for spoilers renders inconsistently and would make hidden text look selectable; dropping the
spoiler text silently, or printing it, both mislead. Emitting hyperlinks with OSC 8 was rejected as
too uneven across terminals for the modest gain.

**Why.** Only the table is a human-facing view, so only it gets escapes. Measuring the visible width
is a tiny, contained change to `ConsoleOutput` (a regex strip) and makes the invariant — every line
of a table is its border width — hold for styled and unstyled cells alike. Masking is honest: it says
there is a spoiler and how long it is without revealing it. `NO_COLOR`, `--no-color` and the terminal
check are the conventional three ways a user turns colour off, and all three are respected; `--color`
is the matching way to ask for colour when the consumer is a pipe, which is what a pager such as
`less -R` needs.

## 0012 — Detect a sent file's kind by default, with `--no-detect` to opt out

**Decision.** A plain `send-file <peer> <path>` probes the file and sends it by the kind it finds: an
ISO base media or Matroska video goes out as a streamable video with its duration and dimensions, a
`jpg`/`jpeg`/`png` as a photo, and anything else as a document. `--detect` is the explicit spelling of
that default, `--no-detect` forces a plain document with no probing, and `--photo`/`--video` force
the kind and win over detection. The two detector flags are mutually exclusive with each other and
with the forcing flags, and explicit `--duration`/`--width`/`--height` still require `--video` or
`--detect`.

**Alternatives.** Keep `--detect` opt-in and document the trap, leaving a plain send as a document.
That is what shipped first, and it is wrong: a `.mp4` is a video to every user and every other
client, so the obvious command produced a download bubble with a file name and an audio-looking
duration. Making `--video` the default for every file would push a `.txt` or a `.zip` out as a video
the facade cannot describe. A separate `--document` flag lost to the negative `--no-detect`, which
matches the existing `--no-color` vocabulary and names exactly what it turns off.

**Why.** The kind is a property of the file, not of the command line, so the tool should read it
rather than make the user repeat what the extension already says. The opt-out keeps the escape hatch
for a caller who wants the bytes sent verbatim — a file the probe would mistake for a media type, or
a deliberately raw upload — without making that the common path. Reusing `FileMediaProbe` and
`MediaKindHint` keeps one detector, and the failure modes stay safe: a video whose container cannot
be read is still a video with null metadata, a file with no extension is a document, and nothing is
deleted or rewritten, so the changed default changes the bubble, not the payload.

## 0013 — Progress as a port with the service owning the slot, not a rendering callback

**Decision.** Upload progress is an `UploadProgressReporter` in `application/port/spi` that hands out
an `UploadProgressSlot`. `SendMediaService` opens the slot once the input is known good and before any
bytes move, and closes it in a `finally`. The terminal bar is an `UploadProgressReporter` in
`adapter/format`; `adapter/telegram` only attaches the facade's counter to the slot it was given.

**Alternatives.** A `((sent: Long) -> Unit)` callback threaded through the gateway, which reads more
directly. Having `adapter/telegram` draw the bar itself, which needs no new port at all. Buffering the
upload and drawing from the same thread, which is what the facade's own blocking calls make look
tempting.

**Why.** An upload blocks the thread performing it, so the code that draws cannot also be the code
that uploads. A counter plus a slot splits the two without either side knowing about the other, and
`isWatched` lets a bar that nobody asked for cost nothing: the gateway skips the facade's progress
handle entirely and the upload stays on its plain path. A callback cannot be cleaned up, because a
partly drawn line has to be erased on the failure and the interrupt paths as well as the successful
one, and only the code that opened the bar knows when it is over; closing in a `finally` in the
service is that place, and it is also offline-testable, which a `TelegramClient`-shaped drawing loop
would not be. Putting the bar in `adapter/format` rather than `adapter/telegram` keeps the render
loop out of the Telegram adapter, so no presentation concern is coupled to the library.

## 0014 — The progress bar follows the terminal, like colour

**Decision.** The bar is on when `System.console() != null` and off otherwise. `--progress` forces it
on, `--no-progress` forces it off and wins when both are given.

**Alternatives.** On by default everywhere. On only with `--progress`, leaving the flag mandatory.

**Why.** A carriage return is a control character: in a pipeline or a redirected file it becomes part
of the data, and a consumer reading the command's output sees a line with embedded returns on it. The
test that matters is therefore "does this terminal exist", not "is progress useful", and that is the
same question `--color` already asks, so the two now resolve identically and `--no-progress` mirrors
`--no-color`. On by default would break scripts silently; making the flag mandatory would put work on
the common case, which is a person watching a terminal.

## 0015 — Piped stdin is spooled to a temporary file rather than buffered

**Decision.** A `-` path reads standard input as raw bytes into a `SpoolFile`, a temporary file that
is measured for its length, uploaded from with a `FileInputStream` and deleted in a `finally`.

**Alternatives.** Reading stdin as text through Clikt's terminal and buffering it into a
`ByteArray`, which is what the code did. Buffering the bytes into a `ByteArray` but decoding nothing,
which fixes the corruption but still holds the payload. Teaching the facade to accept a stream of
unknown length.

**Why.** Reading it as text was simply wrong: UTF-8 decoding turns an undecodable byte into a
replacement character and drops a lone `0x0D`, so a piped photo arrived corrupted and longer than it
went in. But buffering the raw bytes is the wrong fix too — it would satisfy the facade's `size`
requirement at the cost of the heap the facade was made to avoid, since 0.8.0 uploads incrementally
precisely so a large file is never fully resident. A spool file keeps the memory bounded, makes the
size exact without the user having to know it, and turns out to fix a second thing: because the total
is now real, a piped upload gets a progress percentage rather than only a byte count. It costs one
pass through the temporary directory, which is cheap next to a network upload and is documented as
such. Deleting in a `finally` is why a failed upload leaves no spool file, and swallowing a deletion
failure is why a cleanup problem never masks the outcome of the upload.

## 0016 — The facade comes from JitPack rather than Maven Central

**Decision.** The build resolves `com.github.J0s3f:kotlogram` from JitPack, pinned by tag
(`v0.9.11`). `settings.gradle.kts` adds the JitPack repository beside Maven Central and sets
`RepositoriesMode.FAIL_ON_PROJECT_REPOS` so every repository is declared in one place;
`build.gradle.kts` names the JitPack coordinate, and `-PkotlogrammeVersion=...` still selects a
different tag.

**Alternatives.** Staying on Maven Central's `io.github.j0s3f:kotlogramme` and waiting for the tag
to be published there. Vendoring the facade jar into the repository. Building the facade from source
as a composite build.

**Why.** A facade tag reaches JitPack as soon as it is pushed, while Maven Central lags behind, so
JitPack is the only source that has `v0.9.11` today. Pinning the tag keeps the client on the exact
facade build it was tested against, and Maven Central stays first in the list so anything already
published there is unaffected. The cost is a slower first resolve while JitPack builds the tag and a
dependency on JitPack's availability, both acceptable next to blocking every client release on Maven
Central's publication schedule.

## 0017 — Standalone packages use GraalVM Native Image and Liberica NIK in CI

**Decision.** Add the official GraalVM Native Build Tools Gradle plugin as an opt-in build path,
for Windows x86_64 and Linux/macOS x86_64 and ARM64. The executable embeds the host Telegram library; the loader
extracts it at runtime. Derive serializer reflection metadata from the resolved facade jar and keep
native smoke checks offline. The standard JVM build and fast test task remain independent of native
compilation. GRAALVM_HOME selects the native toolchain, and the Windows helper defaults to the
installation supplied for development rather than baking a machine path into Gradle.

**Alternatives.** Bundling a JVM with jpackage would remove the install prerequisite but still ship
a runtime directory. Rewriting with Kotlin/Native would replace the JVM facade and terminal stack.
A manually curated serializer list would drift with facade upgrades; collecting it through a live
tracing-agent session would require credentials and incomplete command coverage.

**Why.** The requested distribution runs with no JRE or JDK. The package includes the generated AWT libraries.
The existing JNI boundary and embedded DLL loader make Native Image a smaller change than a rewrite.
Generating registrations from the bounded protocol/raw packages keeps coverage aligned with the
selected facade version. No fallback image is permitted because that would reintroduce Java. Native
compilation costs extra time and needs MSVC, so it is an explicit distribution step, not part of every
ordinary build. Runtime smoke checks copy the portable native files and remove Java from the environment.

CI uses Liberica NIK 25 on all five native targets to avoid mixing distributions and to keep Intel
macOS supported with an up-to-date JDK 25. Oracle/GraalVM CE discontinued Intel macOS binaries after
25.0.1; pinning that older compiler lost to NIK's maintained target. The local Windows helper still
uses the explicitly supplied Oracle installation. Windows ARM64 has no GraalVM/NIK Native Image
compiler, so its CI job uses an ARM64 Liberica JVM instead. Native release builds share a reusable
workflow with CI; all five must succeed before the release is created. Unix packages use tar.gz to
preserve executable permissions, Windows uses zip, and both include the image's support libraries.
Linux x86_64 builds use Ubuntu 22.04 for a broader glibc baseline. ARM64 uses Ubuntu 24.04 because
the facade's ARM64 ELF library has a strong GLIBC_2.39 requirement. The x86_64 library's reference to
that version is weak, so it loads on the older runner. Compiling the image on an older ARM64 runner
cannot lower the requirements of the already-built facade library.
## 0018 — Wrappe supplies the single-file runner on every native platform

**Decision.** Pack the GraalVM executable and support libraries with Wrappe 1.0.6. Use verified
release packers for Windows, macOS and Linux x86_64, and compile its native ARM64 Linux runner from
the pinned Rust crate. Windows payloads also include MSVC redistributable DLLs. Preserve caller
working directory, console I/O and exit status; suppress packer banners and verify cached files.
If the native output already consists of one runtime file, copy it directly. Publish only five
single-file native executables plus the bundled jar and Java distribution zip.

**Alternatives.** A custom Go extraction runner passed initial Windows tests but would add code for
signals, cleanup and caching that an existing packer already provides. PyInstaller is established
but would add a Python runtime to a native application. Wrappe explicitly supports both Mac CPU
architectures and Windows. AppImage is a conventional Linux option with ready ARM64 tooling, but
the user chose Wrappe after comparing the two to keep one format across platforms. MSI and native
archives with separate DLLs/shared libraries were excluded by the user's distribution preference.

**Why.** The user wants one downloadable program without Java or an installer. Reusing a maintained
open-source packer avoids a bespoke launcher. The versioned extraction cache supports repeated and
parallel CLI commands; cleanup-after-every-run would risk deleting files another process still
uses. Unix exec preserves signals directly; Windows console mode waits and returns the native
exit code. Keeping raw native outputs locally supports diagnosis without exposing multi-file
native archives in the release.