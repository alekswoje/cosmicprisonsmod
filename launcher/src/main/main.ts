import path from "node:path";
import { app, BrowserWindow, dialog, ipcMain, shell } from "electron";
import { getLauncherDirectories } from "./platformPaths";
import { Logger } from "./logger";
import { StateStore } from "./stateStore";
import { PrismRuntimeService } from "./prismRuntimeService";
import { UpdateService } from "./updateService";
import type { ApplyUpdatesRequest } from "../types/contracts";

let updateService: UpdateService;
let logger: Logger;

function rendererHtmlPath(): string {
  return path.join(__dirname, "..", "renderer", "index.html");
}

function preloadPath(): string {
  return path.join(__dirname, "..", "preload", "preload.js");
}

async function createWindow(): Promise<void> {
  const window = new BrowserWindow({
    width: 1080,
    height: 760,
    minWidth: 960,
    minHeight: 640,
    autoHideMenuBar: true,
    backgroundColor: "#0d1018",
    webPreferences: {
      preload: preloadPath(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  await window.loadFile(rendererHtmlPath());
}

function bindIpc(): void {
  ipcMain.handle("launcher.getStatus", async () => updateService.getStatus(false));

  ipcMain.handle("launcher.playDirect", async () => {
    try {
      return await updateService.playDirect();
    } catch (error) {
      await logger.error("Play Direct failed", { error: (error as Error).message });
      throw error;
    }
  });

  ipcMain.handle("launcher.installNative", async () => {
    try {
      return await updateService.installNative();
    } catch (error) {
      await logger.error("Native install failed", { error: (error as Error).message });
      throw error;
    }
  });

  ipcMain.handle("launcher.installLunar", async (_, targetPath?: string) => {
    try {
      return await updateService.installLunar(targetPath);
    } catch (error) {
      await logger.error("Lunar install failed", { error: (error as Error).message });
      throw error;
    }
  });

  ipcMain.handle("launcher.selectFolder", async () => {
    const result = await dialog.showOpenDialog({
      title: "Choose Mods Folder",
      properties: ["openDirectory", "createDirectory"]
    });

    if (result.canceled || result.filePaths.length === 0) {
      return null;
    }

    return result.filePaths[0];
  });

  ipcMain.handle("launcher.checkUpdates", async (_, force = true) => {
    try {
      return await updateService.checkUpdates(Boolean(force));
    } catch (error) {
      await logger.error("Update check failed", { error: (error as Error).message });
      throw error;
    }
  });

  ipcMain.handle("launcher.applyUpdates", async (_, request: ApplyUpdatesRequest) => {
    try {
      return await updateService.applyUpdates(request);
    } catch (error) {
      await logger.error("Apply updates failed", { error: (error as Error).message });
      throw error;
    }
  });

  ipcMain.handle("launcher.openLogs", async () => {
    await shell.showItemInFolder(logger.getLogFilePath());
  });
}

async function bootstrap(): Promise<void> {
  app.setName("Cosmic Prisons Launcher");
  await app.whenReady();

  const directories = getLauncherDirectories(app.getPath("userData"));
  logger = new Logger(directories.logsDir);

  const stateStore = new StateStore(directories.stateFilePath);
  const prismRuntimeService = new PrismRuntimeService();

  updateService = new UpdateService({
    appVersion: app.getVersion(),
    directories,
    logger,
    prismRuntimeService,
    stateStore
  });

  bindIpc();
  await createWindow();

  void updateService.checkUpdates(false).catch(async (error: unknown) => {
    await logger.warn("Initial background update check failed", { error: (error as Error).message });
  });

  setInterval(() => {
    void updateService.checkUpdates(false).catch(async (error: unknown) => {
      await logger.warn("Scheduled update check failed", { error: (error as Error).message });
    });
  }, 6 * 60 * 60 * 1000);

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      void createWindow();
    }
  });
}

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

void bootstrap().catch((error) => {
  // eslint-disable-next-line no-console
  console.error("Launcher bootstrap failed", error);
  app.quit();
});
