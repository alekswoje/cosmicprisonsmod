package me.landon.companion.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.landon.companion.protocol.ProtocolConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class PayloadFallbackDeciderTest {
    @Test
    void fallbackDisabledAlwaysReturnsFalse() {
        assertFalse(
                PayloadFallbackDecider.shouldOverrideCodec(false, ProtocolConstants.CHANNEL_ID));
    }

    @Test
    void fallbackEnabledOnlyMatchesCompanionChannel() {
        assertTrue(PayloadFallbackDecider.shouldOverrideCodec(true, ProtocolConstants.CHANNEL_ID));
        assertFalse(
                PayloadFallbackDecider.shouldOverrideCodec(
                        true, Identifier.of("minecraft", "brand")));
    }
}
