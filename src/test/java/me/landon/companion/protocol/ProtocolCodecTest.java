package me.landon.companion.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    private static final String GOLDEN_SERVER_HELLO_BASE64 =
            "AQITY29zbWljLXByaXNvbnMtcHJvZAUxLjQuMgU=";
    private static final String GOLDEN_ITEM_OVERLAYS_BASE64 = "AQoCAAEFMTIuNUsMAgQ5OTlN";

    private final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void roundtripClientHello() throws Exception {
        assertRoundtrip(
                new ProtocolMessage.ClientHelloC2S(
                        "mod=1.0.0;build=dev;sha256=unknown;iat=now;sig=UNSIGNED", 63));
    }

    @Test
    void roundtripServerHello() throws Exception {
        assertRoundtrip(
                new ProtocolMessage.ServerHelloS2C(
                        "cosmic-prisons-prod",
                        "1.4.2",
                        32,
                        Optional.of(
                                new ProtocolMessage.SignatureBytes(new byte[] {10, 11, 12, 13}))));
    }

    @Test
    void roundtripInventoryItemOverlays() throws Exception {
        assertRoundtrip(
                new ProtocolMessage.InventoryItemOverlaysS2C(
                        List.of(
                                new ProtocolMessage.InventoryItemOverlay(0, 1, "12.5K"),
                                new ProtocolMessage.InventoryItemOverlay(12, 2, "999M"))));
    }

    @Test
    void rejectsOversizedString() {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(MessageType.CLIENT_HELLO_C2S.id());

        int oversizedLength = ProtocolConstants.MAX_STRING_BYTES + 1;
        writer.writeVarInt(oversizedLength);
        writer.writeBytes(new byte[oversizedLength]);
        writer.writeVarInt(0);

        assertThrows(BinaryDecodingException.class, () -> codec.decode(writer.toByteArray()));
    }

    @Test
    void rejectsOversizedOverlayText() {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(MessageType.INVENTORY_ITEM_OVERLAYS_S2C.id());
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        writer.writeVarInt(1);
        writer.writeVarInt(ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES + 1);
        writer.writeBytes(new byte[ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES + 1]);

        assertThrows(BinaryDecodingException.class, () -> codec.decode(writer.toByteArray()));
    }

    @Test
    void rejectsInvalidVarInt() {
        byte[] invalidVarInt = new byte[] {(byte) 0x80};
        assertThrows(BinaryDecodingException.class, () -> codec.decode(invalidVarInt));
    }

    @Test
    void rejectsOversizedOverlayCount() {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(MessageType.INVENTORY_ITEM_OVERLAYS_S2C.id());
        writer.writeVarInt(ProtocolConstants.MAX_ITEM_OVERLAY_COUNT + 1);

        assertThrows(BinaryDecodingException.class, () -> codec.decode(writer.toByteArray()));
    }

    @Test
    void rejectsUnknownMessageType() {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(3);

        assertThrows(BinaryDecodingException.class, () -> codec.decode(writer.toByteArray()));
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] encoded =
                codec.encode(
                        new ProtocolMessage.InventoryItemOverlaysS2C(
                                List.of(new ProtocolMessage.InventoryItemOverlay(0, 1, "15K"))));
        byte[] withTrailing = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, withTrailing, 0, encoded.length);
        withTrailing[withTrailing.length - 1] = 0x01;

        assertThrows(BinaryDecodingException.class, () -> codec.decode(withTrailing));
    }

    @Test
    void rejectsTruncatedPacket() {
        byte[] encoded =
                codec.encode(
                        new ProtocolMessage.InventoryItemOverlaysS2C(
                                List.of(new ProtocolMessage.InventoryItemOverlay(0, 1, "15K"))));
        byte[] truncated = new byte[encoded.length - 1];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);

        assertThrows(BinaryDecodingException.class, () -> codec.decode(truncated));
    }

    @Test
    void goldenVectorServerHello() {
        ProtocolMessage.ServerHelloS2C serverHello =
                new ProtocolMessage.ServerHelloS2C(
                        "cosmic-prisons-prod", "1.4.2", 5, Optional.empty());
        byte[] encoded = codec.encode(serverHello);
        assertEquals(GOLDEN_SERVER_HELLO_BASE64, Base64.getEncoder().encodeToString(encoded));
    }

    @Test
    void goldenVectorInventoryItemOverlays() {
        ProtocolMessage.InventoryItemOverlaysS2C overlays =
                new ProtocolMessage.InventoryItemOverlaysS2C(
                        List.of(
                                new ProtocolMessage.InventoryItemOverlay(0, 1, "12.5K"),
                                new ProtocolMessage.InventoryItemOverlay(12, 2, "999M")));
        byte[] encoded = codec.encode(overlays);
        assertEquals(GOLDEN_ITEM_OVERLAYS_BASE64, Base64.getEncoder().encodeToString(encoded));
    }

    private void assertRoundtrip(ProtocolMessage message) throws Exception {
        byte[] encoded = codec.encode(message);
        ProtocolCodec.DecodedFrame decoded = codec.decode(encoded);

        assertEquals(ProtocolConstants.PROTOCOL_VERSION, decoded.protocolVersion());
        assertEquals(message, decoded.message());
        assertArrayEquals(encoded, codec.encode(decoded.message()));
    }
}
