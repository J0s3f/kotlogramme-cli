#!/usr/bin/env bash
#
# Keeps every version the client advertises in step with the release.
#
#   scripts/version.sh set   0.3.5 v0.9.12   # rewrite the docs and the build to these
#   scripts/version.sh check 0.3.5           # fail when anything disagrees
#
# Two versions matter, and both had drifted before: the client's own, and the facade tag it builds
# against. `check` reads the facade tag from `build.gradle.kts` and treats it as the source, so the
# docs cannot quietly disagree with the build.
#
# `CHANGELOG.md` and `docs/live-test-findings.md` are deliberately excluded: their version numbers
# are a record of what shipped when, and rewriting them would falsify history. A release is prepared
# by running `set`, and CI runs `check` on the tag so a stale README cannot ship.
set -euo pipefail

mode="${1:?usage: scripts/version.sh <set|check> <cli-version> [facade-tag]}"

case "${mode}" in
  set)
    cli="${2:?usage: scripts/version.sh set <cli-version> <facade-tag>}"
    facade="${3:?usage: scripts/version.sh set <cli-version> <facade-tag>}"
    ;;
  check)
    cli="${2:?usage: scripts/version.sh check <cli-version>}"
    # The build's pin is the source of truth for the facade tag; the docs must agree with it.
    facade="$(grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' build.gradle.kts | head -n1)"
    if [ -z "${facade}" ]; then
      echo "build.gradle.kts names no facade tag" >&2
      exit 1
    fi
    ;;
  *)
    echo "unknown mode '${mode}'; use set or check" >&2
    exit 2
    ;;
esac

cli="${cli#v}"
facade="v${facade#v}"

# The client's own version is the README's; the facade tag is named wherever the dependency is.
cli_files=(README.md)
facade_files=(README.md AGENTS.md docs/decisions.md docs/plan.md build.gradle.kts)

cli_family="$(printf '%s' "${cli}" | sed -E 's/\.[0-9]+$//')"
cli_pattern="$(printf '%s' "${cli_family}" | sed -E 's/\./\\./g')\.[0-9]+"
# Family-specific, so the facade pattern cannot match the client's own v0.3.x tag.
facade_family="$(printf '%s' "${facade}" | sed -E 's/\.[0-9]+$//')"
facade_pattern="$(printf '%s' "${facade_family}" | sed -E 's/\./\\./g')\.[0-9]+"

replace() {
  local pattern="$1" value="$2" file="$3"
  sed -i -E "s/${pattern}/${value}/g" "${file}"
}

stale_in() {
  local pattern="$1" expected="$2" file="$3"
  grep -oE "${pattern}" "${file}" | grep -vx "${expected}" | sort -u | tr '\n' ' ' || true
}

case "${mode}" in
  set)
    for file in "${cli_files[@]}"; do replace "${cli_pattern}" "${cli}" "${file}"; done
    for file in "${facade_files[@]}"; do replace "${facade_pattern}" "${facade}" "${file}"; done
    echo "docs and build now advertise client ${cli} on facade ${facade}"
    ;;

  check)
    status=0
    if ! grep -q "${cli}" README.md; then
      echo "README.md does not name the client release ${cli}" >&2
      status=1
    fi
    for file in "${cli_files[@]}"; do
      stale="$(stale_in "${cli_pattern}" "${cli}" "${file}")"
      [ -z "${stale}" ] || { echo "${file}: names ${stale}- but the client is ${cli}" >&2; status=1; }
    done
    for file in "${facade_files[@]}"; do
      stale="$(stale_in "${facade_pattern}" "${facade}" "${file}")"
      [ -z "${stale}" ] || { echo "${file}: names ${stale}- but the build pins ${facade}" >&2; status=1; }
    done
    if [ "${status}" -eq 0 ]; then
      echo "docs agree with client ${cli} on facade ${facade}"
    fi
    exit "${status}"
    ;;
esac
