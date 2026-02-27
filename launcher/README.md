# Cosmic Prisons Launcher (Electron)

Standalone launcher for `CosmicPrisonsMod` on macOS and Windows.

## Features
- `Play Directly`
  - Ensures a portable Prism runtime exists.
  - Imports/maintains the managed Prism instance.
  - Installs latest mod release into the managed instance.
  - Launches and joins `cosmicprisons.com`.
- `Install into a Client`
  - Installs latest mod release into native Minecraft mods folder.
  - Installs latest mod release into Lunar Client Fabric mods folder.
- Release-based updates
  - Uses GitHub Releases from this repo.
  - Checks for updates every 6 hours and on app launch.
  - Supports per-target mod updates and launcher package staging.

## Setup

```bash
cd launcher
npm install
npm run build
npm run dev
```

## Build distributables

```bash
cd launcher
npm run dist:mac
npm run dist:win
```

## Environment variables
- `CPM_GITHUB_REPO` (optional): override release source repo (`owner/name`). Default: `LandonDev/CosmicPrisonsMod`.
- `CPM_PRISM_GITHUB_REPO` (optional): override Prism repo (`owner/name`).
- `GITHUB_TOKEN` (optional): avoids unauthenticated API rate limits.

## Required release assets
- `CosmicPrisonsMod-<semver>.jar`
- `cosmicprisons-prism-pack-1.21.11.zip`
- `cosmic-launcher-manifest.json`
- `cosmic-launcher-macos-universal.zip`
- `cosmic-launcher-win-x64.zip`
- `cosmic-launcher-win-arm64.zip`
