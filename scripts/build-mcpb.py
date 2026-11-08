#!/usr/bin/env python3
"""Packs one release executable into an MCP Bundle (.mcpb) for Claude Desktop and other MCPB clients.

usage: build-mcpb.py <version> <platform> <executable> <output-directory>

<platform> is the release suffix: windows-x86_64, linux-x86_64, linux-aarch64, macos-x86_64 or macos-aarch64.
The bundle holds mcpb/manifest.json with the version and operating system filled in, and the executable as
server/kotlogramme (server/kotlogramme.exe on Windows) with the executable bit kept.
"""
import json
import pathlib
import sys
import zipfile

OPERATING_SYSTEMS = {"windows": "win32", "linux": "linux", "macos": "darwin"}
ROOT = pathlib.Path(__file__).resolve().parent.parent
EXECUTABLE_MODE = 0o100755 << 16
FILE_MODE = 0o100644 << 16


def main() -> None:
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    version, platform, executable, output = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3]), pathlib.Path(sys.argv[4])
    family = platform.split("-")[0]
    if family not in OPERATING_SYSTEMS:
        sys.exit(f"unknown platform {platform!r}; expected one of windows, linux, macos followed by -x86_64 or -aarch64")
    if not executable.is_file():
        sys.exit(f"no such executable: {executable}")

    manifest = (ROOT / "mcpb" / "manifest.json").read_text(encoding="utf-8")
    manifest = manifest.replace("__VERSION__", version).replace("__OS__", OPERATING_SYSTEMS[family])
    json.loads(manifest)  # fails loudly when the template is no longer valid JSON

    inside = "server/kotlogramme.exe" if family == "windows" else "server/kotlogramme"
    output.mkdir(parents=True, exist_ok=True)
    bundle = output / f"kotlogramme-{version}-{platform}.mcpb"
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        add(archive, "manifest.json", manifest.encode("utf-8"), FILE_MODE)
        add(archive, inside, executable.read_bytes(), EXECUTABLE_MODE)
    print(bundle)


def add(archive: zipfile.ZipFile, name: str, data: bytes, mode: int) -> None:
    entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))  # fixed time: the same input gives the same bytes
    entry.external_attr = mode
    entry.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(entry, data)


if __name__ == "__main__":
    main()
