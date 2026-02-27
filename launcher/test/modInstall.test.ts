import { mkdtemp, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { installModJar } from "../src/main/modInstallService";

describe("installModJar", () => {
  it("replaces old CosmicPrisonsMod jars and preserves unrelated files", async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), "mod-install-"));
    const sourceJar = path.join(baseDir, "source.jar");
    await writeFile(sourceJar, "new-mod");

    await writeFile(path.join(baseDir, "CosmicPrisonsMod-0.9.0.jar"), "old-mod");
    await writeFile(path.join(baseDir, "some-other-mod.jar"), "other");

    const result = await installModJar({
      target: "nativeMinecraft",
      targetDir: baseDir,
      sourceJarPath: sourceJar,
      destinationJarName: "CosmicPrisonsMod-1.0.0.jar",
      version: "1.0.0"
    });

    const files = await readdir(baseDir);
    expect(files.includes("CosmicPrisonsMod-0.9.0.jar")).toBe(false);
    expect(files.includes("CosmicPrisonsMod-1.0.0.jar")).toBe(true);
    expect(files.includes("some-other-mod.jar")).toBe(true);
    expect(result.removedFiles.length).toBe(1);
  });
});
