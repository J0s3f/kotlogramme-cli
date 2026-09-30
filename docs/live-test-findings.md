# Live test findings

Run on 2026-09-30 against real Telegram, using the pre-authorized user session and the test
supergroup `@kotlogramme_test` (credentials in the library repository's `.secret`).

## Worked

- `whoami` — authenticated from the persisted grammers session and printed the account.
- `dialogs --limit 12` — the real dialog list, with unread counts and last messages.
- `history @kotlogramme_test --limit 5` — the real history, including the library's own
  live-test messages.
- `send @kotlogramme_test "…"` — sent a message and returned the stored message.
- `search "kotlogramme" --limit 5` — global search across chats.
- `members @kotlogramme_test` — the supergroup's members and roles.
- `edit @kotlogramme_test 8 "…"` — edited a message and returned the updated one.
- `delete @kotlogramme_test 8` — deleted it and reported the count.
- `contacts` — returned an empty list, which is correct for this account.

## Bugs found, by owner

### Client

1. **Unhandled errors print a Java stack trace.** `folders`, `react` and `unreact` each dumped
   `Exception in thread "main" org.kotlogramme.TelegramException: …` and the stack. The client must
   catch Telegram (and unexpected) errors in `main`, print a one-line message and exit non-zero.
2. **Output and input are not UTF-8 on Windows.** Cyrillic chat titles rendered as `?`, and a
   reaction emoji passed on the command line arrived mangled (Telegram answered `REACTION_INVALID`
   for `messages.sendReaction`). Force UTF-8 for stdin/stdout/stderr, both in the launcher and in
   `ConsoleOutput`.
3. **The dialog list table pads the preview column to the longest message**, so one long message
   makes the table thousands of columns wide and multi-line content breaks the rows. The preview
   must be collapsed to one line and truncated (the domain `Chat.lastMessagePreview` or the
   renderer, whichever keeps the domain honest).

### Library (`kotlogramme`)

4. **`messagesGetDialogFilters` fails with `CHANNEL_PRIVATE caused by channels.getChannels`.** The
   folder projection resolves every peer through `channels.getChannels`, and one folder references
   a channel this account cannot access. A folder listing should skip or mark inaccessible peers
   rather than failing the whole call. **Fixed** in `kotlogramme` (`b8b17fda`): `peers_dto` now
   skips the peer it cannot resolve, and the live test returns the real folders again.
5. **`messagesRemoveReaction` fails with `REACTION_EMPTY caused by messages.sendReaction`.** Clearing
   a reaction sends an empty reaction vector, which this layer rejects. **Open.** The live test
   could not separate two causes, because `react` itself cannot run on this machine: Windows
   decodes command-line arguments in the ANSI code page, so the emoji never reaches the JVM as an
   emoji and Telegram answers `REACTION_INVALID`. An attempted fix (omit the vector instead of
   sending it empty) was reverted as unverified, since grammers' `InputReactions::remove()` is a
   deliberate API and the empty vector may well be the correct form when a reaction is present.

## Status

| Finding | Owner | State |
| --- | --- | --- |
| 1 stack-trace dumps | client | open |
| 2 not UTF-8 (output and input) | client | open |
| 3 dialog table width | client | open |
| 4 folders `CHANNEL_PRIVATE` | library | fixed (`b8b17fda`) |
| 5 reaction removal | library | open, blocked on 2 |

Finding 2 blocks finding 5: the client needs a way to express an emoji that survives a non-UTF-8
command line (for example a `U+1F44D` escape that the client decodes), after which `react` and
`unreact` can be exercised live.
