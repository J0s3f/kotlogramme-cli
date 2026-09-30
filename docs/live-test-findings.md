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

## Accident to repair

The `kick` smoke test was run without a second thought and removed the test bot
(`@KotlogrammeDevBot`) from `@kotlogramme_test`. The client has no `invite` capability, so it could
not be restored programmatically: the facade exposes `channelsJoinChannel`, `channelsLeaveChannel`,
`channelsGetParticipants` and `channelsKickParticipant`, but nothing that adds a member
(`channels.inviteToChannel` / `messages.addChatUser`).

Two consequences:

- The library's `LiveTelegramIntegrationTest` needs the bot in that supergroup, so it will fail
  until the bot is re-added (from the Telegram app, or by a new `invite` operation).
- **`invite` is a real gap** and belongs in Phase 7 alongside `members`/`kick`.
