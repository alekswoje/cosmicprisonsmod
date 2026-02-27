package me.landon.companion.protocol;

public enum MessageType {
    CLIENT_HELLO_C2S(1),
    SERVER_HELLO_S2C(2),
    HUD_WIDGET_STATE_S2C(3),
    ENTITY_MARKER_DELTA_S2C(4),
    PING_INTENT_C2S(ProtocolConstants.MESSAGE_TYPE_PING_INTENT_C2S),
    INVENTORY_ITEM_OVERLAYS_S2C(10);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) throws BinaryDecodingException {
        for (MessageType value : values()) {
            if (value.id == id) {
                return value;
            }
        }

        throw new BinaryDecodingException("Unknown message type: " + id);
    }
}
