package me.landon.companion.network;

import java.util.Arrays;
import java.util.Objects;
import me.landon.companion.protocol.ProtocolConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public final class CompanionRawPayload implements CustomPayload {
    public static final CustomPayload.Id<CompanionRawPayload> ID =
            new CustomPayload.Id<>(ProtocolConstants.CHANNEL_ID);
    public static final PacketCodec<PacketByteBuf, CompanionRawPayload> CODEC =
            PacketCodec.of(CompanionRawPayload::encode, CompanionRawPayload::decode);

    private final byte[] payloadBytes;

    public CompanionRawPayload(byte[] payloadBytes) {
        this.payloadBytes = Objects.requireNonNull(payloadBytes, "payloadBytes").clone();

        if (this.payloadBytes.length > ProtocolConstants.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Companion payload exceeds maximum allowed bytes");
        }
    }

    private static CompanionRawPayload decode(PacketByteBuf buf) {
        return new CompanionRawPayload(buf.readByteArray(ProtocolConstants.MAX_PACKET_BYTES));
    }

    private static void encode(CompanionRawPayload payload, PacketByteBuf buf) {
        buf.writeByteArray(payload.payloadBytes);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public byte[] payloadBytes() {
        return Arrays.copyOf(payloadBytes, payloadBytes.length);
    }
}
