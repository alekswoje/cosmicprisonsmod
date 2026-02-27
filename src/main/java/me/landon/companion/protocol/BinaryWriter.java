package me.landon.companion.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class BinaryWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public void writeVarInt(int value) {
        int current = value;

        while ((current & 0xFFFFFF80) != 0) {
            output.write((current & 0x7F) | 0x80);
            current >>>= 7;
        }

        output.write(current & 0x7F);
    }

    public void writeLong(long value) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            output.write((int) (value >>> (i * 8)) & 0xFF);
        }
    }

    public void writeByte(int value) {
        output.write(value & 0xFF);
    }

    public void writeBytes(byte[] data) {
        output.writeBytes(data);
    }

    public void writeString(String value, int maxBytes) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);

        if (utf8.length > maxBytes) {
            throw new IllegalArgumentException(
                    "String too large for protocol field: " + utf8.length);
        }

        writeVarInt(utf8.length);
        writeBytes(utf8);
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }
}
