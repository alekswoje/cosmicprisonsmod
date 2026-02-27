package me.landon.companion.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BuildAttestationVerifier {
    public static final String PINNED_ATTESTATION_PUBLIC_SPKI_BASE64 =
            "MCowBQYDK2VwAyEAdvjSMc5qu8vFHmhTNDJY4FA/yBcNS82m3zHFEOAyBAE=";

    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Duration MAX_ISSUED_AT_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_ATTESTATION_AGE = Duration.ofDays(3650);

    private final String pinnedPublicKeySpkiBase64;
    private final Clock clock;

    public BuildAttestationVerifier() {
        this(PINNED_ATTESTATION_PUBLIC_SPKI_BASE64, Clock.systemUTC());
    }

    BuildAttestationVerifier(String pinnedPublicKeySpkiBase64, Clock clock) {
        this.pinnedPublicKeySpkiBase64 = Objects.requireNonNull(pinnedPublicKeySpkiBase64);
        this.clock = Objects.requireNonNull(clock);
    }

    public VerificationResult verifySignedMetadata(BuildAttestation attestation) {
        if (attestation == null) {
            return VerificationResult.invalid("missing attestation");
        }

        if (!attestation.isSigned()) {
            return VerificationResult.invalid("attestation signature missing");
        }

        if (!isHexSha256(attestation.jarSha256())) {
            return VerificationResult.invalid("attestation sha256 is not 64-char hex");
        }

        Instant issuedAt = parseIssuedAt(attestation.issuedAt());
        if (issuedAt == null) {
            return VerificationResult.invalid("attestation issuedAt is not valid ISO-8601 instant");
        }

        Instant now = Instant.now(clock);
        if (issuedAt.isAfter(now.plus(MAX_ISSUED_AT_FUTURE_SKEW))) {
            return VerificationResult.invalid("attestation issuedAt is too far in the future");
        }

        if (issuedAt.isBefore(now.minus(MAX_ATTESTATION_AGE))) {
            return VerificationResult.invalid("attestation issuedAt is too old");
        }

        byte[] signatureBytes;
        try {
            signatureBytes = decodeSignature(attestation.signature());
        } catch (Exception ex) {
            return VerificationResult.invalid(
                    "attestation signature is not valid base64/base64url");
        }

        if (signatureBytes.length != 64) {
            return VerificationResult.invalid("attestation signature length is invalid");
        }

        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(pinnedPublicKeySpkiBase64);
            PublicKey publicKey =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(attestation.canonicalPayload().getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signatureBytes)) {
                return VerificationResult.invalid("attestation signature verification failed");
            }
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            return VerificationResult.invalid(
                    "attestation signature verification failed: " + ex.getMessage());
        }

        return VerificationResult.success();
    }

    public VerificationResult verifyRuntimeJarSha256(
            BuildAttestation attestation, Class<?> anchorClass) {
        if (attestation == null) {
            return VerificationResult.invalid("missing attestation");
        }

        if (!isHexSha256(attestation.jarSha256())) {
            return VerificationResult.invalid("attestation sha256 is not 64-char hex");
        }

        Path runtimeArtifact = resolveRuntimeArtifactPath(anchorClass);
        if (runtimeArtifact == null) {
            return VerificationResult.invalid("runtime artifact path unavailable");
        }

        if (!Files.isRegularFile(runtimeArtifact)) {
            return VerificationResult.invalid("runtime artifact is not a file: " + runtimeArtifact);
        }

        return verifyArtifactSha256(attestation, runtimeArtifact);
    }

    VerificationResult verifyArtifactSha256(BuildAttestation attestation, Path artifactPath) {
        if (attestation == null) {
            return VerificationResult.invalid("missing attestation");
        }

        if (artifactPath == null || !Files.isRegularFile(artifactPath)) {
            return VerificationResult.invalid("artifact file not found");
        }

        String expectedSha = attestation.jarSha256().toLowerCase(Locale.ROOT);
        if (!isHexSha256(expectedSha)) {
            return VerificationResult.invalid("attestation sha256 is not 64-char hex");
        }

        String actualSha;
        try {
            actualSha = sha256Hex(artifactPath);
        } catch (IOException ex) {
            return VerificationResult.invalid(
                    "failed to hash runtime artifact: " + ex.getMessage());
        }

        if (!actualSha.equals(expectedSha)) {
            return VerificationResult.invalid(
                    "runtime artifact hash mismatch (expected "
                            + expectedSha
                            + ", got "
                            + actualSha
                            + ")");
        }

        return VerificationResult.success();
    }

    private static Instant parseIssuedAt(String issuedAt) {
        try {
            return Instant.parse(issuedAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolveRuntimeArtifactPath(Class<?> anchorClass) {
        try {
            CodeSource codeSource = anchorClass.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }

            return Path.of(codeSource.getLocation().toURI()).normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }

        byte[] buffer = new byte[8192];

        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isHexSha256(String value) {
        return value != null && SHA256_HEX_PATTERN.matcher(value).matches();
    }

    private static byte[] decodeSignature(String encodedSignature) {
        String trimmed = Objects.requireNonNullElse(encodedSignature, "").trim();
        if (trimmed.isEmpty()) {
            return new byte[0];
        }

        String padded = applyBase64Padding(trimmed);
        try {
            return Base64.getUrlDecoder().decode(padded);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(padded);
        }
    }

    private static String applyBase64Padding(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }

        return value + "====".substring(remainder);
    }

    public record VerificationResult(boolean valid, String reason) {
        public static VerificationResult success() {
            return new VerificationResult(true, "");
        }

        public static VerificationResult invalid(String reason) {
            return new VerificationResult(false, reason);
        }
    }
}
