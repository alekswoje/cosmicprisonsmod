import semver from "semver";

function coerceVersion(value: string): semver.SemVer | null {
  const coerced = semver.coerce(value);
  return coerced ?? null;
}

export function isVersionNewer(currentVersion: string, latestVersion: string): boolean {
  const current = coerceVersion(currentVersion);
  const latest = coerceVersion(latestVersion);

  if (!current || !latest) {
    return currentVersion !== latestVersion;
  }

  return semver.lt(current, latest);
}

export function toIsoNow(): string {
  return new Date().toISOString();
}
