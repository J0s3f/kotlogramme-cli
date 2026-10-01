# Changelog

All notable changes to `kotlogramme-cli` are recorded here.

## Unreleased

Built on `kotlogramme` 0.7.0.

### Features

- Received messages now show their formatting. In the table's text column, bold, italic, underline,
  strikethrough, inline `code`, `pre`, links, mentions and the auto-detected kinds (hashtag, command,
  email, phone, card) are rendered; a kind a terminal cannot express is left plain. A spoiler has no
  terminal equivalent and is masked with blocks rather than printed, and an entity whose span falls
  outside the text is ignored instead of throwing. Colour is used only for the table format on a
  terminal, and `NO_COLOR` or the new global `--no-color` turns it off, while the new global `--color`
  forces it on for a caller that is not a terminal, such as a pager; the plain and JSON formats
  stay raw. Columns are measured by visible width, so the styling never moves the borders.
- The `via` column resolves a message's inline bot to `@username` through one batched lookup of the
  distinct ids on the page. A failed lookup or an unresolved id falls back to the numeric id and never
  fails the command; only the history path pays for the lookup, not `listen` or search.
- `send-file` detects a real file's kind by default. A plain `send-file @chat clip.mp4` now goes out
  as a streamable video with the duration and resolution read from the container, a `.png` as a
  photo, and anything else as a document. `--detect` is the explicit form of that default, and the
  new `--no-detect` turns detection off and sends the bytes as a plain document. `--photo` and
  `--video` force the kind and win over detection; each is mutually exclusive with the other and
  with `--detect`/`--no-detect`, and explicit `--duration`/`--width`/`--height` still require
  `--video` or `--detect`.
- `download-media <peer> <message-id> [<target>]` writes a message's media to a local file. Without
  a target the file is named after the media's own name, falling back to the message id, and lands
  in the working directory; missing parent directories are created and an existing file is
  overwritten. It prints the path and the byte count. A message that has no media, a peer that does
  not resolve and a message id Telegram cannot find are all reported as a one-line error with a
  non-zero exit code.
- `list-files <peer>` lists a chat's files, filtered by media kind on Telegram's side rather than by
  scanning history, so it reaches the whole chat and not just the part a history read has covered.
  `--kind` names the filter (`photo`, `video`, `photo-video`, `document`, `audio`, `voice`, `gif`,
  `animation`), `--limit` caps the page, and `--total` prints just the count. The listing renders as
  the same table every other command uses, and as JSON through the configured output format.

### Project

- The facade dependency moves to `kotlogramme` 0.7.0.

## 0.1.0 — 2026-09-30

The first tagged release. Built on `kotlogramme` 0.6.0 and published as a GitHub Release
carrying the fat jar and the distribution archive.

### Features

- `config` / `config set` — show or store the Telegram API credentials and the output format;
  `login` (phone code plus optional 2FA, or a bot token), `logout`, `whoami`, `doctor`.
- `dialogs` and `history` — the chat list, and paged message history with reply markers, media
  labels and a `via` column naming the inline bot a message came through (a bare id today, because
  the facade projects no username to resolve it to).
- `send`, `edit`, `delete`, `forward`, `pin`/`unpin`, `react`/`unreact`, `mark-read`; `send` accepts
  its body on stdin via `-`.
- `send-file <peer> <path|->` (a document, `--photo`, streamable `--video`, or `--detect`),
  `send-media-url` and `copy-media`; `--detect` reads a video's duration and dimensions from its
  container, Matroska (`.mkv`, `.webm`) as well as MP4/M4V/MOV. All three media commands take
  `--reply-to <id>` and `--silent`.
- `contacts`, `search` (global or `--in <peer>`, with `--total`), `members`/`invite`/`kick`.
- `folders`, and `listen` — follow live updates with `--once` and `--json` (one JSON object per
  line), exiting cleanly on Ctrl-C.
- `stickers`, `sticker-set <set>` and `send-sticker <peer> <set> <index>` — list the installed sets,
  read one with its stickers numbered, and send one from that list.
- `shell` — an interactive REPL with history, completion for commands and peers, a current-chat
  prompt, and `open`/`read`/`send`/`reply`/`contacts`/`search`, plus `stickers`, `sticker-set` and
  `send-sticker`, `inline`, `members` and `folders`. The send and lookup commands act on the
  current chat the prompt shows, so the shell's `inline` needs no `--to`.
- Global `--config-dir` and `--version`; output as a table, plain text or JSON.
- Credentials resolve from the config, falling back to `TG_API_ID` / `TG_API_HASH`.
- A fat jar (`kotlogramme-all.jar`) with every dependency and the six native libraries bundled.

### Project

- Gradle build on the newest LTS JVM (JDK 25), Kotlin 2.4.20 and Gradle 9.8, with a toolchain
  resolver so the JDK is fetched where it is missing.
- Dependencies: `kotlogramme` 0.6.0, Clikt 5, JLine 4, kotlinx.serialization 1.11.
- CI runs `clean test` on Linux, Windows and macOS.
- A tag-driven release workflow: a `v*` tag, or a manual dispatch with a version, builds the fat jar
  and the `distZip` distribution under the release version, verifies the fat jar still bundles all
  six native libraries, and publishes both on a GitHub Release.
- [`docs/native-image.md`](docs/native-image.md) — an assessment of a GraalVM `native-image` build:
  viable with caveats, but nothing was built or run because no GraalVM was available.
- `AGENTS.md`, [`docs/plan.md`](docs/plan.md), [`docs/architecture.md`](docs/architecture.md),
  [`docs/decisions.md`](docs/decisions.md) and [`docs/features.md`](docs/features.md).
