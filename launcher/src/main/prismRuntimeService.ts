import extract from "extract-zip";
import { chmod, readdir, stat } from "node:fs/promises";
import path from "node:path";
import { downloadToFile } from "./downloadService";
import { PRISM_RELEASE_REPO } from "./constants";
import { ensureDir, pathExists } from "./fsUtils";
import { GithubReleaseService } from "./githubReleaseService";
import { normalizeArch, getSupportedPlatform } from "./platformPaths";
import { toIsoNow } from "./versioning";
import type { LauncherDirectories, PrismRuntimeState, SupportedArch, SupportedPlatform } from "../types/contracts";

function normalizePrismVersion(tag: string): string {
  return tag.startsWith("v") ? tag.slice(1) : tag;
}

function scoreWindowsPortableAsset(name: string): number {
  if (!name.includes("Portable") || !name.endsWith(".zip")) {
    return -1;
  }

  if (name.includes("MSVC")) {
    return 20;
  }

  if (name.includes("MinGW")) {
    return 10;
  }

  return 1;
}

export function pickPortablePrismAssetName(
  assetNames: string[],
  platform: SupportedPlatform,
  arch: SupportedArch
): string {
  if (platform === "darwin") {
    const macAsset = assetNames.find((name) => name.startsWith("PrismLauncher-macOS-") && name.endsWith(".zip"));
    if (!macAsset) {
      throw new Error("No compatible macOS Prism zip asset found.");
    }

    return macAsset;
  }

  const filtered = assetNames
    .filter((name) => name.includes("Windows") && name.toLowerCase().includes(arch) && name.includes("Portable"))
    .map((name) => ({ name, score: scoreWindowsPortableAsset(name) }))
    .filter((entry) => entry.score >= 0)
    .sort((left, right) => right.score - left.score);

  if (filtered.length === 0) {
    throw new Error(`No compatible Windows portable Prism asset found for ${arch}.`);
  }

  return filtered[0].name;
}

async function findFileRecursively(basePath: string, predicate: (filePath: string) => boolean): Promise<string | undefined> {
  const entries = await readdir(basePath, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(basePath, entry.name);
    if (entry.isDirectory()) {
      const nested = await findFileRecursively(fullPath, predicate);
      if (nested) {
        return nested;
      }

      continue;
    }

    if (predicate(fullPath)) {
      return fullPath;
    }
  }

  return undefined;
}

async function findPrismExecutable(extractedDir: string, platform: SupportedPlatform): Promise<string> {
  if (platform === "win32") {
    const executablePath = await findFileRecursively(extractedDir, (candidate) =>
      candidate.toLowerCase().endsWith("prismlauncher.exe")
    );

    if (!executablePath) {
      throw new Error("Could not locate prismlauncher.exe in extracted Prism runtime.");
    }

    return executablePath;
  }

  const executablePath = await findFileRecursively(extractedDir, (candidate) => {
    const normalized = candidate.replace(/\\\\/g, "/").toLowerCase();
    return normalized.includes(".app/contents/macos/") && normalized.endsWith("prismlauncher");
  });

  if (!executablePath) {
    throw new Error("Could not locate Prism executable in macOS app bundle.");
  }

  const stats = await stat(executablePath);
  if ((stats.mode & 0o111) === 0) {
    await chmod(executablePath, 0o755);
  }

  return executablePath;
}

export class PrismRuntimeService {
  private readonly releaseService: GithubReleaseService;
  private readonly platform: SupportedPlatform;
  private readonly arch: SupportedArch;

  public constructor() {
    this.releaseService = new GithubReleaseService(PRISM_RELEASE_REPO);
    this.platform = getSupportedPlatform();
    this.arch = normalizeArch();
  }

  public async ensurePortableRuntime(
    directories: LauncherDirectories,
    existingRuntime?: PrismRuntimeState
  ): Promise<PrismRuntimeState> {
    if (existingRuntime && (await pathExists(existingRuntime.executablePath))) {
      return existingRuntime;
    }

    await ensureDir(directories.prismRuntimeDir);

    const release = await this.releaseService.fetchLatestRelease();
    const prismVersion = normalizePrismVersion(release.tag_name);
    const installDir = path.join(directories.prismRuntimeDir, prismVersion);

    const assetName = pickPortablePrismAssetName(
      release.assets.map((asset) => asset.name),
      this.platform,
      this.arch
    );

    const asset = release.assets.find((candidate) => candidate.name === assetName);
    if (!asset) {
      throw new Error(`Prism release asset '${assetName}' not found on ${release.tag_name}`);
    }

    const archivePath = path.join(directories.downloadsDir, asset.name);

    await ensureDir(directories.downloadsDir);
    await downloadToFile(asset.browser_download_url, archivePath);

    if (!(await pathExists(installDir))) {
      await ensureDir(installDir);
      await extract(archivePath, { dir: installDir });
    }

    const executablePath = await findPrismExecutable(installDir, this.platform);

    return {
      version: prismVersion,
      installDir,
      executablePath,
      updatedAt: toIsoNow()
    };
  }
}
