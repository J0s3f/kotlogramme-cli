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
   rather than failing the whole call.
5. **`messagesRemoveReaction` fails with `REACTION_EMPTY caused by messages.sendReaction`.** Clearing
   a reaction sends an empty reaction vector, which this layer rejects. Removing a reaction needs the
   call the layer actually accepts (or the flag that marks the send as a removal).

Both library findings are worth a fix in `kotlogramme` plus a regression test; they were found here
because the client exercises the API for real.
