package me.landon.companion.network;

import me.landon.companion.protocol.ProtocolConstants;
import net.minecraft.util.Identifier;

public final class PayloadFallbackDecider {
    private PayloadFallbackDecider() {}

    public static boolean shouldOverrideCodec(boolean fallbackEnabled, Identifier channelId) {
        return fallbackEnabled && ProtocolConstants.CHANNEL_ID.equals(channelId);
    }
}
