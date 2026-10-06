# Findings

Bugs and things worth fixing that turned up while working on the code, noted so they are not lost.
Each entry says where it is, why it matters, and how sure the note is. Remove an entry when it is
fixed, and say in the commit which one it was.

## Likely bugs

- **The name cache never evicts.** `UserNameCache` (`adapter/telegram/UserNameCache.kt:28`) treats an
  entry as gone once it expires, but only replaces it when the same id is asked for again. A `listen`
  that runs for weeks sees a growing set of ids, and expired entries stay in memory for good. Evict
  expired entries when one is added, or cap the size. Confirmed by reading; not measured.
- **`System.console() != null` is used as "this is an interactive terminal"**
  (`adapter/cli/AppContext.kt:267`). Since JDK 22 `System.console()` can return a console even when
  stdin and stdout are redirected, so colour, the upload progress bar and the Windows console fix may
  switch on inside a pipe. `Console.isTerminal()` or Mordant's `terminalInfo` answers the question
  properly. **Not verified** on a pipe; check before changing.
- **Wrappe unpacks into a new folder for every release and never cleans up.** The packed executable
  extracts to `%TEMP%\kotlogramme-windows-x86_64\<hash>` (`scripts/build-native-single.ps1:70`). The
  per-start leak is gone, but each new version leaves about 45 MB behind. Look at Wrappe's options for
  removing older unpack directories, or document the cache location.
- **`JsonConfigStore.load()` creates the configuration directory** (`adapter/config/JsonConfigStore.kt:21`).
  A read should not write; `doctor` and `--help`-style reads leave a directory behind. Create it in
  `save`, which already does.

## Gaps in coverage

- **FFM on Linux and macOS images is unverified.** JLine's shipped FFM metadata covers the libc calls
  but was not complete for Windows, so it may not be for the other two. `doctor` reports
  `FFM unavailable` as a warning there, and JLine falls back to its JNI libraries, so nothing breaks, but
  the CI logs for the Linux and macOS packed executables have not been read for that row.
- **The interactive shell is not exercised in CI.** The smoke test runs `shell` with piped input, which
  gives a dumb terminal, so which JLine provider a real console picks is only ever checked by hand.
- **Network check hard-codes a data centre address** (`adapter/cli/StandardDiagnostics.kt`,
  `149.154.167.51`). It worked when tried, but Telegram can change addresses. Prefer a name, or take the
  addresses from the facade.

## Inconsistencies with the project's own rules

- **A `Clock` port exists now, but `AdminCommands` still reads the system clock**
  (`adapter/cli/AdminCommands.kt:79`, `Instant.now()`). Move it to the port so the ban expiry is testable.
- **`NativeLibraryCheck.Loaded.facadeOrigin` is no longer used by anything that shows it**
  (`adapter/telegram/NativeLibraryProbe.kt:19`). Drop the field and the code that fills it, or use it.
- **`otherUpdate` decodes each raw update twice** (`adapter/telegram/KotlogramUpdateLoop.kt`): once for the
  JSON and once more to read its `user_id`. Decode once and read the id from the result.

## Smaller things

- **195 files are CRLF in the working tree and LF in the index**, so every `git add` on Windows prints
  `CRLF will be replaced by LF`. A `.gitattributes` with `* text=auto eol=lf` would end the noise.
- **Editing a docs file with a default-encoding script can write cp1252 bytes** (it happened once to
  `docs/decisions.md`). A CI check that the Markdown is valid UTF-8 would catch it.
- **Pushing a tag again re-runs the release workflow.** Moving a tag that already has a release starts a
  full rebuild that can only fail at "release already exists". Guard the first step against a tag whose
  release exists.
- **Pushing a branch and then fast-forwarding `main` to the same commit runs CI twice for one tree.** The
  second run on `main` could be skipped when the commit is already green.

## Found while wrapping tables

- **The native smoke test matches the wording of `doctor`'s human-readable table.** It broke twice in one
  day, once when a phrase was removed from a row. It should read `doctor` through the JSON format
  (`config set --format json`, then check the `check` and `status` fields), which does not change when
  the wording is polished.
- **Wrapping cuts paths mid-name.** A path with no spaces is cut at the column edge. Breaking after a
  `\` or `/` first would read better; the cut is only a fallback for a segment that is itself too wide.
- **Only SGR escapes are tracked when a styled cell wraps.** Other escapes, such as OSC 8 hyperlinks, are
  treated as text, so a long linked cell would be measured wrongly. Check whether any renderer emits them.
- **Each table printing creates a Mordant `Terminal` on first use.** That is lazy, so a command that
  prints no table pays nothing, but the cost of one on startup has not been measured.

