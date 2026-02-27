package me.landon.companion.protocol;

import java.util.Optional;

public final class ProtocolCodec {
    public record DecodedFrame(int protocolVersion, ProtocolMessage message) {}

    public byte[] encode(ProtocolMessage message) {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(message.type().id());

        switch (message) {
            case ProtocolMessage.ClientHelloC2S clientHello -> {
                writer.writeString(
                        clientHello.clientModVersion(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeVarInt(clientHello.clientCapabilitiesBitset());
            }
            case ProtocolMessage.ServerHelloS2C serverHello -> {
                writer.writeString(serverHello.serverId(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeString(
                        serverHello.serverPluginVersion(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeVarInt(serverHello.serverFeatureFlagsBitset());

                if (serverHello.signature().isPresent()) {
                    byte[] signature = serverHello.signature().orElseThrow().value();

                    if (signature.length > ProtocolConstants.MAX_SIGNATURE_BYTES) {
                        throw new IllegalArgumentException("Server hello signature is too large");
                    }

                    writer.writeByte(signature.length);
                    writer.writeBytes(signature);
                }
            }
            case ProtocolMessage.InventoryItemOverlaysS2C overlays -> {
                writeBoundedCount(
                        writer,
                        overlays.overlays().size(),
                        ProtocolConstants.MAX_ITEM_OVERLAY_COUNT,
                        "overlayCount");

                for (ProtocolMessage.InventoryItemOverlay overlay : overlays.overlays()) {
                    writer.writeVarInt(overlay.slot());
                    writer.writeVarInt(overlay.overlayType());
                    writer.writeString(
                            overlay.displayText(), ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES);
                }
            }
        }

        return writer.toByteArray();
    }

    public DecodedFrame decode(byte[] payload) throws BinaryDecodingException {
        if (payload.length > ProtocolConstants.MAX_PACKET_BYTES) {
            throw new BinaryDecodingException("Packet payload exceeds maximum size");
        }

        BinaryReader reader = new BinaryReader(payload);
        int protocolVersion = reader.readVarInt();
        int messageType = reader.readVarInt();

        ProtocolMessage message = decodeMessage(reader, MessageType.fromId(messageType));

        if (reader.hasRemaining()) {
            throw new BinaryDecodingException("Unexpected trailing bytes after message decode");
        }

        return new DecodedFrame(protocolVersion, message);
    }

    private ProtocolMessage decodeMessage(BinaryReader reader, MessageType messageType)
            throws BinaryDecodingException {
        return switch (messageType) {
            case CLIENT_HELLO_C2S ->
                    new ProtocolMessage.ClientHelloC2S(
                            reader.readString(ProtocolConstants.MAX_STRING_BYTES),
                            reader.readVarInt());
            case SERVER_HELLO_S2C -> decodeServerHello(reader);
            case INVENTORY_ITEM_OVERLAYS_S2C -> decodeInventoryItemOverlays(reader);
        };
    }

    private ProtocolMessage.ServerHelloS2C decodeServerHello(BinaryReader reader)
            throws BinaryDecodingException {
        String serverId = reader.readString(ProtocolConstants.MAX_STRING_BYTES);
        String pluginVersion = reader.readString(ProtocolConstants.MAX_STRING_BYTES);
        int featureFlags = reader.readVarInt();
        Optional<ProtocolMessage.SignatureBytes> signature = Optional.empty();

        if (reader.hasRemaining()) {
            int signatureLength = reader.readUnsignedByte();

            if (signatureLength > ProtocolConstants.MAX_SIGNATURE_BYTES) {
                throw new BinaryDecodingException("Server signature length exceeds maximum");
            }

            byte[] signatureBytes = reader.readBytes(signatureLength);
            signature = Optional.of(new ProtocolMessage.SignatureBytes(signatureBytes));
        }

        return new ProtocolMessage.ServerHelloS2C(serverId, pluginVersion, featureFlags, signature);
    }

    private ProtocolMessage.InventoryItemOverlaysS2C decodeInventoryItemOverlays(
            BinaryReader reader) throws BinaryDecodingException {
        int overlayCount =
                readBoundedCount(reader, ProtocolConstants.MAX_ITEM_OVERLAY_COUNT, "overlayCount");
        var overlays = ProtocolMessage.mutableInventoryItemOverlayListWithCapacity(overlayCount);

        for (int index = 0; index < overlayCount; index++) {
            int slot = readBoundedNonNegative(reader, Integer.MAX_VALUE, "slot");
            int overlayType = readBoundedNonNegative(reader, Integer.MAX_VALUE, "overlayType");
            String displayText = reader.readString(ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES);
            overlays.add(new ProtocolMessage.InventoryItemOverlay(slot, overlayType, displayText));
        }

        return new ProtocolMessage.InventoryItemOverlaysS2C(overlays);
    }

    private static void writeBoundedCount(
            BinaryWriter writer, int count, int maxCount, String fieldName) {
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException(fieldName + " out of bounds: " + count);
        }

        writer.writeVarInt(count);
    }

    private static int readBoundedCount(BinaryReader reader, int maxCount, String fieldName)
            throws BinaryDecodingException {
        int count = reader.readVarInt();

        if (count < 0 || count > maxCount) {
            throw new BinaryDecodingException(fieldName + " out of bounds: " + count);
        }

        return count;
    }

    private static int readBoundedNonNegative(BinaryReader reader, int maxValue, String fieldName)
            throws BinaryDecodingException {
        int value = reader.readVarInt();

        if (value < 0 || value > maxValue) {
            throw new BinaryDecodingException(fieldName + " out of bounds: " + value);
        }

        return value;
    }
}
