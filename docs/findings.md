# Findings

Bugs and things worth fixing that turned up while working on the code, noted so they are not lost.
Each entry says where it is, why it matters, and how sure the note is. Remove an entry when it is
fixed, and say in the commit which one it was.

- **Wrappe unpacks into a new folder for every release and never cleans up.** The packed executable
  extracts to `%TEMP%\kotlogramme-windows-x86_64\<hash>` (`scripts/build-native-single.ps1:70`). The
  per-start leak is gone, but each new version leaves about 45 MB behind. Look at Wrappe's options for
  removing older unpack directories, or document the cache location.
- **FFM on Linux and macOS images is unverified.** JLine's shipped FFM metadata covers the libc calls
  but was not complete for Windows, so it may not be for the other two. `doctor` reports
  `FFM unavailable` as a warning there, and JLine falls back to its JNI libraries, so nothing breaks, but
  the CI logs for the Linux and macOS packed executables have not been read for that row.
- **The interactive shell is not exercised in CI.** The smoke test runs `shell` with piped input, which
  gives a dumb terminal, so which JLine provider a real console picks is only ever checked by hand.
- **Each table printing creates a Mordant `Terminal` on first use.** That is lazy, so a command that
  prints no table pays nothing, but the cost of one on startup has not been measured.
