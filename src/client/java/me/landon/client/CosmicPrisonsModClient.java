package me.landon.client;

import me.landon.client.runtime.CompanionClientRuntime;
import net.fabricmc.api.ClientModInitializer;

public final class CosmicPrisonsModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CompanionClientRuntime.getInstance().initializeClient();
    }
}
