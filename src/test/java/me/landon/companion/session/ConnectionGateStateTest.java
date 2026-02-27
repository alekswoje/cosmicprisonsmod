package me.landon.companion.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import me.landon.companion.config.CompanionConfig;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;
import org.junit.jupiter.api.Test;

class ConnectionGateStateTest {
    @Test
    void startsDisabled() {
        ConnectionGateState gateState = new ConnectionGateState();
        assertFalse(gateState.isEnabled());
    }

    @Test
    void enablesOnlyForValidServerHello() {
        ConnectionGateState gateState = new ConnectionGateState();
        CompanionConfig config = validConfig();

        ConnectionGateState.EnableResult invalidProtocolResult =
                gateState.tryEnable(
                        ProtocolConstants.PROTOCOL_VERSION + 1,
                        validServerHello(),
                        config,
                        (serverHello, ignored) -> true);
        assertEquals(ConnectionGateState.EnableResult.PROTOCOL_MISMATCH, invalidProtocolResult);
        assertFalse(gateState.isEnabled());

        ConnectionGateState.EnableResult validResult =
                gateState.tryEnable(
                        ProtocolConstants.PROTOCOL_VERSION,
                        validServerHello(),
                        config,
                        (serverHello, ignored) -> true);
        assertEquals(ConnectionGateState.EnableResult.ENABLED, validResult);
        assertTrue(gateState.isEnabled());
    }

    @Test
    void ignoresNonHelloWhenDisabled() {
        ConnectionGateState gateState = new ConnectionGateState();
        assertFalse(
                gateState.shouldProcessIncoming(
                        new ProtocolMessage.ClientHelloC2S("mod=1.0.0", 63)));
        assertTrue(gateState.shouldProcessIncoming(validServerHello()));
    }

    @Test
    void resetClearsEnabledState() {
        ConnectionGateState gateState = new ConnectionGateState();
        CompanionConfig config = validConfig();
        gateState.tryEnable(
                ProtocolConstants.PROTOCOL_VERSION,
                validServerHello(),
                config,
                (serverHello, ignored) -> true);
        assertTrue(gateState.isEnabled());

        gateState.reset();
        assertFalse(gateState.isEnabled());
    }

    @Test
    void signatureValidationIsRequiredWhenConfigured() {
        ConnectionGateState gateState = new ConnectionGateState();
        CompanionConfig config = validConfig();
        config.requireServerSignature = true;

        ConnectionGateState.EnableResult result =
                gateState.tryEnable(
                        ProtocolConstants.PROTOCOL_VERSION,
                        validServerHello(),
                        config,
                        (serverHello, ignored) -> false);

        assertEquals(ConnectionGateState.EnableResult.SIGNATURE_INVALID, result);
        assertFalse(gateState.isEnabled());
    }

    @Test
    void serverIdMatchIsCaseInsensitive() {
        ConnectionGateState gateState = new ConnectionGateState();
        CompanionConfig config = validConfig();
        ProtocolMessage.ServerHelloS2C mixedCaseHello =
                new ProtocolMessage.ServerHelloS2C(
                        "CosmicPrisons.com", "1.4.2", 1, Optional.empty());

        ConnectionGateState.EnableResult result =
                gateState.tryEnable(
                        ProtocolConstants.PROTOCOL_VERSION,
                        mixedCaseHello,
                        config,
                        (serverHello, ignored) -> true);

        assertEquals(ConnectionGateState.EnableResult.ENABLED, result);
        assertTrue(gateState.isEnabled());
    }

    private static ProtocolMessage.ServerHelloS2C validServerHello() {
        return new ProtocolMessage.ServerHelloS2C(
                "cosmicprisons.com", "1.4.2", 1, Optional.empty());
    }

    private static CompanionConfig validConfig() {
        CompanionConfig config = CompanionConfig.defaults();
        config.requireServerSignature = false;
        config.sanitize();
        return config;
    }
}
