# Architecture

The application is a hexagon. The domain and the application sit in the middle and know nothing
about Telegram, the terminal or the filesystem; everything concrete is an adapter attached to a
port.

```
                 adapter.cli            adapter.format
                     │                       ▲
                     ▼                       │
   application.port.api ──► application.service ──► application.port.spi
                                                          ▲
                                       adapter.telegram ──┤
                                       adapter.config ────┘
```

## Layers

| Package | Holds | May depend on |
| --- | --- | --- |
| `org.kotlogramme.cli.domain` | Entities and value objects: `Chat`, `Message`, `Account`, `ChatId`, `MessageId`, `ChatKind`. | nothing |
| `org.kotlogramme.cli.application.port.api` | Inbound ports: one interface per use case or cohesive use-case group (`ListDialogs`, `ReadHistory`, `SendText`, `Authenticate`). | `domain` |
| `org.kotlogramme.cli.application.port.spi` | Outbound ports: `TelegramGateway`, `SessionStore`, `ConfigStore`, `Clock`, `Output`. | `domain` |
| `org.kotlogramme.cli.application.service` | Implementations of the inbound ports, expressed in terms of the outbound ports. | `domain`, both port packages |
| `org.kotlogramme.cli.adapter.telegram` | The only code that touches `kotlogramme`: maps gateway calls to facade calls and facade models to domain objects. | `domain`, `port.spi` |
| `org.kotlogramme.cli.adapter.config` | File-backed `ConfigStore` and `SessionStore`. | `domain`, `port.spi` |
| `org.kotlogramme.cli.adapter.cli` | Clikt commands and the JLine shell; wires the composition root and translates between arguments and inbound ports. | everything |
| `org.kotlogramme.cli.adapter.format` | Renderers turning domain objects into tables, plain text or JSON. | `domain`, `Output` |

## The dependency rule

Source dependencies point inwards only. A use case never imports `kotlogramme`, never writes to
`System.out`, and never reads a file directly; it calls a port. If you are tempted to import the
facade from `application`, you need a new port method instead.

## The main seam: `TelegramGateway`

Everything that talks to Telegram goes through a single outbound port. That is what makes the whole
application testable offline: the test double implements the port with canned data, and the use
cases, CLI commands and renderers run against it with no network and no credentials.

The real adapter is deliberately thin. It translates, it does not decide. Business decisions
(paging, formatting, which chat a shortcut means) live in the services.

## Commands as use cases

A Clikt command is an adapter: it parses arguments, calls one inbound port, and hands the result to
a renderer. The interactive shell dispatches to the same inbound ports, so a new capability is
implemented once and is immediately available in both modes.

## Configuration and state

All paths and tunables are resolved once at the composition root and passed down. The session file
and the config file live behind `SessionStore` and `ConfigStore` so their format can change without
touching a use case.

## Testing

- Fast, offline, deterministic unit tests with fake ports. This is the default and the CI gate.
- The real adapter is covered by a small number of tests that assert it maps facade models to the
  expected domain objects; it is not exercised against the network.
- Any test that would contact Telegram is opt-in and excluded from `test`.
