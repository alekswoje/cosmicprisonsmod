import { describe, expect, it } from "vitest";
import { resolveLauncherAssetName } from "../src/main/githubReleaseService";
import type { LauncherManifest } from "../src/types/contracts";

const manifest: LauncherManifest = {
  schemaVersion: 1,
  mod: {
    version: "1.0.0",
    assetName: "CosmicPrisonsMod-1.0.0.jar",
    sha256: "abc"
  },
  prismPack: {
    minecraft: "1.21.11",
    instanceId: "cosmicprisons-managed-1_21_11",
    assetName: "cosmicprisons-prism-pack-1.21.11.zip",
    sha256: "def"
  },
  launcher: {
    version: "1.0.0",
    assets: {
      "darwin-x64": "cosmic-launcher-macos-universal.zip",
      "darwin-arm64": "cosmic-launcher-macos-universal.zip",
      "win32-x64": "cosmic-launcher-win-x64.zip",
      "win32-arm64": "cosmic-launcher-win-arm64.zip"
    },
    sha256: {
      "darwin-x64": "1",
      "darwin-arm64": "2",
      "win32-x64": "3",
      "win32-arm64": "4"
    }
  }
};

describe("release asset selection", () => {
  it("resolves launcher asset by platform/arch key", () => {
    expect(resolveLauncherAssetName(manifest, "darwin-arm64")).toBe("cosmic-launcher-macos-universal.zip");
    expect(resolveLauncherAssetName(manifest, "win32-x64")).toBe("cosmic-launcher-win-x64.zip");
  });
});
