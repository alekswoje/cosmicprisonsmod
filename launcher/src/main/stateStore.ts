import { readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import { ensureDir, pathExists } from "./fsUtils";
import { LAUNCHER_STATE_SCHEMA_VERSION } from "./constants";
import type {
  InstallTarget,
  InstalledTargetState,
  LauncherState,
  PrismRuntimeState
} from "../types/contracts";

function defaultState(): LauncherState {
  return {
    schemaVersion: LAUNCHER_STATE_SCHEMA_VERSION,
    installedTargets: [],
    uiPrefs: {}
  };
}

export class StateStore {
  private readonly stateFilePath: string;

  public constructor(stateFilePath: string) {
    this.stateFilePath = stateFilePath;
  }

  public async read(): Promise<LauncherState> {
    if (!(await pathExists(this.stateFilePath))) {
      return defaultState();
    }

    const text = await readFile(this.stateFilePath, "utf8");

    try {
      const parsed = JSON.parse(text) as Partial<LauncherState>;
      return {
        ...defaultState(),
        ...parsed,
        schemaVersion: LAUNCHER_STATE_SCHEMA_VERSION,
        installedTargets: Array.isArray(parsed.installedTargets) ? parsed.installedTargets : []
      };
    } catch {
      return defaultState();
    }
  }

  public async write(nextState: LauncherState): Promise<void> {
    await ensureDir(this.getParentDir());

    const tempPath = `${this.stateFilePath}.tmp`;
    const payload = JSON.stringify(nextState, null, 2);

    await writeFile(tempPath, payload, "utf8");
    await rename(tempPath, this.stateFilePath);
  }

  public async upsertInstalledTarget(entry: InstalledTargetState): Promise<void> {
    const state = await this.read();

    const filtered = state.installedTargets.filter((existing) => existing.target !== entry.target);
    filtered.push(entry);

    await this.write({
      ...state,
      installedTargets: filtered
    });
  }

  public async updatePrismRuntime(runtime: PrismRuntimeState): Promise<void> {
    const state = await this.read();
    await this.write({
      ...state,
      prismRuntime: runtime
    });
  }

  public async getInstalledTarget(target: InstallTarget): Promise<InstalledTargetState | undefined> {
    const state = await this.read();
    return state.installedTargets.find((entry) => entry.target === target);
  }

  private getParentDir(): string {
    return path.dirname(this.stateFilePath);
  }
}
