package me.landon.companion.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildAttestationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildAttestationLoader.class);
    private static final String RESOURCE_PATH = "official-build.properties";
    private final BuildAttestationVerifier verifier = new BuildAttestationVerifier();

    public BuildAttestation load(String modVersion) {
        Properties properties = new Properties();
        boolean loadedFromResource = false;

        try (InputStream input =
                BuildAttestationLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input != null) {
                properties.load(input);
                loadedFromResource = true;
            } else {
                LOGGER.warn(
                        "No {} found on classpath, using unsigned local attestation",
                        RESOURCE_PATH);
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read {}, using unsigned local attestation", RESOURCE_PATH, ex);
        }

        if (!loadedFromResource) {
            return BuildAttestation.unsignedLocal(modVersion);
        }

        BuildAttestation attestation =
                new BuildAttestation(
                        modVersion,
                        properties.getProperty("buildId", "dev-local"),
                        properties.getProperty("jarSha256", "unknown"),
                        properties.getProperty("issuedAt", "unknown"),
                        properties.getProperty("signature", "UNSIGNED"));

        BuildAttestationVerifier.VerificationResult metadataResult =
                verifier.verifySignedMetadata(attestation);
        if (!metadataResult.valid()) {
            LOGGER.warn(
                    "Build attestation metadata failed verification: {}. Falling back to unsigned local mode.",
                    metadataResult.reason());
            return BuildAttestation.unsignedLocal(modVersion);
        }

        BuildAttestationVerifier.VerificationResult artifactResult =
                verifier.verifyRuntimeJarSha256(attestation, BuildAttestationLoader.class);
        if (!artifactResult.valid()) {
            LOGGER.warn(
                    "Build attestation runtime hash check failed: {}. Falling back to unsigned local mode.",
                    artifactResult.reason());
            return BuildAttestation.unsignedLocal(modVersion);
        }

        return attestation;
    }
}
