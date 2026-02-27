import { homedir } from "node:os";
import path from "node:path";
import { readdir } from "node:fs/promises";
import { LUNAR_FABRIC_DIR_PATTERN } from "./constants";
import { pathExists } from "./fsUtils";
import type {
  LauncherDirectories,
  PlatformArchKey,
  SupportedArch,
  SupportedPlatform
} from "../types/contracts";

function normalizePlatform(platform: NodeJS.Platform = process.platform): SupportedPlatform {
  if (platform !== "darwin" && platform !== "win32") {
    throw new Error(`Unsupported operating system '${platform}'. This launcher only supports macOS and Windows.`);
  }

  return platform;
}

export function normalizeArch(arch: string = process.arch): SupportedArch {
  if (arch === "x64" || arch === "arm64") {
    return arch;
  }

  throw new Error(`Unsupported architecture '${arch}'. Supported architectures are x64 and arm64.`);
}

export function getPlatformArchKey(
  platform: NodeJS.Platform = process.platform,
  arch: string = process.arch
): PlatformArchKey {
  return `${normalizePlatform(platform)}-${normalizeArch(arch)}`;
}

export function getLauncherDirectories(userDataDir: string): LauncherDirectories {
  return {
    userDataDir,
    downloadsDir: path.join(userDataDir, "downloads"),
    updatesDir: path.join(userDataDir, "updates"),
    prismRuntimeDir: path.join(userDataDir, "prism-runtime"),
    prismDataDir: path.join(userDataDir, "prism-data"),
    logsDir: path.join(userDataDir, "logs"),
    stateFilePath: path.join(userDataDir, "state.json")
  };
}

export function getNativeMinecraftModsPath(platform: NodeJS.Platform = process.platform): string {
  const supportedPlatform = normalizePlatform(platform);

  if (supportedPlatform === "darwin") {
    return path.join(homedir(), "Library", "Application Support", "minecraft", "mods");
  }

  const appData = process.env.APPDATA;
  if (appData && appData.trim().length > 0) {
    return path.join(appData, ".minecraft", "mods");
  }

  return path.join(homedir(), "AppData", "Roaming", ".minecraft", "mods");
}

export function getLunarProfilesRoot(platform: NodeJS.Platform = process.platform): string {
  const supportedPlatform = normalizePlatform(platform);

  if (supportedPlatform === "darwin") {
    return path.join(homedir(), ".lunarclient", "profiles", "lunar");
  }

  return path.join(homedir(), ".lunarclient", "profiles", "lunar");
}

export async function discoverLunarFabricModDirectories(rootDir: string): Promise<string[]> {
  const result = new Set<string>();

  if (!(await pathExists(rootDir))) {
    return [];
  }

  const rootEntries = await readdir(rootDir, { withFileTypes: true });

  for (const entry of rootEntries) {
    if (!entry.isDirectory()) {
      continue;
    }

    const candidateModsDir = path.join(rootDir, entry.name, "mods");
    if (await pathExists(candidateModsDir)) {
      const modEntries = await readdir(candidateModsDir, { withFileTypes: true });
      for (const modEntry of modEntries) {
        if (modEntry.isDirectory() && LUNAR_FABRIC_DIR_PATTERN.test(modEntry.name)) {
          result.add(path.join(candidateModsDir, modEntry.name));
        }
      }
    }
  }

  const directModsDir = path.join(rootDir, "mods");
  if (await pathExists(directModsDir)) {
    const directEntries = await readdir(directModsDir, { withFileTypes: true });
    for (const directEntry of directEntries) {
      if (directEntry.isDirectory() && LUNAR_FABRIC_DIR_PATTERN.test(directEntry.name)) {
        result.add(path.join(directModsDir, directEntry.name));
      }
    }
  }

  return Array.from(result).sort((left, right) => left.localeCompare(right));
}

export function getSupportedPlatform(platform: NodeJS.Platform = process.platform): SupportedPlatform {
  return normalizePlatform(platform);
}
