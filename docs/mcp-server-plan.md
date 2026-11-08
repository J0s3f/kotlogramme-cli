# Plan: kotlogramme as an optional MCP server

Status: proposal, nothing implemented. Written 2026-11-08 against MCP spec revision **2026-07-28**
(the current one) and the previous **2025-11-25**.

## Implementation status (2026-11-08)

Phases 1 to 3 are implemented in `adapter.mcp` and `adapter.cli.McpCommand`: `kotlogramme mcp` speaks both eras
over stdio and offers the eleven read only tools (`--access` is accepted; the write and full tiers are phase 4).
Decisions 0026 to 0028 are in `docs/decisions.md`.

What the implementation settled that the plan left open:
- **Errors without a readable request id omit `id`**, they do not send JSON-RPC's `"id": null`. MCP's schema makes
  `id` optional on an error response and rejects `null`; the schema check in the tests found this, and a strict
  client (the TS SDK validates with its schema) would reject `null` too.
- **`ToolContext.isCancelled`** lets a running tool see that the client cancelled its request; the transport
  owns the cancelled set and only registers cancellations of requests that are actually in flight, so a client
  cannot grow it without bound. `telegram_wait_for_message` will poll it.
- **Bounded input**: `BoundedLineReader` caps a message at 4 MiB and answers an over long line with
  `-32600` instead of buffering it.
- **Parse errors do not echo the client's text.**
- `ping` without stateless metadata is answered `{}` in any state; with stateless metadata it is `-32601`.
- **Pagination key is `next_cursor` and the argument is `cursor`** in every paging tool (the CLI says `next_after`
  and `--after`/`--before`); one name is easier for a model to carry from a result into the next call.
- **Read only tools take `me`, `@username` or a numeric id for `chat`, never a link**: the resolver joins a chat
  when given an invite link (decision 0028).
- **`AppContext` got `shareTelegramClient()`**, opt-in, so the server uses one connection; the one-shot commands
  and the shell still build a client per use case, unchanged.
- **`whoami`/`login` now report the account through `Output.details`**, so `--json` prints one object. The sweep had
  missed it because its fake was never signed in.
- Tested by: protocol unit tests, JSON Schema validation of every response shape, two golden transcripts
  (`src/test/resources/mcp-transcripts/`), transport tests, a command test and a real-pipe run of the
  installed distribution. The native smoke test (`scripts/test-native.ps1`) has an `mcp` check that has not run
  yet (no GraalVM on the development machine); CI runs it.

## 1. Goal and hard constraints

`kotlogramme mcp` starts the client as a Model Context Protocol server, so an LLM application (Claude
Desktop, Claude Code, an IDE) can read and, if allowed, act on the user's Telegram account.

- **Optional and additive.** Nothing changes unless `mcp` is the command. No existing command, option,
  output or exit code changes; the existing test suite stays green untouched.
- **Same rules as the rest of the code base** (AGENTS.md): hexagonal, constructor injection, TDD, fakes
  for every outbound port, no live Telegram in tests, a decision record for every new design choice.
- **Works as the single-file native executable**, which is how it is installed in practice.

## 2. What the spec requires (verified against the 2026-07-28 pages)

Sources: <https://modelcontextprotocol.io/specification/2026-07-28/> (basic, versioning, transports/stdio,
transports/streamable-http, server/tools, server/discover, changelog).

### 2.1 Wire format
- JSON-RPC 2.0. Request `id` is a string or integer, never `null`. Notifications have no `id` and get no
  reply. Results MUST carry `resultType` (`"complete"`; `"input_required"` is MRTR and is not used here).
- **stdio:** newline-delimited, one message per line, **no embedded newlines**, UTF-8. The server MUST NOT
  write anything to stdout that is not a valid MCP message; logging goes to stderr. Server MUST NOT send
  requests. Shutdown: client closes stdin, so the server MUST exit promptly on EOF.

### 2.2 The 2026-07-28 revision is stateless (the big change)
- **No `initialize` / `notifications/initialized`.** Every request carries `params._meta` with
  `io.modelcontextprotocol/protocolVersion` (required), `.../clientCapabilities` (required), `.../clientInfo`
  (recommended). A request missing a required field is rejected with `-32602`.
- Server SHOULD put `io.modelcontextprotocol/serverInfo` into every result's `_meta`.
- Unsupported version: error **`-32022`** with `data: {supported: [...], requested: "..."}`.
- Missing client capability the server needs: `-32021` (not needed for tools-only).
- Reserved MCP range is `-32020..-32099`; `-32020` is HeaderMismatch (HTTP only). Standard JSON-RPC codes
  otherwise (`-32700`, `-32600`, `-32601` unknown method, `-32602` bad params / unknown tool, `-32603`).
- **`server/discover` MUST be implemented**: result `{resultType, supportedVersions, capabilities,
  instructions?, ttlMs, cacheScope, _meta.serverInfo}`.
- **Removed:** `ping`, `logging/setLevel`, roots-changed notification, sessions, `Mcp-Session-Id`. Sampling,
  roots and logging are deprecated: do not implement them.
- `tools/list` result: `tools`, optional `nextCursor`, and **required `ttlMs` + `cacheScope`**; tools SHOULD
  be returned in a **deterministic order**. `tools/call` result: `content[]`, optional `structuredContent`,
  `isError`. Protocol errors (unknown tool `-32602`, malformed request) are JSON-RPC errors; **tool
  execution errors are results with `isError: true`** so the model can self-correct.
- Cancellation on stdio: client sends `notifications/cancelled {requestId}`; server SHOULD stop and MUST NOT
  send anything further for that request.
- Tool names: 1-128 chars, `[A-Za-z0-9_.-]`, unique. `inputSchema` is a JSON Schema object (2020-12
  default); a tool without parameters uses `{"type":"object","additionalProperties":false}`.
- `ToolAnnotations`: `title`, `readOnlyHint` (default false), `destructiveHint` (default **true**),
  `idempotentHint` (default false), `openWorldHint` (default **true**). They are hints; clients treat them as
  untrusted, so they are not a security boundary.

### 2.3 Why we must also speak the old protocol
Most MCP clients in the field still open with `initialize` (2025-11-25 and earlier). The spec explicitly
allows a **dual-era server**: a request with per-request `_meta` is served statelessly; an `initialize`
request selects legacy semantics for the stdio process. Clients probe a server with `server/discover` and
fall back to `initialize` on any non-modern error, so a modern-only server would simply not work with them.
Legacy specifics we need: `initialize` result `{protocolVersion, capabilities, serverInfo, instructions?}`
(echo the client's version if supported, else our latest legacy version), `notifications/initialized`
ignored, `ping` answered with `{}`.

## 3. Decisions

### D1. Own minimal protocol implementation, no SDK (confirmed after a closer look)
SDK state on 2026-11-08, read from the repositories' releases and changelogs:

| SDK | Tier | Latest | 2026-07-28 (stateless) | stdio | Weight / risk for us |
|---|---|---|---|---|---|
| Kotlin `kotlin-sdk` | 3 | `0.16.0` (2026-10-27), pre-1.0, breaking changes in almost every minor | **Types and codecs only**: `DiscoverRequest/Result`, `-32022` data, request-meta accessors, all `@ExperimentalMcpApi`; the release notes say "the client and server do not use them yet". No stateless server. | `StdioServerTransport` (legacy handshake only) | Ktor + coroutines, Kotlin API level 2.2, Streamable HTTP/WebSocket code we would never use; a large native-image surface. |
| Java `java-sdk` | 2 | `2.1.0` stable, `2.2.0-M1` milestone (both 2026-10-16) | Only in the milestone: a **new stateless server** (`io.modelcontextprotocol.modern`) that passes the 37 required conformance scenarios, HTTP-oriented; "modern client, tasks and custom headers not yet". Stable 2.1.0 is legacy-stateful. | Legacy `McpServer` over stdio only | Jackson 3 + Reactor (milestone: Reactor 2026.0.0-M2), reflection, a second JSON stack next to kotlinx.serialization; GraalVM config would be ours to maintain. |
| TypeScript | 1 | `2.4.0` (2026-10-23) | Both eras | yes | Wrong language, but ideal as a *test client* (section 7). |
| Own implementation | - | - | Both eras, exactly what we need | yes | ~600 lines for tools-only; kotlinx.serialization only. |

Result: no JVM SDK can serve the current revision over stdio today (Kotlin: not at all; Java: only the
milestone and only the HTTP side), so an SDK would give us the legacy era at best, plus a heavy dependency
for a native executable. **Own implementation chosen.** The cost is that compliance is ours, which is why
section 7 relies on the official JSON Schema and on independent third-party clients and not on our own
judgement. Re-evaluate when the Kotlin SDK's server consumes its 2026-07-28 types, or the Java modern server
leaves milestone and gets a stdio binding. Needs `docs/decisions.md` entry 0026.

### D2. Tools are curated, not generated from Clikt
Auto-deriving tools from the Clikt commands would give 45 mechanically named tools with weak descriptions
and fragile type inference. A model chooses tools by name and description, so each tool is a small class
with a written schema and description. Each delegates to the **same inbound ports** the CLI uses
(`ReadHistory`, `ListDialogs`, ...) and reuses the existing JSON renderers (`renderMessages`, `renderChats`,
...) into an in-memory `ConsoleOutput(JSON)`, so MCP and `--json` return identical shapes and share their
tests. Domain objects never leak; no Clikt dependency inside the MCP adapter.

### D3. Access levels, read-only by default
One option `--access read-only|read-write|full`, default **read-only**. Each tool declares its level, and
tools above the chosen level are not listed and are refused if called. (AGENTS.md: avoid over-configurability,
so a single option, not per-tool switches.)
- `read-only`: whoami, list chats, read history, search, contacts, members, pinned, folders, wait for message.
- `read-write`: send, reply, edit, forward, mark read, react, pin/unpin, chat action, import contact.
- `full`: delete messages, kick, block/unblock, delete contact, admin rights changes, terminate sessions.
- **Excluded in v1:** `login`, `logout`, `config`, `shell`, `listen` (replaced by a bounded wait tool),
  and everything that touches local files (`send-file`, `send-album`, `download-media`).
- **Planned for a later release (phase 7), designed now so v1 does not block it:** file tools behind one more
  option, `--files-dir <directory>`. Without it the file tools do not exist. With it, `telegram_send_file` /
  `telegram_send_album` may only read files **inside** that directory and `telegram_download_media` may only
  write inside it. Paths are resolved with `toRealPath()` and rejected if they leave the directory (`..`,
  symlinks, junctions, absolute paths elsewhere, UNC paths on Windows), size-capped, never overwrite, and
  `send_file` additionally needs `--access read-write`. Rationale: a model-controlled path argument is a file
  exfiltration primitive, so the directory sandbox is the feature, not an add-on. Needs its own decision record
  and a traversal test matrix (section 7.4) before it ships.

### D4. Dual-era, stdio only
- v1 transport: **stdio** only (what desktop clients launch).
- Protocol versions: `2026-07-28` (modern) plus legacy `2025-11-25` and `2025-06-18`. An `initialize` from an
  older client gets our latest legacy version back (spec: server answers with another version it supports).
- **stdio only.** Streamable HTTP is out of scope (decision of 2026-11-08). The transport sits behind a small
  interface so it can be added later without touching the protocol core.

### D5. One shared Telegram client, calls run sequentially
`AppContext` builds a new `TelegramClient` for every use-case accessor today (`clientFor`). That is
tolerable for a one-shot command and wrong for a server that lives for hours (several clients on one session
file). The `mcp` composition shares one client, **opt-in via an AppContext parameter, so one-shot commands and
the shell behave exactly as before**, and closes it on EOF. The client is not thread-safe (AGENTS.md), so tool
calls execute one at a time on a single worker thread.

### D6. Login is out of band
stdin is the protocol channel, so the server cannot prompt. It requires an existing session
(`kotlogramme login` once) and credentials from config or `TG_API_ID`/`TG_API_HASH`. Without a session every
tool returns an `isError` result saying exactly that command.

## 4. Architecture

New inbound adapter package, parallel to `adapter.cli`; only the composition root and `Main` know both.

```
org.kotlogramme.cli.adapter.mcp
├── protocol   JsonRpc (parse/serialise), ProtocolVersion (value object), McpError (codes), Era
├── server     McpServer (dispatch, era handling, validation), ServerInfo, ToolCatalog
├── tool       McpTool (interface), ToolResult, AccessLevel, one class per tool (ReadHistoryTool, ...)
└── transport  StdioTransport (the only threaded code), later HttpTransport
adapter.cli.McpCommand  -- `kotlogramme mcp [--access ...]`, wires the above
```

- `McpTool`: `name`, `title`, `description`, `inputSchema: JsonObject`, `annotations`, `access: AccessLevel`,
  `call(arguments: JsonObject): ToolResult`. Polymorphism instead of a `when` over names.
- `McpServer.handle(line): String?` is pure (string in, string out, no threads, no IO), so the whole protocol
  is testable without processes. `StdioTransport` adds the reader thread (needed so `notifications/cancelled`
  is seen while a call blocks), the single worker and the cancelled-id set.
- **stdout hygiene:** at start `McpCommand` keeps the real stdout for the transport and `System.setOut` to
  stderr, so a stray `println` (ours, the facade's or a library's) cannot corrupt the stream. The transport
  writes UTF-8 with `\n` explicitly. `WindowsConsoleUtf8` is not applied.
- Errors: `IllegalArgumentException` and `UsageError` -> `isError` result with the message; unknown tool or
  bad arguments -> `-32602`; `TelegramException` (e.g. flood wait) -> `isError` with Telegram's message;
  anything else -> `-32603` without a stack trace on the wire (stack trace to stderr).
- Tool results: `content: [{type:"text", text:<JSON>}]` plus `structuredContent` with the same value (spec:
  return both for compatibility). `outputSchema` is omitted in v1 (optional).
- `limit` arguments are clamped (default 20, max 100); message `text` capped at Telegram's own limit.
  `instructions` in discovery/initialize state that message bodies are untrusted data, not instructions.

## 5. Tool catalogue v1

| Tool | CLI equivalent | Level | Notes |
|---|---|---|---|
| `telegram_whoami` | whoami | read-only | no secrets in the result |
| `telegram_list_chats` | dialogs | read-only | `limit`, `after` |
| `telegram_read_history` | history | read-only | `chat`, `limit`, `before` |
| `telegram_search_messages` | search | read-only | `query`, `chat?`, `limit`, `total?` |
| `telegram_list_contacts` / `telegram_search_contacts` | contacts / search-contacts | read-only | |
| `telegram_list_members` | members | read-only | `chat`, `limit`, `after` |
| `telegram_list_pinned` | pinned | read-only | |
| `telegram_list_folders` | folders | read-only | |
| `telegram_wait_for_message` | listen --once --chat | read-only | `chat?`, `timeout_seconds` (default 30, max 300); returns `{timed_out:true}` instead of blocking forever |
| `telegram_send_message` | send | read-write | `chat`, `text`, `reply_to?`, `silent?` |
| `telegram_edit_message`, `telegram_forward_messages`, `telegram_mark_read`, `telegram_react`, `telegram_pin_message`, `telegram_send_chat_action` | edit, forward, mark-read, react, pin, chat-action | read-write | |
| `telegram_delete_messages`, `telegram_kick_member`, `telegram_block`, `telegram_unblock`, `telegram_delete_contact`, `telegram_set_admin_rights`, `telegram_terminate_session` | delete, kick, block, ... | full | `destructiveHint: true` |

Pagination: list tools take the same `after`/`before` cursors as the CLI and return `next_after` in the
payload (the shape `--json` already defines). `tools/list` itself returns everything on one page, in a fixed
order, `ttlMs: 3600000`, `cacheScope: "public"`.

## 6. Phases (each is a green, committed step; TDD red-green-refactor)

1. **Decision + skeleton.** `docs/decisions.md` 0026 (own implementation) and 0027 (test strategy), vendored
   schemas, `McpCommand` registered in `Main` and the CLI test fixture; prints nothing else.
2. **Protocol core, no tools.** JSON-RPC parsing/serialising, era detection, `server/discover`, `initialize`,
   `ping` (legacy), `tools/list` (empty), `tools/call` (always unknown tool), all error codes, `_meta`
   validation. Stdio transport with EOF shutdown and stdout guard. Schema-validation and golden-transcript
   tests (section 7) are written first and drive this phase.
3. **Shared client + read-only tools.** AppContext opt-in shared client, `ToolCatalog`, `AccessLevel`, the
   read-only tools, `telegram_wait_for_message` on top of the existing `Listen` port and `Chat.isNamedBy`.
4. **Write and full tiers**, their annotations, refusal when above the access level.
5. **Hardening and packaging.** Cancellation, native smoke test, third-party client interop job, conformance
   fixture job, README section with client configuration, CHANGELOG, release `0.11.0`.
6. *(HTTP transport: not planned.)*
7. **File tools** behind `--files-dir` (D3, 7.4), a later release.

Client configuration to document (Claude Desktop style):
```json
{ "mcpServers": { "telegram": {
  "command": "C:\\Tools\\console\\kotlogramme.exe",
  "args": ["mcp", "--access", "read-only"],
  "env": { "TG_API_ID": "...", "TG_API_HASH": "..." } } } }
```
and `claude mcp add telegram -- kotlogramme mcp`.

## 7. Test plan and test suites

### 7.1 What exists (researched 2026-11-08) and what it can do for a stdio server

| Suite | What it is | Usable for us? |
|---|---|---|
| **Official JSON Schema** (`modelcontextprotocol/modelcontextprotocol`, `schema/2026-07-28/schema.json`, `schema/2025-11-25/schema.json`) | The machine-readable source of truth for every message. | **Yes, the backbone.** Validate every message our server emits against it in plain JVM unit tests. Vendor both files under `src/test/resources/mcp-schema/<version>/` pinned to a commit SHA (current head of the 2026-07-28 file: `38353251fa`, 2026-11-06) with a README stating source and SHA. |
| **Official conformance suite** (`modelcontextprotocol/conformance`, npm `@modelcontextprotocol/conformance`) | Black-box scenarios plus a `wire-schema-valid` check on every message. Stable `0.1.16` predates the 2026 revision; the 2026-07-28 scenarios (`server-stateless`, `caching`, ...) ship in the **`0.2.0-alpha.x`** line (`alpha` tag = `0.2.0-alpha.12`). Frozen requirement list: `requirements/2026-07-28.yaml`. | **Only indirectly.** `server` mode takes `--url` (HTTP only; no stdio mode exists) and its scenarios call fixture tools (`test_simple_text`, `test_missing_capability`, ...) that a Telegram server does not have. We therefore run it against a **test-only fixture server**, see 7.3. |
| **MCP Inspector** v2.14.0 (`@modelcontextprotocol/inspector --cli`) | Scriptable client that launches a stdio server. | **Yes, interop smoke** against the real exe. |
| **TypeScript SDK client** `@modelcontextprotocol/client` 2.4.0 (Tier 1, both eras) | A complete, independent client with a stdio transport. | **Yes, the best interop oracle**: a small script that spawns the exe and does list/call in modern and legacy mode. |
| Kotlin SDK `conformance-test` module | Their own harness, pinned to conformance `0.1.15` with a baseline file. | Reference for wiring the runner and a baseline; not reusable (HTTP, Ktor). |

### 7.2 Layers (cheap and deterministic first)
1. **Protocol unit tests (JVM, pure, no IO).** Table-driven per method and era: valid modern request; missing
   `protocolVersion`/`clientCapabilities` -> `-32602`; unsupported version -> `-32022` with `supported` and
   `requested`; unknown method -> `-32601`; malformed JSON -> `-32700` with `id: null`; legacy `initialize`
   version echo and fallback; `ping` answered in legacy and `-32601` in modern; notifications never answered;
   `id` as string and integer, never `null`; `resultType` present in modern results and absent in legacy.
2. **Schema validation (JVM).** Every response and error produced in layers 1 and 4 is validated against the
   matching vendored `schema.json` (modern -> 2026-07-28, legacy -> 2025-11-25). This is the same check as the
   conformance suite's `wire-schema-valid`, run on every build and offline. Needs one **test-only** dependency,
   a JSON Schema 2020-12 validator (for example `com.networknt:json-schema-validator`); decision record 0027
   with the alternatives (hand-written structural asserts, which drift; validating in Node, which would make
   the unit tests depend on Node).
3. **Golden transcripts.** Recorded request/response line files for a modern and a legacy session, compared
   byte-for-byte (the deterministic tool order and fixed `ttlMs` make this stable).
4. **Tool tests** with the existing fakes (`FakeReadHistory`, ...): argument validation, limit clamping, result
   shape equals the `--json` shape, `isError` for domain failures, `structuredContent` equals the parsed text,
   access-level filtering of `tools/list` and refusal on `tools/call`.
5. **Transport tests.** Every stdout line parses as JSON-RPC even when other code prints; emoji, CJK and
   embedded newlines survive; EOF exits promptly; `notifications/cancelled` suppresses the reply and a slow call
   does not block reading it; a 1 MB message works; a partial last line without a newline at EOF is handled.
6. **Regression guarantee for "no impairment".** The unchanged existing suite; a test that every existing
   command still parses and prints the same help; `AppContext` without the opt-in still builds a client per
   accessor exactly as today.
7. **Native smoke** (`scripts/test-native.ps1`): feed `server/discover` and `initialize` on stdin, assert every
   stdout line is valid JSON-RPC, exit 0 on EOF, nothing but protocol on stdout.

### 7.3 Third-party and conformance jobs (CI, Node 20+)
- **Interop job (blocking):** the TS SDK client script runs against the built exe twice, forcing the modern and
  the legacy protocol version, with a fake backend (an env-selected in-memory gateway used only by tests, so no
  Telegram). Asserts list, a read-only call, the `isError` path and the access-level filter.
- **Inspector job (non-blocking at first):** `npx @modelcontextprotocol/inspector@2.14.0 --cli` with `tools/list`
  and one `tools/call`.
- **Conformance job (non-blocking at first, then blocking):** a test-only fixture (in the test source set) wires
  the same `McpServer` protocol core to the fixture tools the suite expects, plus a small stdio-to-HTTP bridge
  on the JDK `HttpServer` that exists only in tests. Run
  `npx @modelcontextprotocol/conformance@<pinned alpha> server --url ... --requirements 2026-07-28 --expected-failures conformance-baseline.yml`
  and once more at the 2025-11-25 wire for the legacy era. Scenarios for features we deliberately lack
  (resources, prompts, completion, MRTR, `subscriptions/listen`, DNS rebinding, SSE) go into the baseline file,
  as the Kotlin SDK does; the baseline fails the build when a listed scenario starts passing or a passing one
  regresses. This checks the protocol core, not the Telegram tools. Pin the version; the alpha line moves.
- Live Telegram stays out of every automated job (AGENTS.md); one manual run with a real account before each
  release is recorded in `docs/live-test-findings.md`.

### 7.4 Later, for file tools (phase 7)
Path-traversal matrix against a temp directory: `..`, absolute paths, symlink and junction escapes, UNC and
drive-relative Windows paths, case tricks, names with trailing dots or spaces, overwrite attempts, size caps.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Spec is new (July 2026); clients differ in which era they speak | Dual-era; golden transcripts for both; discovery probe handled. |
| An LLM with write access can message anyone from the user's account | Default read-only, explicit `--access`, annotations, no file tools, clamps; the client's confirmation UI is the human in the loop. |
| Prompt injection through message text | `instructions` mark bodies as untrusted; results are data in `structuredContent`; read-only default. |
| Stray output on stdout kills the session | stdout redirected to stderr for everything except the transport; test enforces it. |
| Telegram client is not thread-safe | One worker thread; cancellation only suppresses the reply. |
| Hand-written protocol drifts from spec | Official JSON Schema validation on every build, independent TS client interop, pinned conformance run, spec links in code comments. |
| Conformance runner is HTTP-only and the 2026 scenarios are alpha | Fixture + test-only bridge, pinned version, baseline file; schema validation does not depend on it. |
| Flood waits / long calls | Surface Telegram's wait as `isError`; bounded wait tool. |

## 9. Decisions taken (2026-11-08)

1. Own implementation, after re-checking all SDKs (D1).
2. `--access read-only|read-write|full`, default read-only (D3).
3. stdio only; HTTP is out of scope (D4).
4. File tools later (phase 7) but designed now: `--files-dir` sandbox (D3, 7.4).

Still open, small: whether the conformance job should block merges from the start (recommended: non-blocking
until the baseline is stable), and the exact pinned conformance alpha version at implementation time.
