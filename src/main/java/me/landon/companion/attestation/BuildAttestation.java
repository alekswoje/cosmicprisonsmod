package me.landon.companion.attestation;

import java.util.Objects;

public final class BuildAttestation {
    private final String modVersion;
    private final String buildId;
    private final String jarSha256;
    private final String issuedAt;
    private final String signature;

    public BuildAttestation(
            String modVersion,
            String buildId,
            String jarSha256,
            String issuedAt,
            String signature) {
        this.modVersion = sanitize(modVersion);
        this.buildId = sanitize(buildId);
        this.jarSha256 = sanitize(jarSha256);
        this.issuedAt = sanitize(issuedAt);
        this.signature = sanitize(signature);
    }

    public String asStructuredClientVersion() {
        return "mod="
                + modVersion
                + ";build="
                + buildId
                + ";sha256="
                + jarSha256
                + ";iat="
                + issuedAt
                + ";sig="
                + signature
                + ";signature="
                + signature;
    }

    public String buildId() {
        return buildId;
    }

    public String modVersion() {
        return modVersion;
    }

    public String jarSha256() {
        return jarSha256;
    }

    public String issuedAt() {
        return issuedAt;
    }

    public String signature() {
        return signature;
    }

    public boolean isSigned() {
        return !"UNSIGNED".equals(signature) && !"UNKNOWN".equals(signature);
    }

    private static String sanitize(String value) {
        String clean = Objects.requireNonNullElse(value, "UNKNOWN").trim();

        if (clean.isEmpty()) {
            clean = "UNKNOWN";
        }

        return clean.replace(';', '_');
    }
}
