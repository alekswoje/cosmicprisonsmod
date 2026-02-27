import { copyFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import { ensureDir } from "./fsUtils";
import { MOD_FILE_PATTERN } from "./constants";
import type { InstallResult, InstallTarget } from "../types/contracts";

export interface InstallModInput {
  target: InstallTarget;
  targetDir: string;
  sourceJarPath: string;
  destinationJarName: string;
  version: string;
}

export async function installModJar(input: InstallModInput): Promise<InstallResult> {
  const { target, targetDir, sourceJarPath, destinationJarName, version } = input;

  await ensureDir(targetDir);

  const entries = await readdir(targetDir, { withFileTypes: true });
  const removedFiles: string[] = [];

  for (const entry of entries) {
    if (!entry.isFile()) {
      continue;
    }

    if (!MOD_FILE_PATTERN.test(entry.name)) {
      continue;
    }

    const existingPath = path.join(targetDir, entry.name);
    if (entry.name !== destinationJarName) {
      await rm(existingPath, { force: true });
      removedFiles.push(existingPath);
    }
  }

  const destinationPath = path.join(targetDir, destinationJarName);
  await copyFile(sourceJarPath, destinationPath);

  return {
    target,
    installPath: destinationPath,
    version,
    removedFiles
  };
}
