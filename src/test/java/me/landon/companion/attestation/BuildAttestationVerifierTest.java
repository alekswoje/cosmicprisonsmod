package me.landon.companion.attestation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BuildAttestationVerifierTest {
    @Test
    void acceptsValidSignedAttestation() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        String publicSpkiBase64 = encodeSpki(keyPair.getPublic());

        Instant now = Instant.parse("2026-02-27T10:00:00Z");
        BuildAttestationVerifier verifier =
                new BuildAttestationVerifier(publicSpkiBase64, Clock.fixed(now, ZoneOffset.UTC));

        BuildAttestation attestation =
                signedAttestation(keyPair.getPrivate(), "v1.2.1", "2026-02-27T09:59:30Z");

        BuildAttestationVerifier.VerificationResult result =
                verifier.verifySignedMetadata(attestation);
        assertTrue(result.valid(), result.reason());
    }

    @Test
    void rejectsTamperedSignedAttestation() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        String publicSpkiBase64 = encodeSpki(keyPair.getPublic());
        Instant now = Instant.parse("2026-02-27T10:00:00Z");
        BuildAttestationVerifier verifier =
                new BuildAttestationVerifier(publicSpkiBase64, Clock.fixed(now, ZoneOffset.UTC));

        BuildAttestation validAttestation =
                signedAttestation(keyPair.getPrivate(), "v1.2.1", "2026-02-27T09:59:30Z");
        BuildAttestation tampered =
                new BuildAttestation(
                        validAttestation.modVersion(),
                        validAttestation.buildId(),
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        validAttestation.issuedAt(),
                        validAttestation.signature());

        BuildAttestationVerifier.VerificationResult result =
                verifier.verifySignedMetadata(tampered);
        assertFalse(result.valid());
    }

    @Test
    void rejectsFutureIssuedAtOutsideSkewWindow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        String publicSpkiBase64 = encodeSpki(keyPair.getPublic());
        Instant now = Instant.parse("2026-02-27T10:00:00Z");
        BuildAttestationVerifier verifier =
                new BuildAttestationVerifier(publicSpkiBase64, Clock.fixed(now, ZoneOffset.UTC));

        BuildAttestation attestation =
                signedAttestation(keyPair.getPrivate(), "v1.2.1", "2026-02-27T10:10:00Z");

        BuildAttestationVerifier.VerificationResult result =
                verifier.verifySignedMetadata(attestation);
        assertFalse(result.valid());
    }

    @Test
    void verifiesArtifactSha256MatchAndMismatch() throws Exception {
        BuildAttestationVerifier verifier = new BuildAttestationVerifier();
        Path artifact = Files.createTempFile("cpm-attestation-test", ".jar");

        try {
            Files.writeString(artifact, "official-artifact", StandardCharsets.UTF_8);
            String actualSha = sha256Hex(artifact);

            BuildAttestation matching =
                    new BuildAttestation(
                            "1.2.1", "v1.2.1", actualSha, "2026-02-27T09:59:30Z", "UNSIGNED");
            BuildAttestationVerifier.VerificationResult matchingResult =
                    verifier.verifyArtifactSha256(matching, artifact);
            assertTrue(matchingResult.valid(), matchingResult.reason());

            BuildAttestation mismatched =
                    new BuildAttestation(
                            "1.2.1",
                            "v1.2.1",
                            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                            "2026-02-27T09:59:30Z",
                            "UNSIGNED");
            BuildAttestationVerifier.VerificationResult mismatchResult =
                    verifier.verifyArtifactSha256(mismatched, artifact);
            assertFalse(mismatchResult.valid());
        } finally {
            Files.deleteIfExists(artifact);
        }
    }

    private static BuildAttestation signedAttestation(
            PrivateKey privateKey, String buildId, String issuedAt) throws Exception {
        String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        BuildAttestation unsignedAttestation =
                new BuildAttestation("1.2.1", buildId, sha256, issuedAt, "UNSIGNED");
        String signature = signCanonicalPayload(privateKey, unsignedAttestation.canonicalPayload());
        return new BuildAttestation("1.2.1", buildId, sha256, issuedAt, signature);
    }

    private static String signCanonicalPayload(PrivateKey privateKey, String payload)
            throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = signature.sign();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        return keyPairGenerator.generateKeyPair();
    }

    private static String encodeSpki(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private static String sha256Hex(Path path) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(path);
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
