# Cosmic Prisons Server Companion (Fabric Client Mod)

Optional Fabric **client-only** mod for Minecraft 1.21.11 that integrates with a Paper/Spigot plugin over custom payload channel `servercompanion:main`.

Phase 1 scope is intentionally minimal:
- handshake + signature-gated enable flow
- feature settings entry in ESC pause menu
- server-driven item value overlays in hotbar/inventory

## Build

```bash
./gradlew spotlessCheck test build
```

Output artifact:
- `build/libs/*-remapped.jar`

## Run In Dev

```bash
./gradlew runClient
```

## Client Config

Config path:
- `config/cosmicprisonsmod-client.json`

Example:

```json
{
  "allowedServerIds": ["cosmicprisons.com"],
  "enablePayloadCodecFallback": false,
  "serverSignaturePolicy": "LOG_ONLY",
  "serverSignaturePublicKeys": [],
  "logMalformedOncePerConnection": true,
  "featureToggles": {
    "inventory_item_overlays": true
  }
}
```

## Handshake + Gating

The mod stays disabled until a valid `ServerHelloS2C` is received:
1. protocol version matches `1`
2. `serverId` is allowed
3. signature policy check passes

Client capability bitset sent in `ClientHelloC2S` is `63` (bits `0..5`).

## Protocol Surface

Supported message types:
- `1`: `CLIENT_HELLO_C2S`
- `2`: `SERVER_HELLO_S2C`
- `10`: `INVENTORY_ITEM_OVERLAYS_S2C`

`INVENTORY_ITEM_OVERLAYS_S2C` payload:
- `overlayCount` (VarInt)
- repeated `overlayCount` times:
  - `slot` (VarInt)
  - `overlayType` (VarInt)
  - `displayText` (UTF-8 string, max 24 bytes)

Overlay types:
- `1` => Cosmic Energy style
- `2` => Money Note style

## Feature Settings

In ESC pause menu, click `Mod Settings`.

Current feature list:
- `Item Value Overlays` (`inventory_item_overlays`)

Feature toggles persist to `featureToggles` in config.

## Inventory Item Overlay Behavior

- Server feature bit `1<<5` is required.
- Full snapshot semantics: each packet replaces the entire client overlay map.
- Rendering applies only to player storage slots:
  - `0-8` hotbar
  - `9-35` main inventory
- `displayText` is rendered exactly as sent by the server (no client-side number reformatting).
- Overlay cache is cleared on disconnect, world leave, server switch, and empty snapshots.

## Optional Payload Fallback

An optional codec-level fallback mixin remains available for Fabric environments where plugin payload decode routing is inconsistent:
- enabled by `enablePayloadCodecFallback=true`
- scoped strictly to `servercompanion:main`

## Tests

`src/test` covers:
- protocol encode/decode roundtrip for all supported message types
- malformed packet handling (oversized string/text/count, unknown type, truncated/trailing bytes)
- golden vectors for `ServerHelloS2C` and `InventoryItemOverlaysS2C`
- connection gate behavior

## Standalone Launcher

This repository now includes an Electron launcher in `launcher/` for macOS + Windows.

Launcher capabilities:
- `Play Directly` via a managed portable Prism runtime + managed instance launch to `cosmicprisons.com`
- `Install into a Client` for native Minecraft and Lunar Client Fabric mod directories
- GitHub-release based update checks and per-target update actions

Launcher docs:
- [`launcher/README.md`](launcher/README.md)

Release helper scripts:
- [`scripts/launcher/generate-manifest.mjs`](scripts/launcher/generate-manifest.mjs)
# CosmicPrisonsMod
