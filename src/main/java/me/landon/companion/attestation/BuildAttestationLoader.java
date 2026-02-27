package me.landon.companion.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildAttestationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildAttestationLoader.class);
    private static final String RESOURCE_PATH = "official-build.properties";

    public BuildAttestation load(String modVersion) {
        Properties properties = new Properties();

        try (InputStream input =
                BuildAttestationLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input != null) {
                properties.load(input);
            } else {
                LOGGER.warn(
                        "No {} found on classpath, using unsigned local attestation",
                        RESOURCE_PATH);
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read {}, using unsigned local attestation", RESOURCE_PATH, ex);
        }

        return new BuildAttestation(
                modVersion,
                properties.getProperty("buildId", "dev-local"),
                properties.getProperty("jarSha256", "unknown"),
                properties.getProperty("issuedAt", "unknown"),
                properties.getProperty("signature", "UNSIGNED"));
    }
}
