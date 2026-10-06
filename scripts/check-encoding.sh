#!/usr/bin/env bash
# Fails when a tracked Markdown file is not valid UTF-8, which is what a script writing with the
# machine's default code page produces (an em dash becomes a lone 0x97 byte).
set -euo pipefail
cd "$(dirname "$0")/.."

status=0
while IFS= read -r file; do
  if ! iconv -f UTF-8 -t UTF-8 "$file" >/dev/null 2>&1; then
    echo "not valid UTF-8: $file" >&2
    status=1
  fi
done < <(git ls-files '*.md')
exit "$status"
