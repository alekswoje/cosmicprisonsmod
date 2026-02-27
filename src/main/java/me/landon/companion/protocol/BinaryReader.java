package me.landon.companion.protocol;

import java.nio.charset.StandardCharsets;

public final class BinaryReader {
    private static final int VAR_INT_MAX_BYTES = 5;

    private final byte[] data;
    private int cursor;

    public BinaryReader(byte[] data) {
        this.data = data.clone();
    }

    public int readVarInt() throws BinaryDecodingException {
        int numRead = 0;
        int result = 0;
        byte read;

        do {
            if (!hasRemaining()) {
                throw new BinaryDecodingException("Unexpected end of packet while reading VarInt");
            }

            read = readByte();
            int value = read & 0x7F;
            result |= value << (7 * numRead);

            numRead++;
            if (numRead > VAR_INT_MAX_BYTES) {
                throw new BinaryDecodingException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }

    public long readLong() throws BinaryDecodingException {
        if (remaining() < Long.BYTES) {
            throw new BinaryDecodingException("Unexpected end of packet while reading long");
        }

        long result = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            result <<= 8;
            result |= readUnsignedByte();
        }

        return result;
    }

    public int readUnsignedByte() throws BinaryDecodingException {
        return readByte() & 0xFF;
    }

    public byte readByte() throws BinaryDecodingException {
        if (!hasRemaining()) {
            throw new BinaryDecodingException("Unexpected end of packet");
        }

        return data[cursor++];
    }

    public byte[] readBytes(int byteCount) throws BinaryDecodingException {
        if (byteCount < 0 || byteCount > remaining()) {
            throw new BinaryDecodingException("Requested byte count out of bounds: " + byteCount);
        }

        byte[] out = new byte[byteCount];
        System.arraycopy(data, cursor, out, 0, byteCount);
        cursor += byteCount;
        return out;
    }

    public String readString(int maxBytes) throws BinaryDecodingException {
        int length = readVarInt();

        if (length < 0 || length > maxBytes) {
            throw new BinaryDecodingException("String byte length out of bounds: " + length);
        }

        byte[] utf8 = readBytes(length);
        return new String(utf8, StandardCharsets.UTF_8);
    }

    public int remaining() {
        return data.length - cursor;
    }

    public boolean hasRemaining() {
        return cursor < data.length;
    }
}
