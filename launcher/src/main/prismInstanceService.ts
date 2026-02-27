import { spawn } from "node:child_process";
import path from "node:path";
import { ensureDir, pathExists } from "./fsUtils";
import { COSMIC_SERVER_ADDRESS } from "./constants";

function runCommand(executablePath: string, args: string[]): Promise<void> {
  return new Promise((resolve, reject) => {
    const processRef = spawn(executablePath, args, {
      stdio: ["ignore", "pipe", "pipe"]
    });

    let errorOutput = "";
    processRef.stderr.on("data", (chunk: Buffer) => {
      errorOutput += chunk.toString("utf8");
    });

    processRef.on("error", (error) => {
      reject(error);
    });

    processRef.on("close", (exitCode) => {
      if (exitCode === 0) {
        resolve();
        return;
      }

      reject(
        new Error(
          `Command failed (${executablePath} ${args.join(" ")}) with code ${exitCode}. ${errorOutput.trim()}`
        )
      );
    });
  });
}

export function getManagedInstanceDirectory(prismDataDir: string, instanceId: string): string {
  return path.join(prismDataDir, "instances", instanceId);
}

export function getManagedInstanceModsDirectory(prismDataDir: string, instanceId: string): string {
  return path.join(getManagedInstanceDirectory(prismDataDir, instanceId), ".minecraft", "mods");
}

export async function ensureManagedInstance(
  prismExecutablePath: string,
  prismDataDir: string,
  instanceId: string,
  prismPackPath: string
): Promise<void> {
  const instanceDirectory = getManagedInstanceDirectory(prismDataDir, instanceId);
  if (await pathExists(instanceDirectory)) {
    return;
  }

  await ensureDir(prismDataDir);

  await runCommand(prismExecutablePath, ["--dir", prismDataDir, "--import", prismPackPath]);

  if (!(await pathExists(instanceDirectory))) {
    throw new Error(
      `Prism import finished but managed instance '${instanceId}' was not created in ${prismDataDir}`
    );
  }
}

export async function launchManagedInstance(
  prismExecutablePath: string,
  prismDataDir: string,
  instanceId: string,
  serverAddress: string = COSMIC_SERVER_ADDRESS
): Promise<void> {
  const processRef = spawn(prismExecutablePath, ["--dir", prismDataDir, "--launch", instanceId, "--server", serverAddress], {
    stdio: "ignore",
    detached: true
  });

  processRef.unref();
}
