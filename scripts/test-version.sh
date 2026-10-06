#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/docs"
cat > "$fixture/README.md" <<'README'
Current release: [v0.3.4](https://github.com/J0s3f/kotlogramme-cli/releases/tag/v0.3.4).
Distribution: kotlogramme-0.3.4.zip. Facade: v0.9.11. JLine: 4.4.6.
README
for path in AGENTS.md docs/decisions.md docs/plan.md build.gradle.kts; do
  printf '%s\n' 'Facade v0.9.11' > "$fixture/$path"
done
cd "$fixture"
bash "$script_dir/version.sh" set 0.4.0 v0.9.11
if grep -q '0\.3\.4' "$fixture/README.md"; then
  echo 'Minor version update left the previous release in README.md' >&2
  exit 1
fi
grep -q 'kotlogramme-0.4.0.zip' "$fixture/README.md"
grep -q 'Facade: v0.9.11. JLine: 4.4.6.' "$fixture/README.md"
bash "$script_dir/version.sh" check 0.4.0
bash "$script_dir/version.sh" set 0.4.1 v0.9.12
bash "$script_dir/version.sh" check 0.4.1
grep -q 'Facade: v0.9.12. JLine: 4.4.6.' "$fixture/README.md"
echo 'Version script updates minor and patch releases while preserving dependency versions.'