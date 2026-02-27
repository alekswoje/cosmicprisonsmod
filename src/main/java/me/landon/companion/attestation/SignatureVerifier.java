package me.landon.companion.attestation;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import me.landon.companion.config.CompanionConfig;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SignatureVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(SignatureVerifier.class);

    public boolean verifyServerHello(
            ProtocolMessage.ServerHelloS2C serverHello, CompanionConfig config) {
        if (config.isSignatureVerificationOff()) {
            return true;
        }

        boolean valid = verifySignature(serverHello, config);

        if (valid) {
            return true;
        }

        if (config.isSignatureVerificationLogOnly()) {
            LOGGER.warn(
                    "ServerHello signature verification failed in LOG_ONLY mode for serverId={}",
                    serverHello.serverId());
            return true;
        }

        return false;
    }

    private boolean verifySignature(
            ProtocolMessage.ServerHelloS2C serverHello, CompanionConfig config) {
        if (serverHello.signature().isEmpty()) {
            return false;
        }

        byte[] signature = serverHello.signature().orElseThrow().value();

        if (config.serverSignaturePublicKeys.isEmpty()) {
            return false;
        }

        byte[] canonicalBytes = canonicalPayload(serverHello).getBytes(StandardCharsets.UTF_8);

        for (String encodedKey : config.serverSignaturePublicKeys) {
            if (isValidForKey(encodedKey, canonicalBytes, signature)) {
                return true;
            }
        }

        return false;
    }

    private static String canonicalPayload(ProtocolMessage.ServerHelloS2C serverHello) {
        return "v="
                + ProtocolConstants.PROTOCOL_VERSION
                + ";serverId="
                + serverHello.serverId()
                + ";plugin="
                + serverHello.serverPluginVersion()
                + ";flags="
                + serverHello.serverFeatureFlagsBitset();
    }

    private static boolean isValidForKey(
            String encodedKey, byte[] canonicalBytes, byte[] signatureBytes) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(encodedKey);
            PublicKey publicKey =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(canonicalBytes);
            return signature.verify(signatureBytes);
        } catch (Exception ignored) {
            return false;
        }
    }
}
