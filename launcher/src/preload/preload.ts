import { contextBridge, ipcRenderer } from "electron";
import type {
  ApplyUpdatesRequest,
  ApplyUpdatesResult,
  CheckUpdatesResponse,
  InstallLunarResponse,
  InstallResult,
  PlayDirectResult,
  StatusResponse
} from "../types/contracts";

const api = {
  getStatus: (): Promise<StatusResponse> => ipcRenderer.invoke("launcher.getStatus"),
  playDirect: (): Promise<PlayDirectResult> => ipcRenderer.invoke("launcher.playDirect"),
  installNative: (): Promise<InstallResult> => ipcRenderer.invoke("launcher.installNative"),
  installLunar: (targetPath?: string): Promise<InstallLunarResponse> =>
    ipcRenderer.invoke("launcher.installLunar", targetPath),
  selectFolder: (): Promise<string | null> => ipcRenderer.invoke("launcher.selectFolder"),
  checkUpdates: (force = true): Promise<CheckUpdatesResponse> =>
    ipcRenderer.invoke("launcher.checkUpdates", force),
  applyUpdates: (request: ApplyUpdatesRequest): Promise<ApplyUpdatesResult> =>
    ipcRenderer.invoke("launcher.applyUpdates", request),
  openLogs: (): Promise<void> => ipcRenderer.invoke("launcher.openLogs")
};

contextBridge.exposeInMainWorld("launcherApi", api);
