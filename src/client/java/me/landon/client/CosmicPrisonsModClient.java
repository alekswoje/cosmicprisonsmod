package me.landon.client;

import me.landon.client.runtime.CompanionClientRuntime;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint.
 *
 * <p>The mod runtime is centralized in {@link CompanionClientRuntime} so feature behavior, protocol
 * handling, and rendering state stay in one place.
 */
public final class CosmicPrisonsModClient implements ClientModInitializer {
    /** Bootstraps the singleton client runtime and registers all client-side event hooks. */
    @Override
    public void onInitializeClient() {
        CompanionClientRuntime.getInstance().initializeClient();
    }
}
