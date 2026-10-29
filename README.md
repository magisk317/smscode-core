# magisk-forward-core

Shared forwarding infrastructure for the Magisk317 Android app family.

## Modules

- `contract` — product-neutral forwarding payloads, message kinds, call stages and Android transport keys.
- `capture` — notification capture and call-source classification for Xposed hosts.
- `runtime` — app-side forwarding intake and dispatch lifecycle.
- `engine` — route, filter and dispatch planning.
- `sender` — sender contracts and transport adapters.
- `db` — forwarding events, routes, sender configuration and delivery attempts.

The repository depends on `magisk-xposed-kit` for Xposed and runtime primitives. It does not depend on a product application or on `magisk-smscode-core`.
