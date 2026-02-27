import { createHash } from "node:crypto";
import { createWriteStream } from "node:fs";
import { copyFile, mkdir, readFile, rename, rm } from "node:fs/promises";
import path from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";

export interface DownloadOptions {
  retries?: number;
  headers?: Record<string, string>;
}

async function sleep(milliseconds: number): Promise<void> {
  await new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

export async function downloadToFile(url: string, destinationFilePath: string, options?: DownloadOptions): Promise<string> {
  const retries = options?.retries ?? 2;
  const headers = {
    "User-Agent": "CosmicPrisonsLauncher/1.0",
    Accept: "application/octet-stream",
    ...(options?.headers ?? {})
  };

  await mkdir(path.dirname(destinationFilePath), { recursive: true });

  let attempt = 0;
  while (attempt <= retries) {
    try {
      const response = await fetch(url, { headers });
      if (!response.ok || !response.body) {
        throw new Error(`Download failed for ${url}: ${response.status} ${response.statusText}`);
      }

      const tempPath = `${destinationFilePath}.part`;
      const writeStream = createWriteStream(tempPath);
      const readable = Readable.fromWeb(response.body as never);
      await pipeline(readable, writeStream);
      await rename(tempPath, destinationFilePath);

      return destinationFilePath;
    } catch (error) {
      attempt += 1;
      if (attempt > retries) {
        throw error;
      }

      await sleep(attempt * 300);
    }
  }

  throw new Error(`Unreachable download loop for ${url}`);
}

export async function computeSha256(filePath: string): Promise<string> {
  const hash = createHash("sha256");
  const bytes = await readFile(filePath);
  hash.update(bytes);
  return hash.digest("hex");
}

export async function verifySha256(filePath: string, expectedSha256: string): Promise<boolean> {
  const actualSha256 = await computeSha256(filePath);
  return actualSha256.toLowerCase() === expectedSha256.toLowerCase();
}

export async function downloadAndVerify(
  url: string,
  destinationFilePath: string,
  expectedSha256?: string,
  options?: DownloadOptions
): Promise<string> {
  await downloadToFile(url, destinationFilePath, options);

  if (expectedSha256) {
    const matches = await verifySha256(destinationFilePath, expectedSha256);
    if (!matches) {
      await rm(destinationFilePath, { force: true });
      throw new Error(`Checksum mismatch for ${destinationFilePath}`);
    }
  }

  return destinationFilePath;
}

export async function copyToFile(sourceFilePath: string, destinationFilePath: string): Promise<void> {
  await mkdir(path.dirname(destinationFilePath), { recursive: true });
  await copyFile(sourceFilePath, destinationFilePath);
}
