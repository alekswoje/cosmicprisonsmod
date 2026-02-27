import type { LauncherApi } from "./contracts";

declare global {
  interface Window {
    launcherApi: LauncherApi;
  }
}

export {};
