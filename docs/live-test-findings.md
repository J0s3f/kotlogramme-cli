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

