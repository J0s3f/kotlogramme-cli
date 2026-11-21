#!/usr/bin/env bash
#
# Fails when the JDK on PATH is a build affected by Java4Shell (CVE-2026-114938), an out-of-bounds
# heap write in HotSpot's AVX-512 Base64 decoder that affects OpenJDK 17.0.6 and later, 21, 25 and 27.
#
#   scripts/check-jdk.sh
#
# The fixed builds (November 2026) are 17.0.21.1, 21.0.13.1, 25.0.5.1 and 27.0.1.1. `setup-java`
# resolves `25` to the newest build, so this only trips when a runner image serves a stale cached
# one. A major version outside that list never received the fix and is refused, because guessing
# that it is safe is worse than asking for a supported line. The versions are compared in pure bash
# because the `sort -V` the obvious solution needs is missing from older BSD userlands.
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "No java on PATH, so the JDK cannot be checked against Java4Shell (CVE-2026-114938)." >&2
  exit 1
fi

banner="$(java -version 2>&1)"
banner="${banner%%$'\n'*}"
raw="${banner#*\"}"
raw="${raw%%\"*}"
# Drop a build, vendor or early-access suffix such as `+5`, `-LTS`, `-ea` or Java 8's `_412`.
version="${raw%%[+_-]*}"
major="${version%%.*}"

# Whether dotted version $1 is older than $2; a missing component counts as zero.
is_older() {
  local IFS=. index have need
  local -a left=($1) right=($2)
  local count=${#left[@]}
  if [ "${#right[@]}" -gt "$count" ]; then count=${#right[@]}; fi
  for ((index = 0; index < count; index++)); do
    have=${left[index]:-0}
    need=${right[index]:-0}
    if ((10#$have < 10#$need)); then return 0; fi
    if ((10#$have > 10#$need)); then return 1; fi
  done
  return 1
}

case "${major}" in
  17) minimum="17.0.21.1" ;;
  21) minimum="21.0.13.1" ;;
  25) minimum="25.0.5.1" ;;
  27) minimum="27.0.1.1" ;;
  *)
    if [ "${major}" -ge 18 ] 2>/dev/null; then
      echo "JDK ${raw} is a line without a Java4Shell fix; use 17, 21, 25 or 27." >&2
      exit 1
    fi
    echo "JDK ${raw} is outside the affected range."
    exit 0
    ;;
esac

if is_older "${version}" "${minimum}"; then
  echo "JDK ${raw} is older than ${minimum} and is affected by Java4Shell (CVE-2026-114938)." >&2
  exit 1
fi
echo "JDK ${raw} includes the Java4Shell fix (>= ${minimum})."
