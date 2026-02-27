package me.landon;

import me.landon.companion.network.CompanionNetworkBootstrap;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CosmicPrisonsMod implements ModInitializer {
    public static final String MOD_ID = "cosmicprisonsmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CompanionNetworkBootstrap.registerPayloadTypes();
        LOGGER.info("{} initialized", MOD_ID);
    }
}
