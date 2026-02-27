package me.landon.client.feature;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import me.landon.companion.protocol.ProtocolConstants;

public final class ClientFeatures {
    public static final String INVENTORY_ITEM_OVERLAYS_ID = "inventory_item_overlays";
    public static final ClientFeatureDefinition INVENTORY_ITEM_OVERLAYS =
            new ClientFeatureDefinition(
                    INVENTORY_ITEM_OVERLAYS_ID,
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.name",
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.icon",
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS));

    private static final List<ClientFeatureDefinition> ALL = List.of(INVENTORY_ITEM_OVERLAYS);

    private ClientFeatures() {}

    public static List<ClientFeatureDefinition> all() {
        return ALL;
    }

    public static Optional<ClientFeatureDefinition> findById(String id) {
        return ALL.stream().filter(feature -> feature.id().equals(id)).findFirst();
    }
}
