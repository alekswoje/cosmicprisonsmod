# Cosmic Prisons Server Companion (Fabric Client Mod)

<p align="center">
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/latest/download/cosmic-launcher-win-x64.zip">
    <img alt="Download Windows Launcher" src="https://img.shields.io/badge/Windows%20Launcher-Download-0A66C2?style=for-the-badge&logo=windows&logoColor=white">
  </a>
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/latest/download/cosmic-launcher-macos-universal.zip">
    <img alt="Download macOS Launcher" src="https://img.shields.io/badge/macOS%20Launcher-Download-111111?style=for-the-badge&logo=apple&logoColor=white">
  </a>
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/latest/download/CosmicPrisonsMod-latest.jar">
    <img alt="Download Latest Mod JAR" src="https://img.shields.io/badge/Latest%20Mod%20JAR-Download-2DA44E?style=for-the-badge&logo=java&logoColor=white">
  </a>
</p>

## What This Mod Is
Cosmic Prisons Server Companion is an optional **Fabric client mod** for Cosmic Prisons players.

Its purpose is to render and manage **server-authoritative client features** (UI, overlays, feature toggles, and session-gated behavior) based on data sent by the game server.

This repository is for the client mod only.

## What This Repo Is For (and Not For)
In scope:
- Fabric client mod source code
- Protocol/client runtime logic used by the mod
- Tests for protocol and client behavior
- Contributor PRs that improve or add client features

Out of scope:
- Launcher/distribution tooling
- Private deployment/release assets
- Server plugin implementation code
- Built artifacts

## Current Integration Model
Features should follow the existing model:
- Server advertises support/capabilities
- Client enables feature only when allowed
- Client treats server data as authoritative
- Client no-ops safely when capability/data is missing

## How To Implement A New Feature
Build features in this order to keep PRs correct and reviewable.

1. Define the player behavior
- What the player sees
- Where it appears (inventory, HUD, menus, etc.)
- Exact conditions for display/update/hide
- Failure behavior when server data is unavailable

2. Define the server contract first
- Capability bit(s) required
- Message type(s) required
- Full payload schema (type, bounds, max lengths, optional fields)
- Update model (snapshot vs incremental)
- Frequency/triggers for server sends

3. Define compatibility and safety
- Behavior on old server + new client
- Behavior on new server + old client
- Validation and malformed payload handling
- Performance constraints (rate, size, render cost)

4. Implement client changes
- Protocol decode/encode updates
- Runtime state management
- UI/rendering behavior
- Graceful fallback when server support is missing

5. Add tests
- Protocol tests (valid + malformed)
- Runtime/state behavior tests
- Feature gating/capability tests
- Regression tests for prior behavior

6. Update docs
- Document new message/capability behavior in this README
- Explain any server dependency clearly in the PR

## How To Request Server-Side Changes
If your feature needs game-server work, your PR **must** include a `Server Requirements` section using this exact structure.

### Server Requirements (Required Template)
- Feature name
- Player value
- Capability flag(s) required
- Message type(s) required
- Payload schema (field name, type, bounds, max length)
- Send policy (when/how often server sends)
- Validation/security rules required on server
- Fallback behavior before server support exists
- Rollout plan and compatibility notes

### Example (format only)
- Feature name: Inventory Overlay X
- Player value: Show authoritative slot value text
- Capability flag(s): `1 << N`
- Message type(s): `OVERLAY_X_S2C`
- Payload schema: `slot VarInt (0..35)`, `text UTF-8 <= 24 bytes`
- Send policy: Full snapshot whenever inventory values change
- Validation/security: Server clamps length, enforces allowed slot ranges
- Fallback behavior: Client hides feature when capability/message is absent
- Rollout/compatibility: Old clients ignore unknown message; new clients no-op without capability

## Pull Request Requirements (Strict)
Every PR must include all of the following.

1. Summary
- What changed
- Why it is needed
- Which player workflow is affected

2. Implementation details
- Files/components touched
- Data flow changes
- Any protocol additions or schema changes

3. Server Requirements
- Required if server work is needed
- Must follow the template above

4. Compatibility statement
- Old server + new client behavior
- New server + old client behavior

5. Testing evidence
- Tests added/updated
- What scenarios were validated
- Any intentionally untested edge cases

6. Risk + mitigation
- Potential regressions
- Performance impact
- Security/validation concerns

PRs may be rejected if these sections are incomplete.

## Local Build/Test Commands
```bash
./gradlew spotlessCheck test build
```

## Contributor Quality Bar
- Keep PRs focused and small where possible
- Do not bundle unrelated refactors with feature work
- Do not ship client behavior that hard-depends on server changes without explicit fallback behavior
- If server work is required, say so clearly in PR title and description
