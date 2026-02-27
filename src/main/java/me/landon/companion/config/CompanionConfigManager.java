package me.landon.companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionConfigManager.class);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path configPath =
            FabricLoader.getInstance().getConfigDir().resolve("cosmicprisonsmod-client.json");

    private CompanionConfig cached;

    public synchronized CompanionConfig load() {
        CompanionConfig config;

        if (Files.exists(configPath)) {
            config = readFromDisk();
        } else {
            config = CompanionConfig.defaults();
            writeToDisk(config);
        }

        config.sanitize();
        writeToDisk(config);
        cached = config;
        return config;
    }

    public synchronized CompanionConfig getOrLoad() {
        return cached == null ? load() : cached;
    }

    public synchronized void save(CompanionConfig config) {
        config.sanitize();
        writeToDisk(config);
        cached = config;
    }

    private CompanionConfig readFromDisk() {
        try (Reader reader = Files.newBufferedReader(configPath)) {
            CompanionConfig config = gson.fromJson(reader, CompanionConfig.class);
            return config == null ? CompanionConfig.defaults() : config;
        } catch (IOException ex) {
            LOGGER.warn("Failed to read companion config from {}, using defaults", configPath, ex);
            return CompanionConfig.defaults();
        }
    }

    private void writeToDisk(CompanionConfig config) {
        try {
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(config, writer);
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to write companion config to {}", configPath, ex);
        }
    }
}
