# Features

User-facing features that actually ship, newest first. A feature appears here once it is usable, not
when it is planned. See [`plan.md`](plan.md) for what is coming.

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
  `search <query>`, `quit`/`exit`. It dispatches to the same use cases as the one-shot commands, so
  anything available there is available in the shell.
- `folders` — lists the account's dialog folders with their kind and how many peers each pins,
  includes or excludes.

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

## People, search and updates (Phase 5)

- `contacts [--limit <n>]` — lists the account's contacts with their username and phone number.
- `search <query> [--in <peer>] [--limit <n>] [--total]` — searches message text, globally or in one
  chat; `--total` prints just the number of matches.
- `members <peer> [--limit <n>]` - lists a chat's members with their role.
- `invite <peer> <user>` - adds a member to a channel, supergroup or group.
- `kick <peer> <user>` - removes a member from a chat.
- `listen [--once] [--json]` — follows the live update stream, printing each update as it arrives.
  `--once` stops after the first update, which is what makes it scriptable; `--json` prints one JSON
  object per line; Ctrl-C ends the stream cleanly instead of killing the process mid-print.
- `stickers` — lists the installed sticker sets with their short name, title, count and flags.
- `sticker-set <set>` — shows one set with its stickers numbered, which is the index `send-sticker`
  takes. The set is named by short name, or `id:accessHash` for one that is not installed.
- `send-sticker <peer> <set> <index> [--reply-to <id>] [--silent]` — sends one sticker from a set.

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
