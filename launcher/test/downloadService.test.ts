import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { computeSha256, verifySha256 } from "../src/main/downloadService";

describe("downloadService checksums", () => {
  it("computes and verifies SHA-256", async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), "checksum-"));
    const filePath = path.join(baseDir, "sample.txt");
    await writeFile(filePath, "cosmic-prisons");

    const sha = await computeSha256(filePath);
    expect(await verifySha256(filePath, sha)).toBe(true);
    expect(await verifySha256(filePath, "deadbeef")).toBe(false);
  });
});
