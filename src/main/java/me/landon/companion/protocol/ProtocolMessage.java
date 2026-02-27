package me.landon.companion.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface ProtocolMessage
        permits ProtocolMessage.ClientHelloC2S,
                ProtocolMessage.ServerHelloS2C,
                ProtocolMessage.EntityMarkerDeltaS2C,
                ProtocolMessage.InventoryItemOverlaysS2C {

    MessageType type();

    record SignatureBytes(byte[] value) {
        public SignatureBytes {
            value = Objects.requireNonNull(value, "value").clone();
        }

        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof SignatureBytes signatureBytes)) {
                return false;
            }

            return Arrays.equals(value, signatureBytes.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    record InventoryItemOverlay(int slot, int overlayType, String displayText) {
        public InventoryItemOverlay {
            displayText = Objects.requireNonNull(displayText, "displayText");
        }
    }

    record EntityMarkerDeltaS2C(
            int markerType, List<Integer> addEntityIds, List<Integer> removeEntityIds)
            implements ProtocolMessage {
        public EntityMarkerDeltaS2C {
            addEntityIds = List.copyOf(Objects.requireNonNull(addEntityIds, "addEntityIds"));
            removeEntityIds =
                    List.copyOf(Objects.requireNonNull(removeEntityIds, "removeEntityIds"));
        }

        @Override
        public MessageType type() {
            return MessageType.ENTITY_MARKER_DELTA_S2C;
        }
    }

    record ClientHelloC2S(String clientModVersion, int clientCapabilitiesBitset)
            implements ProtocolMessage {
        public ClientHelloC2S {
            clientModVersion = Objects.requireNonNull(clientModVersion, "clientModVersion");
        }

        @Override
        public MessageType type() {
            return MessageType.CLIENT_HELLO_C2S;
        }
    }

    record ServerHelloS2C(
            String serverId,
            String serverPluginVersion,
            int serverFeatureFlagsBitset,
            Optional<SignatureBytes> signature)
            implements ProtocolMessage {
        public ServerHelloS2C {
            serverId = Objects.requireNonNull(serverId, "serverId");
            serverPluginVersion =
                    Objects.requireNonNull(serverPluginVersion, "serverPluginVersion");
            signature = Objects.requireNonNull(signature, "signature");
        }

        @Override
        public MessageType type() {
            return MessageType.SERVER_HELLO_S2C;
        }
    }

    record InventoryItemOverlaysS2C(List<InventoryItemOverlay> overlays)
            implements ProtocolMessage {
        public InventoryItemOverlaysS2C {
            overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
        }

        @Override
        public MessageType type() {
            return MessageType.INVENTORY_ITEM_OVERLAYS_S2C;
        }
    }

    static List<InventoryItemOverlay> mutableInventoryItemOverlayListWithCapacity(int size) {
        return new ArrayList<>(size);
    }

    static List<Integer> mutableIntegerListWithCapacity(int size) {
        return new ArrayList<>(size);
    }
}
