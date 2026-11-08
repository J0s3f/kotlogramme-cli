// Drives `kotlogramme mcp` with the official TypeScript SDK client, an independent implementation of the
// protocol, in each negotiation mode and at each access level. It talks to no Telegram account: the server
// runs with an empty configuration, so a tool call that needs one must come back as a readable tool error.
//
// usage: node interop.mjs <command> [leading arguments...]     (the script appends: mcp --access <level>)
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';

const [command, ...leading] = process.argv.slice(2);
if (!command) throw new Error('usage: node interop.mjs <command> [leading arguments...]');

const modes = ['legacy', 'auto', { pin: '2026-07-28' }];
const levels = [
  { name: 'read-only', tools: 15, offers: 'telegram_list_chats', withholds: 'telegram_send_message' },
  { name: 'read-write', tools: 26, offers: 'telegram_send_message', withholds: 'telegram_delete_messages' },
  { name: 'full', tools: 36, offers: 'telegram_delete_messages', withholds: null },
];
let failures = 0;

for (const level of levels) {
  for (const mode of modes) {
    const label = `${level.name} / ${typeof mode === 'string' ? mode : `pin ${mode.pin}`}`;
    const transport = new StdioClientTransport({ command, args: [...leading, 'mcp', '--access', level.name] });
    const client = new Client(
      { name: 'interop-test', version: '1.0.0' },
      { versionNegotiation: { mode } },
    );
    try {
      await client.connect(transport);
      const server = client.getServerVersion();
      const tools = (await client.listTools()).tools;
      const byName = new Map(tools.map((tool) => [tool.name, tool]));

      const countMatches = tools.length === level.tools;
      const offered = byName.has(level.offers);
      const withheld = level.withholds === null || !byName.has(level.withholds);
      const namesSorted = tools.map((t) => t.name).join() === [...tools.map((t) => t.name)].sort().join();
      const hintsComplete = tools.every((t) => ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint']
        .every((hint) => typeof t.annotations?.[hint] === 'boolean'));
      const readOnlyHonest = level.name !== 'read-only' || tools.every((t) => t.annotations.readOnlyHint === true);
      const destructiveMarked = level.name !== 'full'
        || (byName.get('telegram_delete_messages')?.annotations?.destructiveHint === true
          && byName.get('telegram_whoami')?.annotations?.destructiveHint === false);

      let unknownRejected = false;
      try {
        await client.callTool({ name: 'no_such_tool', arguments: {} });
      } catch {
        unknownRejected = true;
      }

      let withheldRefused = level.withholds === null;
      if (!withheldRefused) {
        try {
          await client.callTool({ name: level.withholds, arguments: {} });
        } catch {
          withheldRefused = true;
        }
      }

      let invalidArgumentIsToolError = false;
      try {
        const result = await client.callTool({ name: 'telegram_read_history', arguments: { chat: 'https://t.me/+AbCdEf' } });
        invalidArgumentIsToolError = result.isError === true;
      } catch {
        invalidArgumentIsToolError = false;
      }

      let notSignedIn = false;
      try {
        const result = await client.callTool({ name: 'telegram_list_chats', arguments: {} });
        notSignedIn = result.isError === true;
      } catch {
        notSignedIn = false;
      }

      const checks = {
        server: server?.name === 'kotlogramme', countMatches, offered, withheld, namesSorted, hintsComplete,
        readOnlyHonest, destructiveMarked, unknownRejected, withheldRefused, invalidArgumentIsToolError, notSignedIn,
      };
      const failed = Object.entries(checks).filter(([, ok]) => !ok).map(([name]) => name);
      console.log(`${failed.length === 0 ? 'PASS' : 'FAIL'} [${label}] ${server?.name} ${server?.version} `
        + `protocol=${client.protocolVersion ?? 'n/a'} tools=${tools.length}${failed.length ? ` failed=${failed.join(',')}` : ''}`);
      if (failed.length) failures++;
    } catch (error) {
      failures++;
      console.log(`FAIL [${label}] ${error?.stack ?? error}`);
    } finally {
      await client.close().catch(() => {});
    }
  }
}
process.exit(failures === 0 ? 0 : 1);
