import { access, mkdir, stat } from "node:fs/promises";
import { constants } from "node:fs";

export async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

export async function ensureDir(path: string): Promise<void> {
  await mkdir(path, { recursive: true });
}

export async function isDirectory(path: string): Promise<boolean> {
  try {
    const fileStats = await stat(path);
    return fileStats.isDirectory();
  } catch {
    return false;
  }
}
