# Implementation plan

`kotlogramme-cli` is a scriptable, feature-rich terminal client for Telegram, built in Kotlin on
top of the [`kotlogramme`](https://github.com/J0s3f/kotlogram) facade. This document is the plan of
record: what ships, in what order, and how it is verified.

## Goals

1. A genuinely useful client: read and send everything the Telegram API reasonably allows from a
   terminal, in both a one-shot (scriptable) and an interactive mode.
2. A reference consumer that exercises the whole `kotlogramme` surface, so its gaps are found by a
   real application rather than by tests.

## Non-goals

- Voice/video calls, stories, and paid media.
- A full-screen ncurses dashboard. The interactive mode is a readline shell, not a TUI clone of the
  official client.
- Multi-account concurrency in one process (a config directory per account is enough).

## Reference feature matrix

Drawn from `tgt`, `nchat`, `telegram-console` and `tg`. The columns are the clients; `ours` is the
target.

| Capability | tgt | nchat | telegram-console | tg | ours |
| --- | --- | --- | --- | --- | --- |
| Scriptable one-shot commands | – | – | – | yes | **yes** |
| Interactive shell | yes | yes | yes | yes | **yes** |
| Login: phone + code | yes | yes | yes | yes | **yes** |
| Login: 2FA password | yes | yes | – | yes | **yes** |
| Login: QR | – | yes | yes | – | yes (later) |
| Session persistence | yes | yes | yes | yes | **yes** |
| Chat list / unread / pinned / muted | yes | yes | yes | – | **yes** |
| Read history with paging | yes | yes | yes | yes | **yes** |
| Send text | yes | yes | yes | yes | **yes** |
| Reply / edit / delete / forward | yes | yes | – | – | **yes** |
| Reactions | yes | yes | – | – | **yes** |
| Send files / media | yes | yes | – | yes | **yes** |
| Download media | yes | yes | – | yes | **yes** |
| Search messages | yes | yes | – | – | **yes** |
| Contacts / block | yes | yes | – | – | **yes** |
| Live updates / notifications | yes | yes | yes | – | **yes** |
| Mark read / typing | yes | yes | – | – | **yes** |
| JSON output for scripting | – | – | – | – | **yes** |
| Folders, stickers, inline, admin | some | some | – | – | **yes** |

## Architecture at a glance

Hexagonal, dependency-inverted. See `docs/architecture.md`.

- `domain` — plain Kotlin entities (`Chat`, `Message`, `Account`, `MessageId`, …).
- `application/port/api` — inbound use cases (`ListDialogs`, `ReadHistory`, `SendMessage`, …).
- `application/port/spi` — outbound ports (`TelegramGateway`, `SessionStore`, `ConfigStore`, `Clock`,
  `Output`).
- `application/service` — use-case implementations.
- `adapter/telegram` — the one place that touches `kotlogramme`.
- `adapter/config` — file-backed stores.
- `adapter/cli` — Clikt commands and the interactive shell.
- `adapter/format` — renderers (table, JSON, plain).

The single most important boundary is `TelegramGateway`: everything that talks to Telegram goes
through it, so the rest of the application is testable offline with a fake.

## Phases

Each task is sized for one agent and one worktree. Tasks list their deliverable and their
acceptance check. A task depends on every task listed before it in its section.

### Phase 0 — Foundation (this commit)

- Gradle build, wrapper, CI workflow, `AGENTS.md`, this plan, README, license.
- A compiling `Main.kt` with `--version`/`--help` and one test, so the pipeline is green from the
  first commit.

### Phase 1 — Session, config and authentication

- **T1.1 `ConfigStore` (SPI + file adapter).** `~/.config/kotlogramme/config.json` (XDG-aware on
  Linux, `%APPDATA%` on Windows, `~/Library/Application Support` on macOS) holding API id/hash,
  default output format, and the session directory. Defaults are created on first run. Tests cover
  path resolution, defaults, and a round-trip.
- **T1.2 `SessionStore` (SPI + file adapter).** Persist the kotlogramme session so a login survives
  restarts. Isolate the file layout behind the port so it can change without touching use cases.
- **T1.3 `AccountGateway` port + kotlogramme adapter.** `isAuthorized`, `requestLoginCode`,
  `signIn`, `checkPassword`, `signOut`, `whoAmI`. Wraps the library's auth operations; the only
  adapter allowed to touch the client's auth surface.
- **T1.4 CLI auth commands.** `login` (phone → code → optional 2FA), `logout`, `whoami`. Interactive
  prompts via Clikt; non-interactive flags (`--phone`, `--code`, `--password`) so scripts and tests
  can drive it.

Acceptance: with a fake gateway, `login` walks phone → code → password and persists a session;
`whoami` prints the account; `logout` clears it. `gradle test` is green offline.

### Phase 2 — Reading

- [x] **T2.1 `Chat` domain + dialog listing.** `ListDialogs` use case over `getDialogs`, paging and
  totals, projecting title, kind, unread count, pinned/muted/archived flags. CLI `dialogs`
  (`--limit`, `--json`).
- **T2.2 Chat resolution.** `ResolveChat` over username, id (`--peer`), invite link, or a bare
  `@name`, returning a domain `Chat` used by every other command.
- [x] **T2.3 Message domain + history.** `ReadHistory` over `getHistory` with paging, projecting sender,
  text, timestamp, edited/pinned flags, reply header, action and media summary. CLI `history`
  (`--limit`, `--before`, `--json`), plus renderers for table/plain/JSON.

Acceptance: fake gateway fixtures produce a stable dialog list and message page; renderers are
snapshot-tested.

### Phase 3 — Writing

- [x] **T3.1 Send text.** `SendText` with reply-to, silent, schedule, and link-preview control. CLI
  `send <peer> <text...>`, reading from stdin when text is `-`.
- [x] **T3.2 Edit, delete, forward, pin.** `EditMessage`, `DeleteMessages`, `ForwardMessages`,
  `PinMessage`/`UnpinMessage`, each with CLI commands and tests.
- [x] **T3.3 Reactions.** `React` / `RemoveReaction` over `sendReactions`.
- [x] **T3.4 Read receipts and typing.** `MarkRead`, `sendChatAction` used by the interactive shell.

Acceptance: each use case has a fake-gateway test asserting the exact gateway call; CLI commands are
covered end-to-end against the fake.

### Phase 4 - Media and files

- **T4.1 Send file / photo / media by URL.** Caption, spoiler, TTL, parse mode, as-album.
- **T4.2 Download.** `DownloadMedia` with chunk paging and a progress view; safe file naming.
  **Partly done**: the `download-media` command writes a message's media to disk, names the file after
  the media, and creates missing parent directories. **Still open**: the CLI reads the whole file in
  one call, so it offers neither the facade's `downloadMediaChunk` paging nor a download progress
  view.
- **T4.3 Albums.** `send-album` for multiple files. Still open.
- **T4.4 List a chat's files.** `list-files` with a server-side media-kind filter. **Done**, added
  after Phase 8; it is listed here because it belongs to this phase.

Acceptance: media sends are asserted against the fake; downloads are exercised with a fake that
supplies chunk bytes.

### Phase 5 — People, search and updates

- **T5.1 Contacts.** `contacts`, `search-contacts`, `block`/`unblock`, `import`.
- **T5.2 Search.** Global and per-chat search with totals and filters.
- **T5.3 Update stream.** `listen` — subscribe to the update stream, print new messages (human or
  JSON Lines), with `--once`, `--follow`, graceful Ctrl-C, and a `wait` command that blocks until
  the next matching message.

Acceptance: the update loop is driven by a fake stream; JSON Lines output is snapshot-tested.

### Phase 6 — Interactive shell

- [x] **T6.1 JLine REPL.** History, completion for commands and peers, prompt showing the current chat,
  and line editing. One shell that dispatches to the same use cases as the one-shot commands.
- [x] **T6.2 Shell workflow.** `open <peer>`, `list`, `read`, `send`, `reply`, `back`, `quit`, and a
  compact message view that keeps the last N messages of the current chat.

Acceptance: the shell is unit-tested through its command dispatcher with a fake terminal.

### Phase 7 - Beyond the facade's first release

Began against `kotlogramme` 0.2.0 and its gap-closure work; the client now builds on the
`kotlogram` `v0.9.7` tag from JitPack.

- **T7.1 Folders.** List and filter by dialog folder; `folders` command.
- **T7.2 Admin and rights.** Show and edit participant rights.
- [x] **T7.3 Stickers and inline.** Sticker reads and sends (`stickers`, `sticker-set`,
  `send-sticker`) and the one-shot `inline` command are done with the facade at 0.4.0; the shell
  gained all four in T9.2.
- **T7.4 Uploads from streams.** Use `uploadStream` for large files and stdin pipe input.
- **T7.5 Invite members.** `invite <peer> <user>` over `channels.inviteToChannel` (channels) and
  `messages.addChatUser` (basic groups). Found missing during the live test, where a kicked member
  could only be added back from the Telegram app. Done, with the facade at 0.3.0.
- **T7.6 Bot-safe peer resolution.** `ChatReferenceResolver` looks a numeric id up in the dialog
  list, and a bot may not call `messages.getDialogs`, so `send <numericId>` from a bot session fails
  with `BOT_METHOD_INVALID`. Fall back to a direct peer lookup when the listing is unavailable, and
  report the restriction clearly when neither works.

### Phase 8 — Packaging and release

- [x] **T8.1 Fat jar.** A single runnable jar (`kotlogramme-all.jar`) that contains every dependency,
  including the `kotlogramme` jar and the six native libraries it bundles. The native loader must
  keep working from inside the shaded jar: `java -jar kotlogramme-all.jar --version` runs on a
  machine with only a JRE, and a command that needs Telegram fails with a credentials or connection
  error rather than `UnsatisfiedLinkError`. Shading must preserve `META-INF/services` entries
  (ServiceLoader) and the `native/<platform>/` resource paths, and must not relocate `kotlogramme`
  or `kotlinx.serialization`. Acceptance: the jar is exercised on Linux, Windows and macOS in CI.
- [x] **T8.2 Distributions and release.** `application`'s `installDist`/`distZip` still build, and a
  tagged release publishes a GitHub Release carrying the fat jar and the distributions (the
  workflow's shape is recorded in `decisions.md`). [`docs/native-image.md`](native-image.md)
  assesses the GraalVM alternative: **viable with caveats** — no GraalVM was available on the build
  machine, so nothing was built or run, the facade resolves its serializers reflectively so every
  `@Serializable` payload would need registering, and the exe would land near the 39.4 MB fat jar,
  the real win being startup and no JVM.
- [x] **T8.3 Documentation.** `docs/features.md` and `docs/decisions.md` complete, a `CHANGELOG.md`
  entry, and a README status update (finished in T9.6).

The fat jar earns its keep because the alternative — a `distZip` with a `lib/` directory — is
awkward to move around. The risk is exactly the native loading, which is why T8.1 is a task with its
own acceptance check rather than a build tweak.

### Phase 9 — Parity follow-ups

- [x] **T9.1 Matroska metadata.** `FileMediaProbe` parses EBML for `.mkv`/`.webm` and returns the
  duration (`ticks × TimecodeScale / 1e9`, default scale 1,000,000 ns) and the `Video` dimensions of
  the `TrackType == 1` track, skipping clusters by size; a malformed or truncated file falls back to
  the video kind with null metadata. `send-file --detect` on an mkv now sends a video with a
  duration and a resolution. `ec69223`.
- [x] **T9.2 Shell parity.** The shell gained `stickers`, `sticker-set <set>`,
  `send-sticker <set> <index>`, `inline <bot> <query> [--send <index>]`, `members [<peer>]` and
  `folders`, with `help` and the completer updated; the send and lookup commands act on the current
  chat the prompt shows. `9897d46`.
- [x] **T9.3 `via` column.** The message view shows the inline bot a message came through beside
  `from`. At this release the facade projected only a numeric `viaBotId`, so the column showed a bare
  id; T10.2 resolves it to `@username`. `1a31732`.
- [x] **T9.4 Media reply and silent.** `send-file`, `send-media-url` and `copy-media` gained
  `--reply-to <id>` and `--silent`, threaded through `SendMedia`/`MediaGateway` to the facade's
  `mediaSend`/`mediaSendUrl`/`mediaCopy`. `25f970c`.
- [x] **T9.5 Native-image assessment.** [`docs/native-image.md`](native-image.md) records whether a
  GraalVM `native-image` build is worth it: viable with caveats, nothing built or run without
  GraalVM, the facade's reflective serializers must all be registered, and the exe lands near the
  fat jar's size. `60916ea`.
- [x] **T9.6 Documentation.** These five changes recorded in `docs/features.md`,
  `docs/decisions.md`, this plan, `CHANGELOG.md` and `README.md`.

### Phase 10 — Consuming `kotlogramme` 0.7.0

- [x] **T10.1 Received formatting.** The domain `Message` carries the facade's `entities`, mapped by
  `MessageMapper`. A `MessageStyler` renders them in the table's text column: the terminal-expressible
  kinds become ANSI styles, a spoiler is masked with blocks rather than printed, an out-of-range span
  is ignored, and colour is emitted only for the table format on a terminal with neither `NO_COLOR`
  nor `--no-color`, or when `--color` forces it on off a terminal. Columns are measured and padded by
  visible width, so styling never moves the borders.
- [x] **T10.2 Inline-bot resolution.** A `UserGateway` port, with a kotlogramme adapter, turns the
  distinct `viaBotId`s of a history page into `@username` in one batched `usersGetUsers` call; a
  failure or an unresolved id falls back to the numeric id without failing the command. Only the
  history path pays for the lookup.
- [x] **T10.3 Facade bump.** `kotlogrammeVersion` moves to `0.7.0`.

Acceptance: the offline suite covers the styling (alignment, colour on/off, masking, out-of-range),
the batched lookup (resolution, fallback, failure) and the JSON shape; the client still builds
against the published facade. Verified offline; the live pass is run by the orchestrator.

### Phase 11 — Send-file kind by default

- [x] **T11.1 Detect the file kind by default.** `SendFileCommand` probes a real file with the
  existing `FileMediaProbe`/`MediaKindHint` path and sends a video as a streamable video with its
  metadata, a photo as a photo and anything else as a document, so a plain `send-file clip.mp4` is no
  longer a document. `--detect` is the explicit form of the default and the new `--no-detect` forces
  a plain document; `--photo`/`--video` force the kind and win over detection, and the combinations
  with the detector flags are rejected rather than left undefined. `docs/features.md`,
  `docs/decisions.md` (0012) and `CHANGELOG.md` record the change.

Acceptance: the offline suite covers the default for a video, a photo, an unknown file and a file
with no extension, a video whose metadata cannot be read still going as a video, the `--no-detect`
opt-out, `--photo`/`--video` winning over detection, and the rejected combinations; every existing
test stays green. Verified offline; the live pass is run by the orchestrator.

## Verification strategy

- `gradle clean test` is the gate for every task: fast, offline, deterministic.
- The `TelegramGateway` fake is the seam that makes the whole application testable without network
  access; the real adapter stays thin.
- A single opt-in test class may exercise a real account, excluded from `test`, in the same spirit
  as the library's `LiveTelegramIntegrationTest`.

## How the work is delivered

- One worktree and one branch per task, named `feat/t<phase>.<n>-<slug>`.
- An agent implements and verifies a task, then commits on its branch; the orchestrator merges
  into `main` and keeps this file's checkboxes up to date.
- A task is done when its acceptance check passes and the documentation it touches is updated.
