export type InstallTarget = "prismManaged" | "nativeMinecraft" | "lunarClient";

export type SupportedPlatform = "darwin" | "win32";
export type SupportedArch = "x64" | "arm64";
export type PlatformArchKey = `${SupportedPlatform}-${SupportedArch}`;

export interface GithubAsset {
  id: number;
  name: string;
  browser_download_url: string;
  size: number;
  content_type?: string;
}

export interface GithubRelease {
  tag_name: string;
  name: string;
  html_url: string;
  published_at: string;
  assets: GithubAsset[];
}

export interface LauncherManifest {
  schemaVersion: 1;
  mod: {
    version: string;
    assetName: string;
    sha256: string;
  };
  prismPack: {
    minecraft: string;
    instanceId: string;
    assetName: string;
    sha256: string;
  };
  launcher: {
    version: string;
    assets: Record<PlatformArchKey, string>;
    sha256: Record<PlatformArchKey, string>;
  };
}

export interface InstalledTargetState {
  target: InstallTarget;
  path: string;
  modVersion: string;
  updatedAt: string;
}

export interface PrismRuntimeState {
  version: string;
  installDir: string;
  executablePath: string;
  updatedAt: string;
}

export interface CachedReleaseState {
  tagName: string;
  publishedAt: string;
}

export interface LauncherState {
  schemaVersion: 1;
  installedTargets: InstalledTargetState[];
  prismRuntime?: PrismRuntimeState;
  lastCheckedReleaseTag?: string;
  lastCheckedAt?: string;
  cachedManifest?: LauncherManifest;
  cachedRelease?: CachedReleaseState;
  uiPrefs: Record<string, string | number | boolean>;
}

export interface LauncherDirectories {
  userDataDir: string;
  downloadsDir: string;
  updatesDir: string;
  prismRuntimeDir: string;
  prismDataDir: string;
  logsDir: string;
  stateFilePath: string;
}

export interface InstallResult {
  target: InstallTarget;
  installPath: string;
  version: string;
  removedFiles: string[];
}

export interface PlayDirectResult {
  launched: boolean;
  prismExecutablePath: string;
  instanceId: string;
  modInstall: InstallResult;
}

export interface LauncherUpdateStage {
  assetName: string;
  filePath: string;
  version: string;
}

export interface UpdateSummary {
  checkedAt: string;
  releaseTag: string;
  stale: boolean;
  launcherCurrentVersion: string;
  launcherLatestVersion: string;
  launcherUpdateAvailable: boolean;
  modLatestVersion: string;
  modUpdateByTarget: Record<InstallTarget, boolean>;
}

export interface StatusResponse {
  platform: SupportedPlatform;
  arch: SupportedArch;
  nativeModsPath: string;
  lunarCandidates: string[];
  installedTargets: InstalledTargetState[];
  updateSummary?: UpdateSummary;
  warnings: string[];
  logsPath: string;
}

export interface CheckUpdatesResponse {
  summary: UpdateSummary;
  warnings: string[];
}

export interface InstallLunarResponse {
  status: "installed" | "requires_selection";
  install?: InstallResult;
  candidates?: string[];
}

export interface ApplyUpdatesRequest {
  modTargets: InstallTarget[];
  includeLauncher: boolean;
  lunarPath?: string;
}

export interface ApplyUpdatesResult {
  modResults: InstallResult[];
  launcherStage?: LauncherUpdateStage;
  warnings: string[];
}

export interface LauncherApi {
  getStatus: () => Promise<StatusResponse>;
  playDirect: () => Promise<PlayDirectResult>;
  installNative: () => Promise<InstallResult>;
  installLunar: (targetPath?: string) => Promise<InstallLunarResponse>;
  selectFolder: () => Promise<string | null>;
  checkUpdates: (force?: boolean) => Promise<CheckUpdatesResponse>;
  applyUpdates: (request: ApplyUpdatesRequest) => Promise<ApplyUpdatesResult>;
  openLogs: () => Promise<void>;
}
