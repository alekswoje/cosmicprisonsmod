import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";

export type LogLevel = "INFO" | "WARN" | "ERROR";

export class Logger {
  private readonly logFilePath: string;

  public constructor(logsDir: string) {
    this.logFilePath = path.join(logsDir, "launcher.log");
  }

  public getLogFilePath(): string {
    return this.logFilePath;
  }

  public async info(message: string, context?: unknown): Promise<void> {
    await this.write("INFO", message, context);
  }

  public async warn(message: string, context?: unknown): Promise<void> {
    await this.write("WARN", message, context);
  }

  public async error(message: string, context?: unknown): Promise<void> {
    await this.write("ERROR", message, context);
  }

  private async write(level: LogLevel, message: string, context?: unknown): Promise<void> {
    const payload = {
      timestamp: new Date().toISOString(),
      level,
      message,
      context
    };

    const line = `${JSON.stringify(payload)}\n`;

    await mkdir(path.dirname(this.logFilePath), { recursive: true });
    await appendFile(this.logFilePath, line, "utf8");

    if (level === "ERROR") {
      // eslint-disable-next-line no-console
      console.error(message, context);
    } else if (level === "WARN") {
      // eslint-disable-next-line no-console
      console.warn(message, context);
    } else {
      // eslint-disable-next-line no-console
      console.log(message, context);
    }
  }
}
