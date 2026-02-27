#!/usr/bin/env node
import { createHash } from "node:crypto";
import { readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";

function getArg(name, fallback) {
  const prefix = `--${name}=`;
  const found = process.argv.find((value) => value.startsWith(prefix));
  if (!found) {
    return fallback;
  }

  return found.slice(prefix.length);
}

async function sha256File(filePath) {
  const hash = createHash("sha256");
  hash.update(await readFile(filePath));
  return hash.digest("hex");
}

function required(value, label) {
  if (!value || value.trim().length === 0) {
    throw new Error(`Missing required argument: ${label}`);
  }

  return value;
}

const releaseDir = required(getArg("release-dir"), "--release-dir=/abs/path");
const modVersion = required(getArg("mod-version"), "--mod-version=1.0.0");
const launcherVersion = required(getArg("launcher-version"), "--launcher-version=1.0.0");
const minecraftVersion = getArg("minecraft-version", "1.21.11");
const instanceId = getArg("instance-id", "cosmicprisons-managed-1_21_11");

const manifestFile = path.join(releaseDir, "cosmic-launcher-manifest.json");
const prismPackAssetName = `cosmicprisons-prism-pack-${minecraftVersion}.zip`;
const modAssetName = `CosmicPrisonsMod-${modVersion}.jar`;
const macLauncherAsset = getArg("mac-asset", "cosmic-launcher-macos-universal.zip");
const winX64Asset = getArg("win-x64-asset", "cosmic-launcher-win-x64.zip");
const winArm64Asset = getArg("win-arm64-asset", "cosmic-launcher-win-arm64.zip");

const assets = new Set(await readdir(releaseDir));

for (const requiredAsset of [modAssetName, prismPackAssetName, macLauncherAsset, winX64Asset, winArm64Asset]) {
  if (!assets.has(requiredAsset)) {
    throw new Error(`Missing expected release asset: ${requiredAsset}`);
  }
}

const manifest = {
  schemaVersion: 1,
  mod: {
    version: modVersion,
    assetName: modAssetName,
    sha256: await sha256File(path.join(releaseDir, modAssetName))
  },
  prismPack: {
    minecraft: minecraftVersion,
    instanceId,
    assetName: prismPackAssetName,
    sha256: await sha256File(path.join(releaseDir, prismPackAssetName))
  },
  launcher: {
    version: launcherVersion,
    assets: {
      "darwin-x64": macLauncherAsset,
      "darwin-arm64": macLauncherAsset,
      "win32-x64": winX64Asset,
      "win32-arm64": winArm64Asset
    },
    sha256: {
      "darwin-x64": await sha256File(path.join(releaseDir, macLauncherAsset)),
      "darwin-arm64": await sha256File(path.join(releaseDir, macLauncherAsset)),
      "win32-x64": await sha256File(path.join(releaseDir, winX64Asset)),
      "win32-arm64": await sha256File(path.join(releaseDir, winArm64Asset))
    }
  }
};

await writeFile(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

// eslint-disable-next-line no-console
console.log(`Manifest written: ${manifestFile}`);
