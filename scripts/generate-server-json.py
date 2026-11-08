#!/usr/bin/env python3
"""Writes server.json for the official MCP Registry from the .mcpb bundles of one release.

usage: generate-server-json.py <version> <directory with the .mcpb files> [output file]

Every bundle named kotlogramme-<version>-<platform>.mcpb becomes one package with its SHA-256, pointing at
the GitHub release asset. Run it after the release has built the bundles; the registry verifies the hash.
"""
import hashlib
import json
import pathlib
import sys

SCHEMA = "https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json"
REPOSITORY = "https://github.com/J0s3f/kotlogramme-cli"
NAME = "io.github.J0s3f/kotlogramme"


def main() -> None:
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    version, directory = sys.argv[1], pathlib.Path(sys.argv[2])
    target = pathlib.Path(sys.argv[3]) if len(sys.argv) == 4 else pathlib.Path("server.json")
    bundles = sorted(directory.glob(f"kotlogramme-{version}-*.mcpb"))
    if not bundles:
        sys.exit(f"no kotlogramme-{version}-*.mcpb in {directory}")

    server = {
        "$schema": SCHEMA,
        "name": NAME,
        "title": "Telegram (kotlogramme)",
        "description": "Read and, if allowed, act on your own Telegram account: chats, messages, contacts, files.",
        "repository": {"url": REPOSITORY + ".git", "source": "github"},
        "websiteUrl": REPOSITORY,
        "version": version,
        "packages": [
            {
                "registryType": "mcpb",
                "identifier": f"{REPOSITORY}/releases/download/v{version}/{bundle.name}",
                "fileSha256": hashlib.sha256(bundle.read_bytes()).hexdigest(),
                "transport": {"type": "stdio"},
            }
            for bundle in bundles
        ],
    }
    target.write_text(json.dumps(server, indent=2) + "\n", encoding="utf-8")
    print(f"{target}: {len(bundles)} package(s)")


if __name__ == "__main__":
    main()
