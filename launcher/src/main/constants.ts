export const COSMIC_SERVER_ADDRESS = "cosmicprisons.com";
export const MANIFEST_ASSET_NAME = "cosmic-launcher-manifest.json";
export const LAUNCHER_STATE_SCHEMA_VERSION = 1 as const;
export const UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;
export const MOD_FILE_PREFIX = "CosmicPrisonsMod-";
export const MOD_FILE_PATTERN = /^CosmicPrisonsMod-.*\.jar$/i;
export const LUNAR_FABRIC_DIR_PATTERN = /^fabric-/i;

const DEFAULT_COSMIC_REPO = "LandonDev/CosmicPrisonsMod";
const DEFAULT_PRISM_REPO = "PrismLauncher/PrismLauncher";

const REPO_PATTERN = /^[^/]+\/[^/]+$/;

function normalizeRepo(repo: string | undefined, fallback: string): string {
  if (!repo) {
    return fallback;
  }

  const trimmed = repo.trim();
  if (!REPO_PATTERN.test(trimmed)) {
    throw new Error(`Invalid repository value '${repo}'. Expected format owner/name.`);
  }

  return trimmed;
}

export const COSMIC_RELEASE_REPO = normalizeRepo(process.env.CPM_GITHUB_REPO, DEFAULT_COSMIC_REPO);
export const PRISM_RELEASE_REPO = normalizeRepo(process.env.CPM_PRISM_GITHUB_REPO, DEFAULT_PRISM_REPO);
