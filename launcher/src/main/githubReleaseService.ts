import { MANIFEST_ASSET_NAME } from "./constants";
import type { GithubAsset, GithubRelease, LauncherManifest, PlatformArchKey } from "../types/contracts";

function parseRepo(repo: string): { owner: string; name: string } {
  const [owner, name] = repo.split("/");
  if (!owner || !name) {
    throw new Error(`Invalid GitHub repository '${repo}'. Expected owner/name.`);
  }

  return { owner, name };
}

function githubApi(path: string): string {
  return `https://api.github.com${path}`;
}

function getDefaultHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "User-Agent": "CosmicPrisonsLauncher/1.0",
    Accept: "application/vnd.github+json"
  };

  const token = process.env.GITHUB_TOKEN;
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: getDefaultHeaders() });
  if (!response.ok) {
    throw new Error(`GitHub API request failed: ${response.status} ${response.statusText} (${url})`);
  }

  return (await response.json()) as T;
}

function assertManifest(data: unknown): asserts data is LauncherManifest {
  if (!data || typeof data !== "object") {
    throw new Error("Manifest is not an object");
  }

  const manifest = data as Partial<LauncherManifest>;
  if (manifest.schemaVersion !== 1) {
    throw new Error("Unsupported manifest schemaVersion");
  }

  if (!manifest.mod?.assetName || !manifest.mod.sha256 || !manifest.mod.version) {
    throw new Error("Manifest.mod is incomplete");
  }

  if (!manifest.prismPack?.assetName || !manifest.prismPack.sha256 || !manifest.prismPack.instanceId) {
    throw new Error("Manifest.prismPack is incomplete");
  }

  if (!manifest.launcher?.version || !manifest.launcher.assets || !manifest.launcher.sha256) {
    throw new Error("Manifest.launcher is incomplete");
  }
}

export function findAssetByName(release: GithubRelease, assetName: string): GithubAsset {
  const asset = release.assets.find((entry) => entry.name === assetName);
  if (!asset) {
    throw new Error(`Required asset '${assetName}' was not found on release ${release.tag_name}`);
  }

  return asset;
}

export function resolveLauncherAssetName(manifest: LauncherManifest, key: PlatformArchKey): string {
  const assetName = manifest.launcher.assets[key];
  if (!assetName) {
    throw new Error(`Manifest launcher asset mapping missing for ${key}`);
  }

  return assetName;
}

export class GithubReleaseService {
  private readonly owner: string;
  private readonly name: string;

  public constructor(repo: string) {
    const parsed = parseRepo(repo);
    this.owner = parsed.owner;
    this.name = parsed.name;
  }

  public async fetchLatestRelease(): Promise<GithubRelease> {
    return fetchJson<GithubRelease>(
      githubApi(`/repos/${this.owner}/${this.name}/releases/latest`)
    );
  }

  public async fetchReleaseByTag(tag: string): Promise<GithubRelease> {
    return fetchJson<GithubRelease>(
      githubApi(`/repos/${this.owner}/${this.name}/releases/tags/${encodeURIComponent(tag)}`)
    );
  }

  public async fetchManifestForRelease(release: GithubRelease): Promise<LauncherManifest> {
    const manifestAsset = findAssetByName(release, MANIFEST_ASSET_NAME);
    const response = await fetch(manifestAsset.browser_download_url, {
      headers: {
        "User-Agent": "CosmicPrisonsLauncher/1.0",
        Accept: "application/octet-stream"
      }
    });

    if (!response.ok) {
      throw new Error(
        `Failed to fetch manifest asset ${MANIFEST_ASSET_NAME}: ${response.status} ${response.statusText}`
      );
    }

    const body = await response.json();
    assertManifest(body);
    return body;
  }
}
