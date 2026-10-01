# Live test findings

Run on 2026-09-30 against real Telegram, using the pre-authorized user session and the test
supergroup `@kotlogramme_test` (credentials in the library repository's `.secret`).

## Worked

- `whoami` — authenticated from the persisted grammers session and printed the account.
- `dialogs --limit N` — the real dialog list, with unread counts and truncated last-message previews.
- `history @kotlogramme_test --limit 5` — the real history, including the library's own live-test
  messages.
- `send @kotlogramme_test "…"` — sent a message and returned the stored message.
- `search "kotlogramme" --limit 5` — global search across chats.
- `members @kotlogramme_test` — the supergroup's members and roles.
- `kick @kotlogramme_test @…` — removed a member (verified by `members`).
- `edit` / `delete` — edited and deleted a message and returned the count.
- `react @kotlogramme_test <id> U+2764` / `unreact` — added and cleared a reaction.
- `folders` — the real dialog folders, after the library fix below.
- `contacts` — an empty list, correct for this account.

## Findings and their resolution

| # | Finding | Owner | State |
| --- | --- | --- | --- |
| 1 | A rejected request dumped a Java stack trace. | client | **fixed** — `main` catches `TelegramException` and unexpected runtime errors and prints one line, exit 1. Verified: `react … not-an-emoji` → `Error: … REACTION_INVALID …`, exit 1, no trace. |
| 2 | Output and input were not UTF-8 on Windows. | client | **fixed** — the launcher forces `stdout`/`stderr` to UTF-8, and `react` accepts a `U+1F44D` or `\uD83D\uDC4D` escape so an emoji survives a non-UTF-8 command line, which is what made the emoji reachable at all. Cyrillic and emoji now render. |
| 3 | The dialog table padded the preview column to the longest message. | client | **fixed** — `previewOf` collapses whitespace and truncates to 60 characters with an ellipsis; the clean-build live run shows a readable table. |
| 4 | `messagesGetDialogFilters` failed with `CHANNEL_PRIVATE`. | library | **fixed** (`kotlogramme` `b8b17fda`) — a folder can reference an inaccessible channel; `peers_dto` now skips the peer it cannot resolve instead of failing the listing. Verified live. |
| 5 | `messagesRemoveReaction` appeared to fail with `REACTION_EMPTY`. | — | **not a bug** — grammers' `InputReactions::remove()` (an empty vector) is correct. The failures came from removing a reaction that was not there, and from the channel allowing only a subset of reactions (👍 🔥 🎉 are rejected with `REACTION_INVALID`; ❤️ works). An attempted rework was reverted as unverified. |

## Accident, and the gap it exposed

The `kick` smoke test was run without a second thought and removed the test bot
(`@KotlogrammeDevBot`) from `@kotlogramme_test`. The client had **no `invite` capability** at all:
the facade exposed join, leave, participants and kick, but nothing that adds a member. That was an
oversight in the parity work rather than a Telegram limitation.

It is now fixed end to end: `channelsInviteToChannel` was added to the facade (0.3.0), the client
gained `invite <peer> <user>`, and the bot is back in the supergroup as verified by `members`. The
library's `LiveTelegramIntegrationTest` environment is whole again.

## Second pass, after the fixes

Re-run against the built distribution, resolving `kotlogramme` 0.3.0 from Maven Central.

Also verified:

- `permissions <peer> <user>` — the member's rights as granted/denied.
- `search <query> --in <peer>` — per-chat search.
- `mark-read <peer>`.
- `pin`, `unpin`, `forward --to`, `delete` (including deleting two ids at once).
- Bot login: `login --bot-token …` then `whoami` reports the bot account.
- `listen --once`: with the bot listening and the user sending it a direct message, the update was
  printed (`kind`, `chat`, `message_id`, `from`, `time`, `text`) and the command exited by itself.

### New finding

**A bot cannot resolve a numeric peer id.** `send <numericId>` from a bot session fails with
`BOT_METHOD_INVALID caused by messages.getDialogs`: the reference resolver looks a numeric id up in
the dialog list, and bots may not call `messages.getDialogs`. A user session is unaffected. The fix
belongs in `ChatReferenceResolver`: fall back to a direct peer lookup when the dialog listing is not
available, or report the bot's restriction clearly. Recorded as a client task.

Resolving `@username` from a bot is a separate Telegram restriction (`PEER_ID_INVALID` until the bot
has seen the user), and is expected behaviour rather than a defect.

## Third pass, after the Phase 9 features

Run against the fat jar, first resolving `kotlogramme` 0.5.0 and then 0.6.0 from Maven Central, to
confirm the four Phase 9 commits against real Telegram. Messages 33-40 were left in
`@kotlogramme_test`, as agreed.

Verified:

- `send-file clip.mkv --detect` → `[video 0:05 640x480]`, matching `ffprobe` on the same bytes
  (5.000 s, 640x480). The WebM twin read correctly too (`3.52 s`, `320x240`) once probed directly.
- `send-file clip.webm --detect --reply-to 33 --silent` → the reply column shows `33`.
- An inline-bot result and a sticker both arrived via `inline @bold … --send 1` and
  `send-sticker pepe_frog 1`, in the chat the prompt had open.
- The shell's `members`, `folders`, `stickers` and `help` all work in interactive mode.

### Findings

| # | Finding | Owner | State |
| --- | --- | --- | --- |
| 6 | A video document was read back as `[document]`. | library | **fixed by 0.6.0** — the layer reports `messageMediaDocument` for a video, and 0.5.0 passed that variant name straight through, so `send-file --detect` on an MKV came back as `[document 33 KB]` and the terminal could not tell a video from a text file. 0.6.0 derives the kind from the document itself, and the same message now reads `[video 0:05 640x480]`. This is what made the version bump load-bearing rather than cosmetic. |
| 7 | WebM/VP9 uploads keep no video metadata. | Telegram | **accepted** — the same 3.52 s 320x240 VP9 file uploaded with `--detect` and again with explicit `--video --duration 3.52 --width 320 --height 240` both came back as a bare `[video]`, while its H.264/Matroska sibling kept its duration and resolution. The client sends identical attributes for both, so the loss is Telegram's, not the client's. The file still arrives as a video; it simply has no duration or dimensions to show. |
| 8 | The `via` column shows a numeric id, not `@username`. | client | **closed by 0.7.0** — the facade gained `usersGetUsers(ids)`, so the client resolves the distinct `viaBotId`s of a history page in one batched lookup and renders `@username`; an id Telegram cannot resolve or a failed lookup still falls back to the numeric id. Verified by the offline suite; the live pass is the orchestrator's. |

Finding 6 is worth keeping in mind for any future client work: the label a message carries is the
library's projection, so a client on an older release can look broken while being perfectly correct.

## Fourth pass, after the Phase 10 features

Run against the fat jar from `main`, resolving `kotlogramme` 0.7.0.

- **The `via` column resolves.** Messages 36 and 38, which are inline-bot answers, now read
  `via @bold` instead of the bare id `107705060`.
- **A received entity is styled.** Message 41 carries a link Telegram itself added, and with colour
  forced it renders as `ESC[4;34mhttps://example.com/pageESC[24;39m` — underline and blue — with the
  table borders exactly where they were. Messages 36 and 38 are the bot's *Italic* answer and render
  as `ESC[3m…ESC[23m`, so an entity coming from an inline result is projected too, not just one the
  server generated.
- **Nothing leaks into a pipe.** Without colour the same rows contain no `ESC` byte at all, which is
  the property a script consuming the output depends on.

The `--color` flag was added during this pass: piping to `less -R` otherwise lost the styling
entirely, and a captured shell has no terminal, so it was also the only way to check the rendering
above. `--no-color` still wins when both are given.


## Fifth pass, the piped-input corruption and the new file commands

Run against the fat jar from `main`, resolving `kotlogramme` 0.8.0, on 2026-10-01.

### The piped-binary bug, found and fixed in this pass

`send-file @chat -` read standard input as UTF-8 *text*: `readStdin()` was
`terminal.readLineOrNull(false)` in a sequence joined with `\n`. That is wrong for bytes, and it failed
in two different ways depending on the input.

| Input | Before | After |
| --- | --- | --- |
| 36 bytes containing `0x00`, `0x0D`, `0xFF`, `0xFE` (a PNG) | **no upload at all** - `java.nio.charset.MalformedInputException: Input length = 1`, an uncaught stack trace and a non-zero exit | message 45, `[document 36 B]`, byte-identical round trip |
| 19 bytes containing a lone `0x0D` and a `0x0D 0x0A` end | message 44, `[document 17 B]` - two bytes silently dropped, no warning | message 46, `[document 19 B]`, byte-identical round trip |

Both halves matter. The invalid-UTF-8 case does not corrupt anything because it never gets as far as
sending; the valid-UTF-8 case corrupts silently, which is the worse failure for a user because nothing
signals it.

The fix reads raw bytes from `System.in`, spools them to a temporary file and uploads from a
`FileInputStream` over it, deleting the spool in a `finally`. Spooling is what lets Telegram be told
the total - a pipe carries no length - and it keeps the upload streaming in bounded chunks instead of
holding the payload in memory, so a large piped file is not a heap problem.

**Evidence.** The round trip was checked by downloading each message back with the new `download-media`
command and comparing SHA-256:

```
piped in        736C4656CCB13EAE4783692E7CEF99C6F4A64A1F19311DF7DC6FE5E4B5C3EBF0  36 B
downloaded      736C4656CCB13EAE4783692E7CEF99C6F4A64A1F19311DF7DC6FE5E4B5C3EBF0  36 B
```

The corrupted message 44 is still in the channel at its original 17 bytes, which is a useful
before/after pair to look at side by side with message 46.

### `list-files`

The server-side filter is real: `list-files @kotlogramme_test --kind document --limit 10` reached
messages **46, 45, 44, 43, 39, 35, 34, 29, 28, 25** in one call - the whole channel, back past a
twenty-message window, including a 1.6 GB video. A client-side scan of the last page would have
stopped around message 30 and reported a false negative. `--kind video` and `--total` (4) both answer
correctly, and an unknown kind is refused with the valid names rather than silently matching nothing.

### The upload progress bar, checked live

`send-file` gained a progress bar in this release. Both directions were checked against the real
channel with a 28 MB file.

**Off by default when piped.** A plain `send-file` with stdout captured produced **no carriage return
and no percent sign at all** - 0 CRs in the captured stream - so a script or a pipeline gets clean
output with nothing to strip. This is the property the default exists for.

**`--progress` forces it on.** Captured to a file and read back as bytes, the same upload produced
**34 `0x0D` bytes and 0 `0x1B` (ESC) bytes**: one line rewritten in place, with no ANSI styling
because the output is not a terminal. The line carries the percentage, the bytes sent against the
total and the transfer rate:

```
[#-------------------]  7% 2.0 MB/28 MB  22 MB/s
[##------------------] 10% 3.0 MB/28 MB 9.9 MB/s
[####----------------] 23% 6.5 MB/28 MB  11 MB/s
```

The first tick reads high because the rate is a running average taken over a very short elapsed time;
it settles to the real figure within a few ticks. That is the intended behaviour rather than a
glitch, and the finished line is erased on success, failure and interrupt alike.
