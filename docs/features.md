# Features

User-facing features that actually ship, newest first. A feature appears here once it is usable, not
when it is planned. See [`plan.md`](plan.md) for what is coming.

## Reading and writing (Phases 2 and 3)

A `<peer>` is an `@username`, a numeric dialog id or a Telegram invite link; the same resolution is
used by every command.

- `dialogs [--limit <n>]` — lists the conversations, newest first, with the unread count and a
  pinned marker.
- `history <peer> [--limit <n>] [--before <messageId>]` — reads a page of a chat's messages with the
  sender, the time, a reply marker and a media placeholder.
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
