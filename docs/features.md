# Features

User-facing features that actually ship, newest first. A feature appears here once it is usable, not
when it is planned. See [`plan.md`](plan.md) for what is coming.

## Pagination

Every listing Telegram can return in more than one page takes `--after <cursor>` to continue from a
previous page — the cursor is the opaque string the last page printed after its table as
`# next: --after <cursor>`, which you paste back verbatim. A full page prints that line; a short one
does not, so its absence means you are done. A malformed cursor is a usage error, never a silent
first page. `contacts`, `sessions`, `folders` and `stickers` arrive whole in one call, so they have no
cursor.

- `dialogs`, `blocked`, `chat-photo-history` and `profile-photos` also take `--all`, which walks the
  whole set in one call and wins over both the cursor and `--limit`. For `dialogs`, `--all` is the
  first page followed by the rest, so the Saved Messages anchor appears exactly once at the top; a
  paged continuation (`--after`) does not repeat it.
- `history` already had `--before <id>`; `--after <id>` is an accepted alias for the same thing.
- The shell's `read`, `search`, `files`, `members` and `blocked` verbs take `--after` too (and
  `blocked` takes `--all`), and `help <verb>` lists the new options.

The cursor format is per listing shape: a message-id listing (`history`, `search`, `list-files`,
`chat-photo-history`) uses the last row's message id; an offset-index listing (`blocked`,
`profile-photos`, `members`) uses the next index, the previous offset plus the rows returned; and
`dialogs` uses `<peerId>:<topMessageId>:<epochMillis>` from the last row.

## Media (Phases 4 and 7)

- `send-file <peer> <path|-> [--caption <text>] [--photo] [--video] [--detect] [--no-detect]
  [--duration <seconds>] [--width <px>] [--height <px>] [--name <name>] [--reply-to <id>]
  [--silent]` — uploads a local file. The kind is detected from the file by default: the extension
  decides (`jpg`, `jpeg` and `png` are photos; `mp4`, `m4v`, `mov`, `mkv`, `webm` and `avi` are
  videos; anything else is a document), and a video also reads its duration, width and height by
  parsing the container — an ISO base media file (MP4, M4V, MOV) or a Matroska/WebM one (MKV, WEBM)
  — so a plain `send-file @chat clip.mp4` goes out as a streamable video that Telegram plays in
  place, with its metadata, and a plain `send-file @chat cat.png` goes out as a photo. `--detect`
  spells that default out and `--no-detect` turns it off, sending the bytes as a plain document with
  no kind inference and no metadata. `--photo` and `--video` force the kind and win over detection;
  each is mutually exclusive with the other and with `--detect`/`--no-detect`. The video's
  `--duration`, `--width` and `--height` describe it and override the probe; they are only accepted
  together with `--video` or `--detect`. An AVI video is still sent as a video, just without
  metadata, because this build does not parse that container, and a video whose container cannot be
  read still goes as a video with what is known. A `-` path reads standard input as raw bytes and
  uploads it as a plain stream named by `--name`, defaulting to `stdin`, so
  `cat cat.png | kotlogramme send-file @chat -` works and the bytes arrive exactly as they were
  piped; a piped upload is never streamable, so it is sent as a document, or as a photo with
  `--detect` when `--name` names one. `--progress` and `--no-progress` choose whether the upload shows
  a bar: it is on by default where the output is a terminal and off where it is piped or redirected,
  `--progress` forces it on, `--no-progress` turns it off and wins if both are given.
- `send-media-url <peer> <url> [--caption <text>] [--photo] [--reply-to <id>] [--silent]` — lets
  Telegram fetch the URL and send it, so nothing is uploaded from this machine.
- `copy-media <peer> <messageId> [--caption <text>] [--reply-to <id>] [--silent]` - re-sends the
  media of an existing message without uploading it again.
- `download-media <peer> <messageId> [<target>]` - writes a message's media to a local file. Without a
  target the file is named after the media's own name and falls back to the message id, landing in the
  working directory; missing parent directories are created and an existing file is overwritten. It
  prints the path and the byte count. A message with no media, a peer that does not resolve and a
  message id Telegram cannot find are each a one-line error with a non-zero exit code.
- `list-files <peer> [--kind <kind>] [--limit <n>] [--total]` - lists a chat's files, filtered by media
  kind **on Telegram's side** rather than by scanning history, so it reaches the whole chat and not
  just the part a history read has covered. `--kind` names the filter (`photo`, `video`, `photo-video`,
  `document`, `audio`, `voice`, `gif`, `animation`); an unknown kind is refused with the valid names
  rather than silently matching nothing. `--limit` caps the page and `--total` prints just the count.
  The listing renders as the same table every other command uses, and as JSON through the configured
  output format.

A message's media upload shows progress as one rewritten line — the percentage, the bytes sent, the
total and the rate — while it is on screen. The line is erased when the upload finishes, fails or is
interrupted, and an upload too quick to reach the first tick draws nothing at all, so a fast upload
leaves no trace and a pipeline that piped the output gets no carriage returns.

`--reply-to <id>` quotes an existing message and `--silent` sends without a notification; all three
media commands carry both, and so does a stdin stream.

Media in a message view is labelled compactly by kind: a `video` or `animation` shows its duration
and dimensions (`[video 0:03 320x240]`), an `audio` or `voice` its duration (`[audio 3:21]`), a
`photo` its dimensions (`[photo 320x240]`) and a `document` its size (`[document 1.6 MB]`). Any
other kind shows just the kind, and a detail the media does not carry is left out rather than shown
as a placeholder.

A message's formatting is rendered in the table's text column: `bold`, `italic`, `underline` and
`strike` use their terminal attribute; `code` and `pre` are cyan; `url` and `textUrl` are underlined
in blue; `mention`, `mentionName` and the auto-detected kinds (`hashtag`, `cashtag`, `botCommand`,
`email`, `phone`, `bankCard`) are cyan. A kind a terminal cannot express, such as `blockquote` or a
custom emoji, is left as plain text. A spoiler has no terminal equivalent, so it is not printed: each
of its characters becomes a block (`█`), which keeps it hidden and keeps the column aligned. An
entity whose span is out of range for the text is ignored rather than throwing. Colour is used only
for the table format on a terminal, and only when neither `NO_COLOR` nor the global `--no-color` flag
is set; the global `--color` forces it on for a caller that is not a terminal, such as a pager, and
`--no-color` wins if both are given. The plain and JSON formats always carry the raw text with no
escapes. Because the table
measures a column by its visible width, the escapes never make the borders drift.

A reply's quoted text is shown in the table's `quote` column as `"<text>"`, beside the id in the
`reply` column. The quote carries its own formatting entities, and they go through the same styler as
a message's own text, so a bold word inside a quote is bold and colour is still only used for the
table format on a terminal. A reply whose header carries no text — a reply to a deleted message, a
scheduled or service reply, a reply to a story — is `null` in the library and renders as an empty
cell, never as empty quotes. A newline inside the quoted text becomes a space so the row stays one
line, and a quote longer than 80 visible characters is truncated with an ellipsis so it cannot stretch
the column. The plain and JSON formats carry the quoted text without escapes as well.

A message view's `via` column names the inline bot a message came through as `@username`. A history
page resolves the distinct `viaBotId`s on the page in one batched lookup, so the page costs at most
one extra request. An id Telegram cannot resolve, a user without a username, or a failed lookup
falls back to the bare numeric id and never fails the command. No other path pays for it: `listen`,
search and the send commands never do the lookup.

A missing file, a blank URL and a non-positive message id are reported as one-line usage errors, not
stack traces.

## Admin and ban rights (Phase 7)

- `permissions <peer> <user>` — shows the member's admin rights as granted and denied, one right per
  row, in the configured output format.
- `promote <peer> <user> [--grant <list>] [--all]` — promotes a member with the named rights, or
  every right with `--all`. The rights are `change-info`, `post-messages`, `edit-messages`,
  `delete-messages`, `ban-users`, `invite-users`, `pin-messages`, `add-admins`, `anonymous` and
  `manage-call`; an unknown name is a usage error.
- `restrict <peer> <user> [--allow <list>] [--forever]` — bans a member, taking away every ability
  for 24 hours by default; `--allow` keeps the named abilities and `--forever` removes the expiry.
  The allowed abilities are the restriction flags (`view-messages`, `send-messages`, `send-media`,
  `send-stickers`, `send-gifs`, `send-games`, `send-inline`, `embed-links`, `send-polls`,
  `change-info`, `invite-users`, `pin-messages`).

A member's rights are read in two steps: the facade's role check decides whether they hold any, and
the participant listing supplies the granular set; a member beyond the listing's lookup limit falls
back to their role.

## Interactive shell and folders (Phases 6 and 7)

- `shell` — starts an interactive, readline-style session. It completes command names and peer
  references, keeps a history file under the config directory, shows the current chat in the prompt
  and keeps the last messages of that chat in view. Commands: `help`, `dialogs`/`list`,
  `open <peer>`, `read [--limit N]`, `send <text...>`, `reply <id> <text...>`, `contacts`,
  `search <query>`, `files [<peer>] [--kind <kind>] [--limit N]`,
  `download-media <peer> <message-id> [target]`, `stickers`, `sticker-set <set>`,
  `send-sticker <set> <index>`, `inline <bot> <query> [--send <index>]`, `members [<peer>]`,
  `folders`, `quit`/`exit`. It dispatches to the same use cases as the one-shot commands, so the
  two modes share behaviour and rendering, but the shell is not a mirror of the CLI: it exposes the
  verbs `Shell.kt` names, and a command is available in the shell only once a matching verb is
  added there. Many one-shot commands, `shell` itself among them, have no shell verb.
- `folders` — lists the account's dialog folders with their kind and how many peers each pins,
  includes or excludes.

The shell's sticker, inline and member commands act on the current chat the prompt shows —
`send-sticker`, `inline --send` and a bare `members` all default to it. The shell's `inline` takes
no `--to`, because the current chat is already the destination.

## Reading and writing (Phases 2 and 3)

A `<peer>` is an `@username`, a numeric dialog id or a Telegram invite link; the same resolution is
used by every command.

- `dialogs [--limit <n>]` — lists the conversations, newest first, with the unread count and a
  pinned marker.
- `history <peer> [--limit <n>] [--before <messageId>]` — reads a page of a chat's messages with the
  sender, the time, a reply marker, the text the reply quotes, a media label (for example
  `[video 0:03 320x240]`) and, for a
  service message, a human phrase for the action (for example "pinned a message" or "added a
  member"). A kind this build does not name still renders as `service action: <kind>` rather than as
  a blank row.
- `send <peer> <text...> [--reply-to <id>] [--silent]` — sends a text message. Pass `-` as the text
  to read the whole message from standard input, so `echo hello | kotlogramme send @chat -` works.
- `edit <peer> <messageId> <text...>` — replaces the text of a message.
- `delete <peer> <messageId...>` — deletes one or more messages.
- `forward <fromPeer> <messageId...> --to <toPeer>` — forwards one or more messages.
- `pin <peer> <messageId>` / `unpin <peer> <messageId>` — pins or unpins a message.
- `react <peer> <messageId> <emoji>` / `unreact <peer> <messageId>` — adds or removes a reaction.
- `mark-read <peer>` — marks every message in a chat as read.

Lists honour the configured output format: a padded table, tab-separated plain text or JSON. Bad
input — a blank message or a non-positive id — is reported as a one-line usage error, not a stack
trace.

## People, search and updates (Phase 5)

- `contacts [--limit <n>]` — lists the account's contacts with their username and phone number.
- `search <query> [--in <peer>] [--limit <n>] [--total]` — searches message text, globally or in one
  chat; `--total` prints just the number of matches.
- `members <peer> [--limit <n>]` - lists a chat's members with their role.
- `invite <peer> <user>` - adds a member to a channel, supergroup or group.
- `kick <peer> <user>` - removes a member from a chat.
- `listen [--once] [--json]` — follows the live update stream, printing each update as it arrives.
  `--once` stops after the first update, which is what makes it scriptable; `--json` prints one JSON
  object per line; Ctrl-C ends the stream cleanly instead of killing the process mid-print. The
  stream is followed on the facade's own background update loop, which polls in short waits and joins
  its thread on stop, so a stop costs at most that one short poll rather than the facade's 30 s one.
- `stickers` — lists the installed sticker sets with their short name, title, count and flags.
- `sticker-set <set>` — shows one set with its stickers numbered, which is the index `send-sticker`
  takes. The set is named by short name, or `id:accessHash` for one that is not installed.
- `send-sticker <peer> <set> <index> [--reply-to <id>] [--silent]` — sends one sticker from a set.
- `inline <bot> <query> [--in <peer>] [--send <index> --to <peer>]` — asks an inline bot and prints
  its numbered results with the id, type, title, description and text of each. Adding `--send` and
  `--to` posts that result in the same call, because Telegram expires the query id the send needs.

## Diagnostics (Phase 8)

- `doctor` — checks that the bundled native library can be loaded on this machine. It builds the
  facade client once through the same factory the real commands use (which loads the library) and
  closes it immediately, so it never contacts Telegram. It prints the API id, the session path, the
  library status and where the facade classes came from (which is the fat jar when run from
  `kotlogramme-all.jar`). A missing or incompatible library is reported as a one-line error, never
  as an `UnsatisfiedLinkError` stack trace.

## Authentication (Phase 1)

- `config` — shows the resolved config directory, session path, whether Telegram API credentials are
  set (the API hash is never printed) and the output format.
- `config set --api-id <id> --api-hash <hash> [--format table|plain|json]` — stores the API
  credentials from `my.telegram.org` and the output format.
- `login [--phone <number>] [--code <code>] [--password <2fa>] [--bot-token <token>]` — signs in as a
  user (login code, then the 2FA password only if Telegram asks) or as a bot; prompts for whatever is
  not given on the command line.
- `logout` — signs the current session out.
- `whoami` — prints the signed-in account, or a clear "not signed in" line.
- Global `--config-dir <path>` to use a different configuration directory, and `--version`.

Credentials are read from the config first and fall back to the `TG_API_ID` / `TG_API_HASH`
environment variables. Without either, commands that need Telegram explain how to provide them
instead of failing with a stack trace.

## Standalone native packages

Native distributions require no JRE or JDK. Windows x86_64 uses zip; Linux and macOS on x86_64 and
ARM64 use tar.gz, with the executable, support libraries and license notices. Build the host's
package with `nativeDist`; Windows has `scripts/build-native-windows.ps1` for the supplied GraalVM
installation. CI uses Liberica NIK 25 and runs offline startup, configuration, native-library and
scripted-shell checks. Releases attach all five packages after every native build succeeds. Windows
ARM64 remains covered by the JVM distribution because NIK has no native compiler for that platform.
See [native-image.md](native-image.md) for requirements and verification limits.