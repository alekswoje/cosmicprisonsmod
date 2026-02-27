import type {
  ApplyUpdatesRequest,
  InstallLunarResponse,
  InstallTarget,
  StatusResponse,
  UpdateSummary
} from "../types/contracts";

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Missing required element with id '${id}'.`);
  }

  return element as T;
}

const playDirectButton = requiredElement<HTMLButtonElement>("play-direct-btn");
const installOpenButton = requiredElement<HTMLButtonElement>("install-open-btn");
const updateButton = requiredElement<HTMLButtonElement>("update-btn");
const openLogsButton = requiredElement<HTMLButtonElement>("open-logs-btn");

const installModal = requiredElement<HTMLDivElement>("install-modal");
const closeInstallModalButton = requiredElement<HTMLButtonElement>("close-install-modal-btn");
const installNativeButton = requiredElement<HTMLButtonElement>("install-native-btn");
const installLunarButton = requiredElement<HTMLButtonElement>("install-lunar-btn");
const installLunarSelectedButton = requiredElement<HTMLButtonElement>("install-lunar-selected-btn");
const chooseLunarFolderButton = requiredElement<HTMLButtonElement>("choose-lunar-folder-btn");
const lunarPicker = requiredElement<HTMLDivElement>("lunar-picker");
const lunarSelect = requiredElement<HTMLSelectElement>("lunar-candidate-select");

const versionPill = requiredElement<HTMLSpanElement>("version-pill");
const installSummary = requiredElement<HTMLSpanElement>("install-summary");
const messageBox = requiredElement<HTMLDivElement>("message-box");

const actionButtons = [
  playDirectButton,
  installOpenButton,
  updateButton,
  openLogsButton,
  closeInstallModalButton,
  installNativeButton,
  installLunarButton,
  installLunarSelectedButton,
  chooseLunarFolderButton
];

let latestStatus: StatusResponse | undefined;
let latestSummary: UpdateSummary | undefined;

function setBusy(isBusy: boolean): void {
  for (const button of actionButtons) {
    if (button.classList.contains("hidden")) {
      continue;
    }

    button.disabled = isBusy;
  }
}

function showMessage(text: string, isError = false): void {
  messageBox.textContent = text;
  messageBox.classList.remove("hidden");
  messageBox.classList.toggle("error", isError);
}

function clearMessage(): void {
  messageBox.textContent = "";
  messageBox.classList.remove("error");
  messageBox.classList.add("hidden");
}

function showInstallModal(): void {
  installModal.classList.remove("hidden");
}

function hideLunarPicker(): void {
  lunarPicker.classList.add("hidden");
}

function hideInstallModal(): void {
  installModal.classList.add("hidden");
  hideLunarPicker();
}

function setLunarCandidates(candidates: string[]): void {
  lunarSelect.innerHTML = "";

  for (const candidate of candidates) {
    const option = document.createElement("option");
    option.value = candidate;
    option.textContent = candidate;
    lunarSelect.append(option);
  }

  lunarPicker.classList.remove("hidden");
}

function prettyTarget(target: InstallTarget): string {
  if (target === "prismManaged") {
    return "Play Directly";
  }

  if (target === "nativeMinecraft") {
    return "Native Minecraft";
  }

  return "Lunar Client";
}

function updateInstallSummary(status: StatusResponse): void {
  if (status.installedTargets.length === 0) {
    installSummary.textContent = "No install detected yet.";
    return;
  }

  const labels = status.installedTargets.map((entry) => prettyTarget(entry.target));
  installSummary.textContent = `Installed in: ${labels.join(", ")}`;
}

function hasModUpdates(summary: UpdateSummary): boolean {
  return (
    summary.modUpdateByTarget.prismManaged ||
    summary.modUpdateByTarget.nativeMinecraft ||
    summary.modUpdateByTarget.lunarClient
  );
}

function updateVersionPill(summary?: UpdateSummary, warning = false): void {
  versionPill.classList.remove("ok", "warn", "error");

  if (!summary) {
    versionPill.textContent = warning ? "Offline" : "Checking updates...";
    versionPill.classList.add(warning ? "error" : "warn");
    return;
  }

  const updatesAvailable = summary.launcherUpdateAvailable || hasModUpdates(summary);
  if (updatesAvailable) {
    versionPill.textContent = `Update available (v${summary.modLatestVersion})`;
    versionPill.classList.add("warn");
  } else {
    versionPill.textContent = `Up to date (v${summary.modLatestVersion})`;
    versionPill.classList.add("ok");
  }

  if (summary.stale) {
    versionPill.textContent += " - cached";
    versionPill.classList.remove("ok", "warn");
    versionPill.classList.add("error");
  }
}

function setUpdateButtonVisibility(summary?: UpdateSummary): void {
  if (!summary) {
    updateButton.classList.add("hidden");
    return;
  }

  const updatesAvailable = summary.launcherUpdateAvailable || hasModUpdates(summary);
  updateButton.classList.toggle("hidden", !updatesAvailable);
}

function updateUiFromStatus(status: StatusResponse): void {
  latestStatus = status;
  latestSummary = status.updateSummary;

  updateInstallSummary(status);
  updateVersionPill(status.updateSummary, status.warnings.length > 0);
  setUpdateButtonVisibility(status.updateSummary);
}

async function refreshStatus(): Promise<void> {
  const status = await window.launcherApi.getStatus();
  updateUiFromStatus(status);
}

async function guardedAction(action: () => Promise<void>): Promise<void> {
  clearMessage();
  setBusy(true);

  try {
    await action();
  } catch (error) {
    showMessage((error as Error).message, true);
  } finally {
    setBusy(false);
  }
}

async function installLunar(targetPath?: string): Promise<void> {
  const result: InstallLunarResponse = await window.launcherApi.installLunar(targetPath);

  if (result.status === "requires_selection") {
    const candidates = result.candidates ?? [];

    if (candidates.length > 0) {
      setLunarCandidates(candidates);
      showMessage("Choose your Lunar folder.");
      return;
    }

    lunarPicker.classList.remove("hidden");
    showMessage("Select a Lunar mods folder.");
    return;
  }

  hideInstallModal();
  showMessage("Installed into Lunar Client.");
  await refreshStatus();
}

function collectUpdateTargets(summary: UpdateSummary): InstallTarget[] {
  const targets: InstallTarget[] = [];

  if (summary.modUpdateByTarget.prismManaged) {
    targets.push("prismManaged");
  }

  if (summary.modUpdateByTarget.nativeMinecraft) {
    targets.push("nativeMinecraft");
  }

  if (summary.modUpdateByTarget.lunarClient) {
    targets.push("lunarClient");
  }

  return targets;
}

playDirectButton.addEventListener("click", () => {
  void guardedAction(async () => {
    await window.launcherApi.playDirect();
    showMessage("Launching game and joining cosmicprisons.com...");
    await refreshStatus();
  });
});

installOpenButton.addEventListener("click", () => {
  showInstallModal();
});

closeInstallModalButton.addEventListener("click", () => {
  hideInstallModal();
});

installNativeButton.addEventListener("click", () => {
  void guardedAction(async () => {
    await window.launcherApi.installNative();
    hideInstallModal();
    showMessage("Installed into Native Minecraft.");
    await refreshStatus();
  });
});

installLunarButton.addEventListener("click", () => {
  void guardedAction(async () => {
    await installLunar();
  });
});

installLunarSelectedButton.addEventListener("click", () => {
  void guardedAction(async () => {
    const selectedPath = lunarSelect.value;
    if (!selectedPath) {
      throw new Error("Select a Lunar folder first.");
    }

    await installLunar(selectedPath);
  });
});

chooseLunarFolderButton.addEventListener("click", () => {
  void guardedAction(async () => {
    const selectedPath = await window.launcherApi.selectFolder();
    if (!selectedPath) {
      return;
    }

    await installLunar(selectedPath);
  });
});

updateButton.addEventListener("click", () => {
  void guardedAction(async () => {
    if (!latestSummary) {
      throw new Error("No update metadata available yet.");
    }

    const request: ApplyUpdatesRequest = {
      modTargets: collectUpdateTargets(latestSummary),
      includeLauncher: latestSummary.launcherUpdateAvailable
    };

    if (request.modTargets.length === 0 && !request.includeLauncher) {
      showMessage("Already up to date.");
      return;
    }

    const result = await window.launcherApi.applyUpdates(request);

    if (result.launcherStage) {
      showMessage("Update downloaded. Restart launcher to apply.");
    } else {
      showMessage("Update complete.");
    }

    await refreshStatus();
  });
});

openLogsButton.addEventListener("click", () => {
  void guardedAction(async () => {
    await window.launcherApi.openLogs();
  });
});

void guardedAction(async () => {
  hideInstallModal();
  await refreshStatus();
});
