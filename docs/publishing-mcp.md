# Publishing the MCP server to directories

Nothing here runs automatically. Each step is public and hard to take back, so it is done by hand after a
release, by the maintainer.

## 1. The bundles

An MCP Bundle (`.mcpb`) is a zip with `manifest.json` and the executable; Claude Desktop installs it by
double click. From `0.11.1` on, the release workflow builds one bundle per platform and attaches them to the
release together with a ready `server.json`, so this step is only needed for an older release. By hand, from
the release executables:

```bash
gh release download v<version> --repo J0s3f/kotlogramme-cli --pattern "kotlogramme-<version>-*" --dir dist
for platform in windows-x86_64 linux-x86_64 linux-aarch64 macos-x86_64 macos-aarch64; do
  file=$(ls dist/kotlogramme-<version>-$platform*)
  python3 scripts/build-mcpb.py <version> $platform "$file" dist/mcpb
done
gh release upload v<version> dist/mcpb/*.mcpb --repo J0s3f/kotlogramme-cli
```

The template is `mcpb/manifest.json`; the script fills in version and operating system. The executables are
not code signed, so macOS and Windows may warn on first start.

## 2. Official MCP Registry

```bash
gh release download v<version> --repo J0s3f/kotlogramme-cli --pattern server.json   # or generate it, see below
mcp-publisher login github
mcp-publisher publish
```

`generate-server-json.py` writes one package per bundle with its SHA-256; the registry checks the hash and
that the file is on GitHub Releases. The name `io.github.J0s3f/kotlogramme` is proved by the GitHub login. If
the registry rejects the capital letters of the account name, use the lower case form in the script. The
current format is described at <https://github.com/modelcontextprotocol/registry>. Sites such as PulseMCP read
from this registry.

## 3. Other places

- Set the GitHub topics `mcp-server`, `mcp`, `telegram`, `telegram-client`.
- `awesome-mcp-servers` (GitHub): a pull request adding one line in the communication category.
- Smithery, Glama, mcp.so and Cursor Directory: submit the repository URL on their sites.
