import { mkdtemp, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  discoverLunarFabricModDirectories,
  getNativeMinecraftModsPath,
  getPlatformArchKey,
  normalizeArch
} from "../src/main/platformPaths";

const originalAppData = process.env.APPDATA;

afterEach(() => {
  process.env.APPDATA = originalAppData;
});

describe("platformPaths", () => {
  it("resolves darwin native mods path", () => {
    const resolved = getNativeMinecraftModsPath("darwin");
    expect(resolved).toContain(path.join("Library", "Application Support", "minecraft", "mods"));
  });

  it("resolves win32 native mods path using APPDATA", () => {
    process.env.APPDATA = "C:\\Users\\Landon\\AppData\\Roaming";
    const resolved = getNativeMinecraftModsPath("win32");

    expect(resolved).toContain(".minecraft");
    expect(resolved.startsWith(process.env.APPDATA)).toBe(true);
  });

  it("discovers lunar fabric directories", async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), "lunar-test-"));

    await mkdir(path.join(baseDir, "1.21", "mods", "fabric-1.21.11"), { recursive: true });
    await mkdir(path.join(baseDir, "1.20", "mods", "forge-47"), { recursive: true });

    const discovered = await discoverLunarFabricModDirectories(baseDir);
    expect(discovered.length).toBe(1);
    expect(discovered[0]).toContain(path.join("mods", "fabric-1.21.11"));
  });

  it("builds platform key for supported values", () => {
    expect(getPlatformArchKey("darwin", "arm64")).toBe("darwin-arm64");
    expect(getPlatformArchKey("win32", "x64")).toBe("win32-x64");
  });

  it("normalizes supported arch", () => {
    expect(normalizeArch("x64")).toBe("x64");
  });
});
