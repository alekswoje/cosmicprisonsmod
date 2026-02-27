package me.landon.companion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class CompanionNetworkBootstrap {
    private CompanionNetworkBootstrap() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(CompanionRawPayload.ID, CompanionRawPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CompanionRawPayload.ID, CompanionRawPayload.CODEC);
    }
}
