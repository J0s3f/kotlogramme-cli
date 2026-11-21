#!/usr/bin/env bash
# Tests scripts/check-jdk.sh against a stand-in `java` that reports a chosen version, so the guard
# is verified without installing every JDK it has to judge.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

cat > "$fixture/java" <<'JAVA'
#!/usr/bin/env bash
echo "openjdk version \"${FAKE_JAVA_VERSION}\" 2026-11-17" >&2
echo "OpenJDK Runtime Environment (build ${FAKE_JAVA_VERSION})" >&2
JAVA
chmod +x "$fixture/java"

# Runs the guard with a stand-in `java` first on PATH and returns its exit status.
guard_status() {
  PATH="$fixture:$PATH" FAKE_JAVA_VERSION="$1" bash "$script_dir/check-jdk.sh" >/dev/null 2>&1 && echo 0 || echo 1
}

failures=0

expect_accepted() {
  if [ "$(guard_status "$1")" != 0 ]; then
    echo "JDK $1 should be accepted but was rejected" >&2
    failures=$((failures + 1))
  fi
}

expect_rejected() {
  if [ "$(guard_status "$1")" != 1 ]; then
    echo "JDK $1 should be rejected but was accepted" >&2
    failures=$((failures + 1))
  fi
}

# The first build of each line that carries the Java4Shell fix, and anything newer.
for fixed in 17.0.21.1 17.0.21.2 17.0.22 21.0.13.1 21.0.14 25.0.5.1 25.0.5.1+5 25.0.6 27.0.1.1 27.0.2; do
  expect_accepted "$fixed"
done

# Builds from before the fix, including the ones a stale runner cache serves.
for vulnerable in 17.0.6 17.0.20.1 17.0.21 21.0.12.1 21.0.13 25.0.4.1 25.0.5 25.0.5.0.3 25 27.0.1 27-ea; do
  expect_rejected "$vulnerable"
done

# A line outside 17, 21, 25 and 27 never received the fix, so it is refused rather than guessed at.
for unfixed in 18.0.2 19 20.0.2 22.0.1 23 24.0.2 26.0.1; do
  expect_rejected "$unfixed"
done

# Releases that predate the affected range are not touched.
for unaffected in 1.8.0_412 8.0.462 11.0.25; do
  expect_accepted "$unaffected"
done

# Without a java on PATH there is nothing to vouch for.
if PATH="/nonexistent" /bin/bash "$script_dir/check-jdk.sh" >/dev/null 2>&1; then
  echo "A missing java should be rejected" >&2
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures JDK guard case(s) failed" >&2
  exit 1
fi
echo 'JDK guard accepts fixed builds and rejects affected ones.'
