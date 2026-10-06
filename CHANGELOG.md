# Changelog

All notable changes to `kotlogramme-cli` are recorded here.

## 0.4.1 - 2026-10-06

Built on the `kotlogram` `v0.9.11` tag, resolved from JitPack.

### Changed

- The macOS executables are compressed like the others and every native image is built with `-Os`,
  so they shrink from about 81 MB. The JNA terminal backend is excluded, which removes the JDK AWT
  libraries from every native image.

## 0.4.0 - 2026-10-06

Built on the `kotlogram` `v0.9.11` tag, resolved from JitPack.

### Features

- **Native distributions without a JRE or JDK.** Windows x86_64, Linux x86_64/ARM64 and macOS
  Intel/Apple Silicon single-file executables embed the native program and its support libraries using Wrappe.
- **Native CI and release packages.** Every native target uses Liberica NIK 25, runs offline smoke
  checks with Java removed from its environment, and uploads a single-file executable. Releases wait
  for all five native builds and attach those executables alongside the JVM artifacts. Windows ARM64
  remains covered by the JVM distribution and an ARM64 CI job.

### Fixed

- The version helper updates the README across minor-version boundaries as well as patch releases,
  without rewriting unrelated dependency versions.

## 0.3.4 - 2026-10-02

Built on the `kotlogram` `v0.9.11` tag, resolved from JitPack.

### Features

- **Every listing command pages, and the bounded ones can show everything.** `--after <cursor>`
  continues from the previous page - the command prints the cursor after its table as
  `# next: --after <cursor>` - and `--all` walks the whole list in one call on `dialogs`, `blocked`,
  `chat-photo-history` and `profile-photos`. `history`, `search`, `list-files` and `members` page
  without `--all`, because their whole set can be unbounded or very large. The shell's `read`,
  `search`, `files`, `members` and `blocked` verbs take `--after` too, and `blocked` takes `--all`.
- **`help <verb>` explains one verb.** `help` still lists the verbs and `help commands` still lists
  the CLI's command set; `help <verb>` adds that verb's usage, what it does and its options, and
  answers to a verb's aliases, so `help list` and `help dialogs` are the same.

### Changed

- Saved Messages is listed on the first page of `dialogs` only: a paged continuation does not repeat
  it, and `--all` shows it once at the top.

## 0.3.3 - 2026-10-02

Built on the `kotlogram` `v0.9.9` tag, resolved from JitPack.

### Features

- **`list-files` lists every kind by default.** `--kind` gains `all`, and it is the default, so the
  command named `list-files` now shows a chat's documents and audio beside its photos and videos.
  Telegram filters one media kind per search and has no "every file" filter, so `all` is the union of
  the kinds, merged newest first and de-duplicated; `--total --kind all` is the sum of the kinds'
  counts.

### Changed

- **`chat-photos` is renamed `chat-photo-history`.** It lists the changes to a chat's profile photo,
  which are service messages, not the photos posted in the chat, and the old name said otherwise. For
  the photos posted in a chat, `list-files --kind photo` reaches every one the chat holds.

### Fixed

- **Table columns line up when a cell holds a wide character.** The table measured cells in UTF-16
  code units, but a terminal pads by display columns, and the two differ for CJK (one code unit, two
  columns), emoji, flags, ZWJ sequences and combining marks. A row containing any of them occupied a
  different number of columns than its own border, so the columns drifted - a live dialog list showed
  it. Widths now come from JLine's grapheme-cluster table, and a preview or a quote is truncated on a
  column budget without splitting a surrogate pair or a flag.
- **The shell's `open` accepts `me` and `@me`.** The alias the one-shot commands already understood
  matched nothing in the shell, which resolved peers against the loaded dialogs, where that chat is
  titled "Saved Messages".

## 0.3.2 - 2026-10-02

Built on the `kotlogram` `v0.9.9` tag, resolved from JitPack.

### Features

- **Contacts**: `contacts block`, `unblock`, `search`, `blocked`, `import` and `delete` manage the
  account's contact list from the CLI.
- **Sessions**: `sessions list`, `terminate` and `terminate-all` inspect and end other logins.
  `terminate` takes an explicit session hash; `terminate-all` refuses without `--yes`, because it
  signs every other device out and cannot be undone.
- **Chat actions**: `chat-action` sends a typing or upload indicator to a chat.
- **Pinned messages**: `pinned` reads a chat's pinned message; `unpin` removes one, or `--all`
  removes them all.
- **Photos**: `chat-photos` and `profile-photos` list a chat's or account's photos.
- **Albums**: `send-album` sends several files as one grouped album.
- **Saved Messages** is addressable as `me` or `@me`, and `dialogs` shows it first. It is the private
  chat with yourself, which never appears in a dialog scan, so this needed the facade's `getSelfPeer`
  operation.
- **Shell**: `blocked`, `sessions`, `chat-action`, `pinned` and `unpin all` are available mid
  conversation. `help commands` now lists the CLI's full command set and marks which names work in
  the shell, instead of an ambiguous list that named commands the shell does not dispatch.

### Fixed

- **`config set --format=json` no longer demands `--api-id` and `--api-hash`.** The three options were
  declared `required` together, so changing a display setting forced re-typing (and shell-histories
  leaking) credentials. Each option is now individually optional: what is given is updated and what
  is not is preserved, so `--format` alone keeps the stored credentials, `--api-id` alone keeps the
  stored hash, and with nothing stored one half is refused until the other arrives. Bare `config set`
  is a usage error naming what can be updated and writes nothing, `config` and `config set --help`
  never require credentials, and an invalid `--format` value fails before anything is saved.

- **Message text piped to `send -` is decoded as UTF-8.** The dash form read through Clikt's
  terminal, whose reader follows the JVM's platform charset - Cp1252 here - so a pipe or redirect
  carrying UTF-8 was read as mojibake, and Telegram stored the mojibake. It now reads the bytes and
  decodes them as UTF-8, which is what every pipe and editor selection carries. Verified live: a
  Cyrillic body sent from a UTF-8 file round-trips through Telegram byte-for-byte.
- **Non-ASCII and emoji render correctly on Windows.** Output used to go through `System.out`, whose
  encoder follows the console code page, so anything outside it was replaced with `?` before the
  terminal saw it - no font or terminal setting could recover it. The interactive shell now writes
  through JLine's writer, which uses `WriteConsoleW` and needs no console change. A one-shot command
  sets the console code page to UTF-8 and swaps the streams to match, then restores the code page it
  actually read on exit. Both paths are strict no-ops when the environment is already correct, and
  piped, redirected and JSON output is byte-for-byte unchanged.
- **Emoji survive the facade's JNI boundary again.** The facade up to `v0.9.8` decoded JNI's Modified
  UTF-8 as standard UTF-8, so an astral-plane character was stored in Telegram as six replacement
  characters. This release builds on the fixed `v0.9.9` facade; a live send and read-back of a body
  mixing Cyrillic, an emoji and CJK is byte-for-byte identical to the bytes that went in.

## 0.3.1 - 2026-10-01

Built on the `kotlogram` `v0.9.7` tag, resolved from JitPack.

### Features

- A reply's quoted text is now shown. Received messages carry the text a reply quotes into the
  domain as `MessageQuote` (the text plus its own entities; the facade's own HTML/CommonMark
  renderings are dropped as the writer's concern), and the table renders it in a new `quote` column
  as `"<text>"` beside the `reply` id. The quote's entities go through the same styler as a
  message's own text, so they are styled with colour on a terminal and plain otherwise, and the
  column is measured by visible width like every other. A quote absent in the library - a non-reply,
  or a reply to a deleted message, a scheduled/service reply or a story - stays absent and renders an
  empty cell, never empty quotes. A newline in the quoted text becomes a space, and a quote longer
  than 80 visible characters is truncated with an ellipsis, so one long quote cannot break the row or
  stretch the column. The plain and JSON formats carry the quoted text with no escapes.

### Fixed

- `listen` stops promptly on Ctrl-C. The command used to poll the update stream with the facade's
  30 s timeout on a worker thread, so a Ctrl-C could take up to half a minute to return. It now
  drives the facade's own background update loop, which polls in short waits and joins its thread on
  stop, so a stop costs at most one short poll. `--once`, `--json` and the human rendering are
  unchanged, and the command no longer starts a worker thread of its own.

### Project

- The facade dependency moves from Maven Central's `io.github.j0s3f:kotlogramme` to JitPack's
  `com.github.J0s3f:kotlogram:v0.9.7`. A facade tag reaches JitPack before Maven Central catches up,
  so the client pins the tag it was tested against; `-PkotlogrammeVersion=...` still selects another.

## 0.3.0 - 2026-10-01

Built on `kotlogramme` 0.9.0.

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
- `send-file` shows an upload progress bar: one rewritten line with the percentage, the bytes sent,
  the total and the rate, erased when the upload finishes, fails or is interrupted. The bar is on by
  default where the output is a terminal and off where it is piped or redirected, so a script gets no
  carriage returns; the new `--progress` forces it on and `--no-progress` turns it off, with
  `--no-progress` winning as `--no-color` does over `--color`. Only `send-file` uploads bytes, so
  only `send-file` has the flags, and with `--no-color` the bar is plain ASCII. An upload too quick to
  reach the first tick draws nothing at all.
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

### Fixed

- `send-file @chat -` no longer corrupts a piped binary file. Piped bytes were decoded as UTF-8 text
  and rejoined line by line, so a `0xFF` byte arrived as a replacement character and a `0x0D` was
  dropped — twelve bytes in became seventeen bytes out. Piped input is now read as raw bytes from
  standard input, spooled to a temporary file and uploaded from there, so the bytes Telegram sees are
  the bytes that were piped. Spooling is what lets Telegram be told the total, since a pipe carries no
  length; it costs one pass through the temporary directory and keeps the upload streaming in chunks
  rather than holding the whole payload in memory. The spool file is deleted once the upload ends,
  whether it succeeded or failed.

### Project

- The facade dependency moves to `kotlogramme` 0.9.0 for this release; the upload counter the progress
  bar reads arrived in 0.8.0 and is unchanged.

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
