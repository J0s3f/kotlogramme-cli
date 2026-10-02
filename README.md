# kotlogramme-cli

A command-line Telegram client written in Kotlin, built on the
[`kotlogramme`](https://github.com/J0s3f/kotlogram) facade.

It aims to be two things at once: a genuinely useful, scriptable client for the terminal, and the
reference consumer that exercises the whole `kotlogramme` surface so its gaps are found by a real
application.

> **Status: usable.** Phases 1-9 are implemented: authentication, configuration, dialogs, history,
> sending and editing, media and files, contacts, search, chat members, dialog folders, live
> updates, an interactive shell, admin rights, stickers and inline. The current release is
> [`v0.3.1`](https://github.com/J0s3f/kotlogramme-cli/releases/tag/v0.3.1), carrying the fat jar and
> the distribution; it builds on the `kotlogram` `v0.9.11` tag from JitPack. Later additions beyond
> the original phases:
> `download-media`, `list-files` with a server-side media-kind filter, and an upload progress bar for
> `send-file`. Received messages render their formatting, and a message's inline bot is
> resolved to `@username`; see [`docs/features.md`](docs/features.md) for what ships and
> [`docs/plan.md`](docs/plan.md) for what is left (album sends, chunked download with a progress view,
> blocking, and the bot-safe numeric peer lookup).

## Quick start

```bash
kotlogramme config set --api-id <id> --api-hash <hash>   # from my.telegram.org
kotlogramme login --phone +491700000000                  # prompts for the code, then 2FA if set
kotlogramme dialogs --limit 20
kotlogramme history @some_chat --limit 50
kotlogramme send @some_chat "hello from the terminal"
kotlogramme send-file @some_chat clip.mp4                  # detects the kind: a streamable video
kotlogramme send-file @some_chat clip.mp4 --no-detect       # ...or send the raw bytes as a document
cat photo.png | kotlogramme send-file @some_chat - --name photo.png   # ...or pipe the bytes on stdin
kotlogramme list-files @some_chat --kind video             # the chat's videos, filtered by Telegram
kotlogramme download-media @some_chat 12345                # save a message's media, named after it
kotlogramme stickers                                       # installed sticker sets
kotlogramme listen                                         # follow new messages
kotlogramme shell                                          # interactive REPL
```

`listen [--once] [--json]` follows the live update stream. `--once` stops after the first update,
`--json` prints one object per line, and Ctrl-C ends the command cleanly. The stream is followed on
the library's own background update loop, which polls in short waits and joins its thread on stop, so
a stop costs at most one of those short polls rather than the library's 30 s one.

`list-files <peer>` lists a chat's files, filtered by media kind on Telegram's side rather than by
scanning history, so it reaches the whole chat and not just the part a history read has covered.
`--kind` names the filter (`photo`, `video`, `photo-video`, `document`, `audio`, `voice`, `gif`,
`animation`), `--limit` caps the page, and `--total` prints just how many files the chat holds.

`download-media <peer> <message-id> [<target>]` writes a message's media to a local file. Without a
target the file is named after the media's own name, falling back to the message id, and lands in
the working directory; missing parent directories are created and an existing file is overwritten.
The command prints the path and the byte count, so a script can read either.

Every command renders as a table by default and can be switched to `--format plain` or `--format
json` (via `config set --format`) for scripting. `kotlogramme doctor` checks the installation,
including that the bundled native library loads.

## Upload progress

`send-file` draws a one-line progress bar while the bytes go out, showing the percentage, the bytes
sent so far, the total and the current rate:

```bash
kotlogramme send-file @some_chat clip.mp4
```

The bar is on by default when the output is a terminal, and off when it is piped or redirected, so a
script gets no carriage returns in its output. `--progress` forces it on and `--no-progress` turns it
off; `--no-progress` wins if both are given, exactly as `--no-color` wins over `--color`. Only
`send-file` uploads bytes, so only `send-file` has these flags. With `--no-color` the bar is plain
ASCII. An upload that finishes before the first tick draws nothing at all, and the line is erased
whether the upload finished, failed or was interrupted with Ctrl-C.

Piped input is spooled to a temporary file before the upload starts, because Telegram has to be told
the total before the first part is sent and a pipe carries no length. The spool file is what makes a
`cat photo.png | kotlogramme send-file @some_chat -` upload the exact bytes that were piped, and it
also gives the bar a real total, so a piped upload gets a percentage too. The spool file is deleted
once the upload ends, whichever way it ended. The one cost is that the bytes pass through the
system's temporary directory once: that costs disk I/O rather than memory, and the upload itself
still streams in chunks rather than holding the whole file in the heap.

## Getting the client

The current release is [`v0.3.1`](https://github.com/J0s3f/kotlogramme-cli/releases/tag/v0.3.1). Its
GitHub Release carries two assets, both with every dependency and all six native libraries bundled,
so nothing else has to be downloaded:

- **`kotlogramme-all.jar`** - a single runnable fat jar. Run it with JDK 25:
  ```bash
  java -jar kotlogramme-all.jar --help
  ```
- **`kotlogramme-0.3.1.zip`** - the `distZip` distribution. Unpack it and run
  `kotlogramme-0.3.1/bin/kotlogramme` (or `kotlogramme-0.3.1\bin\kotlogramme.bat` on Windows); the
  jar and its dependencies live in `lib/`.

## Requirements

- **JDK 25** to run the client (it targets the newest LTS). Nothing else has to be installed: every
  library below, and the native Telegram library for the host, are bundled into the distribution.
  Gradle can fetch the JDK automatically via the toolchain resolver when building.
- **Telegram API credentials**: an `api_id` and `api_hash` from <https://my.telegram.org>, set with
  `kotlogramme config set` or the `TG_API_ID`/`TG_API_HASH` environment variables.
- **A Telegram account to sign in with** (`kotlogramme login`), or a bot token. Reading dialogs,
  history and a chat's files needs an ordinary account.
- **A supported platform**: Windows, Linux or macOS on x86_64 or aarch64.
- Network access to Telegram.

### Dependencies

The client is built from these libraries. The distribution is self-contained - they are all bundled
into `kotlogramme-all.jar`, so none has to be installed - and each keeps its own licence.

- [`com.github.J0s3f:kotlogram`](https://github.com/J0s3f/kotlogram) - the facade this client
  exercises, pinned by tag (see [Picking the facade version](#picking-the-facade-version)); it
  carries grammers and the six native Telegram libraries.
- [Clikt](https://ajalt.github.io/clikt/) 5.1.0 - command-line parsing, options and help.
- [JLine](https://jline.org/) 4.4.6 - the interactive shell, completion and terminal output.
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 1.11.0 (JSON) - the
  `--json` output format and the configuration file.
- [SLF4J](https://www.slf4j.org/) 2.0.20 (simple binding) - logging, quiet unless a level is set.

## Build and run

```bash
./gradlew build          # compile and run the offline test suite
./gradlew installDist    # build the runnable distribution
build/install/kotlogramme/bin/kotlogramme --help
```

On Windows, use `gradlew.bat` and the generated `build\install\kotlogramme\bin\kotlogramme.bat`.

A GraalVM `native-image` build that would drop the JDK requirement is assessed in
[`docs/native-image.md`](docs/native-image.md): **viable with caveats**. No GraalVM was available on
the machine that wrote it, so nothing was built or run; the facade resolves its serializers
reflectively, so every `@Serializable` payload would need registering; and the exe would land near
the 39.4 MB fat jar, the real win being startup and no JVM.

## Picking the facade version

The client depends on the facade's `v0.9.11` tag, resolved from JitPack. To test a different tag:

```bash
./gradlew test -PkotlogrammeVersion=v0.9.6
```

## Documentation

- [`docs/plan.md`](docs/plan.md) — the implementation plan of record.
- [`docs/architecture.md`](docs/architecture.md) — how the code is organised, and why.
- [`docs/decisions.md`](docs/decisions.md) — design decisions and their rationale.
- [`docs/features.md`](docs/features.md) — user-facing features that actually ship.
- [`docs/native-image.md`](docs/native-image.md) — assessment of a GraalVM native build.
- [`AGENTS.md`](AGENTS.md) — engineering rules for humans and agents.

## License

This project is licensed under Apache-2.0; see [`LICENSE`](LICENSE). It builds on the
[`kotlogramme`](https://github.com/J0s3f/kotlogram) facade, which is licensed the same way and which
in turn depends on grammers (dual-licensed Apache-2.0 or MIT), whose notices remain authoritative.
