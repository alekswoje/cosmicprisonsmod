import path from "node:path";
import { shell } from "electron";
import {
  COSMIC_RELEASE_REPO,
  COSMIC_SERVER_ADDRESS,
  UPDATE_CHECK_INTERVAL_MS
} from "./constants";
import { downloadAndVerify } from "./downloadService";
import {
  findAssetByName,
  GithubReleaseService,
  resolveLauncherAssetName
} from "./githubReleaseService";
import { Logger } from "./logger";
import { installModJar } from "./modInstallService";
import {
  discoverLunarFabricModDirectories,
  getLunarProfilesRoot,
  getNativeMinecraftModsPath,
  getPlatformArchKey
} from "./platformPaths";
import { ensureManagedInstance, getManagedInstanceModsDirectory, launchManagedInstance } from "./prismInstanceService";
import { PrismRuntimeService } from "./prismRuntimeService";
import { StateStore } from "./stateStore";
import { isVersionNewer, toIsoNow } from "./versioning";
import type {
  ApplyUpdatesRequest,
  ApplyUpdatesResult,
  CheckUpdatesResponse,
  GithubRelease,
  InstallLunarResponse,
  InstallResult,
  InstallTarget,
  LauncherDirectories,
  LauncherManifest,
  LauncherUpdateStage,
  PlayDirectResult,
  StatusResponse,
  UpdateSummary
} from "../types/contracts";

interface ReleaseBundle {
  release: GithubRelease;
  manifest: LauncherManifest;
}

interface ManifestSnapshot {
  manifest: LauncherManifest;
  releaseTag: string;
  stale: boolean;
  warnings: string[];
}

const INSTALL_TARGETS: InstallTarget[] = ["prismManaged", "nativeMinecraft", "lunarClient"];

export class UpdateService {
  private readonly stateStore: StateStore;
  private readonly releaseService: GithubReleaseService;
  private readonly prismRuntimeService: PrismRuntimeService;
  private readonly appVersion: string;
  private readonly directories: LauncherDirectories;
  private readonly logger: Logger;

  public constructor(input: {
    stateStore: StateStore;
    prismRuntimeService: PrismRuntimeService;
    appVersion: string;
    directories: LauncherDirectories;
    logger: Logger;
  }) {
    this.stateStore = input.stateStore;
    this.releaseService = new GithubReleaseService(COSMIC_RELEASE_REPO);
    this.prismRuntimeService = input.prismRuntimeService;
    this.appVersion = input.appVersion;
    this.directories = input.directories;
    this.logger = input.logger;
  }

  public async getStatus(forceUpdateCheck = false): Promise<StatusResponse> {
    const state = await this.stateStore.read();
    const lunarCandidates = await discoverLunarFabricModDirectories(getLunarProfilesRoot());
    const nativeModsPath = getNativeMinecraftModsPath();
    const platformArchKey = getPlatformArchKey();
    const [platform, arch] = platformArchKey.split("-");

    const warnings: string[] = [];
    let updateSummary: UpdateSummary | undefined;

    try {
      const updateResult = await this.checkUpdates(forceUpdateCheck);
      updateSummary = updateResult.summary;
      warnings.push(...updateResult.warnings);
    } catch (error) {
      warnings.push(`Update check unavailable: ${(error as Error).message}`);
    }

    return {
      platform: platform as StatusResponse["platform"],
      arch: arch as StatusResponse["arch"],
      nativeModsPath,
      lunarCandidates,
      installedTargets: state.installedTargets,
      updateSummary,
      warnings,
      logsPath: this.logger.getLogFilePath()
    };
  }

  public async checkUpdates(force = false): Promise<CheckUpdatesResponse> {
    const snapshot = await this.getManifestSnapshot(force);
    const state = await this.stateStore.read();

    const summary = this.buildUpdateSummary(snapshot, state.installedTargets);
    return {
      summary,
      warnings: snapshot.warnings
    };
  }

  public async playDirect(): Promise<PlayDirectResult> {
    const bundle = await this.fetchReleaseBundleForInstall();
    const state = await this.stateStore.read();

    const prismRuntime = await this.prismRuntimeService.ensurePortableRuntime(
      this.directories,
      state.prismRuntime
    );

    await this.stateStore.updatePrismRuntime(prismRuntime);

    const prismPackPath = await this.downloadReleaseAsset(
      bundle,
      bundle.manifest.prismPack.assetName,
      bundle.manifest.prismPack.sha256
    );

    await ensureManagedInstance(
      prismRuntime.executablePath,
      this.directories.prismDataDir,
      bundle.manifest.prismPack.instanceId,
      prismPackPath
    );

    const modJarPath = await this.downloadReleaseAsset(
      bundle,
      bundle.manifest.mod.assetName,
      bundle.manifest.mod.sha256
    );

    const modsDirectory = getManagedInstanceModsDirectory(
      this.directories.prismDataDir,
      bundle.manifest.prismPack.instanceId
    );

    const install = await installModJar({
      target: "prismManaged",
      targetDir: modsDirectory,
      sourceJarPath: modJarPath,
      destinationJarName: bundle.manifest.mod.assetName,
      version: bundle.manifest.mod.version
    });

    await this.persistInstalledTarget(install);

    await launchManagedInstance(
      prismRuntime.executablePath,
      this.directories.prismDataDir,
      bundle.manifest.prismPack.instanceId,
      COSMIC_SERVER_ADDRESS
    );

    await this.logger.info("Play Directly launched", {
      instanceId: bundle.manifest.prismPack.instanceId,
      executablePath: prismRuntime.executablePath
    });

    return {
      launched: true,
      prismExecutablePath: prismRuntime.executablePath,
      instanceId: bundle.manifest.prismPack.instanceId,
      modInstall: install
    };
  }

  public async installNative(): Promise<InstallResult> {
    const bundle = await this.fetchReleaseBundleForInstall();
    const modJarPath = await this.downloadReleaseAsset(
      bundle,
      bundle.manifest.mod.assetName,
      bundle.manifest.mod.sha256
    );

    const nativeModsPath = getNativeMinecraftModsPath();

    const install = await installModJar({
      target: "nativeMinecraft",
      targetDir: nativeModsPath,
      sourceJarPath: modJarPath,
      destinationJarName: bundle.manifest.mod.assetName,
      version: bundle.manifest.mod.version
    });

    await this.persistInstalledTarget(install);
    await this.logger.info("Installed mod into native Minecraft", { path: install.installPath });

    return install;
  }

  public async installLunar(targetPath?: string): Promise<InstallLunarResponse> {
    let resolvedTargetPath = targetPath;

    if (!resolvedTargetPath) {
      const candidates = await discoverLunarFabricModDirectories(getLunarProfilesRoot());

      if (candidates.length !== 1) {
        return {
          status: "requires_selection",
          candidates
        };
      }

      [resolvedTargetPath] = candidates;
    }

    const bundle = await this.fetchReleaseBundleForInstall();
    const modJarPath = await this.downloadReleaseAsset(
      bundle,
      bundle.manifest.mod.assetName,
      bundle.manifest.mod.sha256
    );

    const install = await installModJar({
      target: "lunarClient",
      targetDir: resolvedTargetPath,
      sourceJarPath: modJarPath,
      destinationJarName: bundle.manifest.mod.assetName,
      version: bundle.manifest.mod.version
    });

    await this.persistInstalledTarget(install);
    await this.logger.info("Installed mod into Lunar Client", { path: install.installPath });

    return {
      status: "installed",
      install
    };
  }

  public async applyUpdates(request: ApplyUpdatesRequest): Promise<ApplyUpdatesResult> {
    const bundle = await this.fetchReleaseBundleForInstall();
    const warnings: string[] = [];
    const modResults: InstallResult[] = [];

    const targetSet = new Set(request.modTargets);
    if (targetSet.size > 0) {
      const modJarPath = await this.downloadReleaseAsset(
        bundle,
        bundle.manifest.mod.assetName,
        bundle.manifest.mod.sha256
      );

      for (const target of INSTALL_TARGETS) {
        if (!targetSet.has(target)) {
          continue;
        }

        const installResult = await this.installToTarget(target, modJarPath, bundle, request.lunarPath);
        modResults.push(installResult);
      }
    }

    let launcherStage: LauncherUpdateStage | undefined;

    if (request.includeLauncher) {
      launcherStage = await this.stageLauncherUpdate(bundle);
      await shell.showItemInFolder(launcherStage.filePath);
      warnings.push("Launcher update was downloaded. Replace the current app bundle/executable after closing the launcher.");
    }

    return {
      modResults,
      launcherStage,
      warnings
    };
  }

  private async installToTarget(
    target: InstallTarget,
    modJarPath: string,
    bundle: ReleaseBundle,
    lunarPath?: string
  ): Promise<InstallResult> {
    const manifest = bundle.manifest;

    if (target === "nativeMinecraft") {
      const result = await installModJar({
        target,
        targetDir: getNativeMinecraftModsPath(),
        sourceJarPath: modJarPath,
        destinationJarName: manifest.mod.assetName,
        version: manifest.mod.version
      });

      await this.persistInstalledTarget(result);
      return result;
    }

    if (target === "lunarClient") {
      let targetDir = lunarPath;
      if (!targetDir) {
        const stateTarget = await this.stateStore.getInstalledTarget("lunarClient");
        if (stateTarget?.path) {
          targetDir = path.dirname(stateTarget.path);
        }
      }

      if (!targetDir) {
        const candidates = await discoverLunarFabricModDirectories(getLunarProfilesRoot());
        if (candidates.length === 1) {
          [targetDir] = candidates;
        }
      }

      if (!targetDir) {
        throw new Error("Lunar update needs an explicit mods folder path. Use Install Lunar first or provide lunarPath.");
      }

      const result = await installModJar({
        target,
        targetDir,
        sourceJarPath: modJarPath,
        destinationJarName: manifest.mod.assetName,
        version: manifest.mod.version
      });

      await this.persistInstalledTarget(result);
      return result;
    }

    const state = await this.stateStore.read();
    const prismRuntime = await this.prismRuntimeService.ensurePortableRuntime(
      this.directories,
      state.prismRuntime
    );

    await this.stateStore.updatePrismRuntime(prismRuntime);

    const prismPackPath = await this.downloadReleaseAsset(
      bundle,
      manifest.prismPack.assetName,
      manifest.prismPack.sha256
    );

    await ensureManagedInstance(
      prismRuntime.executablePath,
      this.directories.prismDataDir,
      manifest.prismPack.instanceId,
      prismPackPath
    );

    const managedModsPath = getManagedInstanceModsDirectory(
      this.directories.prismDataDir,
      manifest.prismPack.instanceId
    );

    const result = await installModJar({
      target,
      targetDir: managedModsPath,
      sourceJarPath: modJarPath,
      destinationJarName: manifest.mod.assetName,
      version: manifest.mod.version
    });

    await this.persistInstalledTarget(result);
    return result;
  }

  private async persistInstalledTarget(installResult: InstallResult): Promise<void> {
    await this.stateStore.upsertInstalledTarget({
      target: installResult.target,
      path: installResult.installPath,
      modVersion: installResult.version,
      updatedAt: toIsoNow()
    });
  }

  private async stageLauncherUpdate(bundle: ReleaseBundle): Promise<LauncherUpdateStage> {
    const key = getPlatformArchKey();
    const assetName = resolveLauncherAssetName(bundle.manifest, key);
    const sha256 = bundle.manifest.launcher.sha256[key];
    const downloaded = await this.downloadReleaseAsset(bundle, assetName, sha256, this.directories.updatesDir);

    return {
      assetName,
      filePath: downloaded,
      version: bundle.manifest.launcher.version
    };
  }

  private async downloadReleaseAsset(
    bundle: ReleaseBundle,
    assetName: string,
    expectedSha256: string,
    targetDirectory: string = this.directories.downloadsDir
  ): Promise<string> {
    const asset = findAssetByName(bundle.release, assetName);
    const destinationFilePath = path.join(targetDirectory, asset.name);
    return downloadAndVerify(asset.browser_download_url, destinationFilePath, expectedSha256);
  }

  private async fetchReleaseBundleForInstall(): Promise<ReleaseBundle> {
    try {
      const release = await this.releaseService.fetchLatestRelease();
      const manifest = await this.releaseService.fetchManifestForRelease(release);

      await this.persistReleaseSnapshot(release, manifest);
      return { release, manifest };
    } catch (error) {
      await this.logger.warn("Failed to fetch latest release for install", {
        error: (error as Error).message
      });

      const state = await this.stateStore.read();
      if (!state.cachedRelease?.tagName) {
        throw error;
      }

      const release = await this.releaseService.fetchReleaseByTag(state.cachedRelease.tagName);
      const manifest = await this.releaseService.fetchManifestForRelease(release);
      return { release, manifest };
    }
  }

  private async getManifestSnapshot(force: boolean): Promise<ManifestSnapshot> {
    const now = Date.now();
    const state = await this.stateStore.read();
    const warnings: string[] = [];

    const isFreshEnough =
      !force &&
      Boolean(state.lastCheckedAt) &&
      now - new Date(state.lastCheckedAt as string).getTime() < UPDATE_CHECK_INTERVAL_MS;

    if (isFreshEnough && state.cachedManifest && state.cachedRelease?.tagName) {
      return {
        manifest: state.cachedManifest,
        releaseTag: state.cachedRelease.tagName,
        stale: false,
        warnings
      };
    }

    try {
      const release = await this.releaseService.fetchLatestRelease();
      const manifest = await this.releaseService.fetchManifestForRelease(release);

      await this.persistReleaseSnapshot(release, manifest);

      return {
        manifest,
        releaseTag: release.tag_name,
        stale: false,
        warnings
      };
    } catch (error) {
      await this.logger.warn("Update check failed", { error: (error as Error).message });

      if (state.cachedManifest && state.cachedRelease?.tagName) {
        warnings.push("Using cached update metadata because GitHub could not be reached.");

        return {
          manifest: state.cachedManifest,
          releaseTag: state.cachedRelease.tagName,
          stale: true,
          warnings
        };
      }

      throw error;
    }
  }

  private buildUpdateSummary(
    snapshot: ManifestSnapshot,
    installedTargets: StatusResponse["installedTargets"]
  ): UpdateSummary {
    const modUpdateByTarget: UpdateSummary["modUpdateByTarget"] = {
      prismManaged: false,
      nativeMinecraft: false,
      lunarClient: false
    };

    for (const target of INSTALL_TARGETS) {
      const installed = installedTargets.find((entry) => entry.target === target);
      if (!installed) {
        continue;
      }

      modUpdateByTarget[target] = isVersionNewer(installed.modVersion, snapshot.manifest.mod.version);
    }

    return {
      checkedAt: toIsoNow(),
      releaseTag: snapshot.releaseTag,
      stale: snapshot.stale,
      launcherCurrentVersion: this.appVersion,
      launcherLatestVersion: snapshot.manifest.launcher.version,
      launcherUpdateAvailable: isVersionNewer(this.appVersion, snapshot.manifest.launcher.version),
      modLatestVersion: snapshot.manifest.mod.version,
      modUpdateByTarget
    };
  }

  private async persistReleaseSnapshot(release: GithubRelease, manifest: LauncherManifest): Promise<void> {
    const state = await this.stateStore.read();
    await this.stateStore.write({
      ...state,
      lastCheckedReleaseTag: release.tag_name,
      lastCheckedAt: toIsoNow(),
      cachedManifest: manifest,
      cachedRelease: {
        tagName: release.tag_name,
        publishedAt: release.published_at
      }
    });
  }
}
