# Findings

Bugs and things worth fixing that turned up while working on the code, noted so they are not lost.
Each entry says where it is, why it matters, and how sure the note is. Remove an entry when it is
fixed, and say in the commit which one it was.

- **The interactive shell is not exercised in CI.** The smoke test runs `shell` with piped input, which
  gives a dumb terminal, so which JLine provider a real console picks is only ever checked by hand.
- **Each table printing creates a Mordant `Terminal` on first use.** That is lazy, so a command that
  prints no table pays nothing, but the cost of one on startup has not been measured.
