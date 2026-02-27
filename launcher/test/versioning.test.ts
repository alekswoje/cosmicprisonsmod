import { describe, expect, it } from "vitest";
import { isVersionNewer } from "../src/main/versioning";

describe("isVersionNewer", () => {
  it("returns true when latest semver is greater", () => {
    expect(isVersionNewer("1.0.0", "1.1.0")).toBe(true);
  });

  it("returns false when versions are equal", () => {
    expect(isVersionNewer("1.0.0", "1.0.0")).toBe(false);
  });

  it("falls back to string compare for non-semver", () => {
    expect(isVersionNewer("release-a", "release-b")).toBe(true);
    expect(isVersionNewer("release-a", "release-a")).toBe(false);
  });
});
