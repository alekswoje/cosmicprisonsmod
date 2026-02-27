package me.landon.client.runtime;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.companion.protocol.ProtocolMessage;
import me.landon.companion.session.ConnectionGateState;

public final class ConnectionSessionState {
    public record ItemOverlayEntry(int overlayType, String displayText) {}

    private final ConnectionGateState gateState = new ConnectionGateState();
    private final Map<Integer, ItemOverlayEntry> inventoryItemOverlays = new LinkedHashMap<>();
    private final IntSet peacefulMiningPassThroughIds = new IntOpenHashSet();

    private String serverId = "";
    private String serverPluginVersion = "";
    private int serverFeatureFlags;

    private boolean helloSent;
    private boolean malformedPacketLogged;
    private boolean inventoryItemOverlaysSupported;

    public ConnectionGateState gateState() {
        return gateState;
    }

    public void reset() {
        gateState.reset();
        clearInventoryItemOverlays();
        clearPeacefulMiningPassThroughIds();
        serverId = "";
        serverPluginVersion = "";
        serverFeatureFlags = 0;
        helloSent = false;
        malformedPacketLogged = false;
        inventoryItemOverlaysSupported = false;
    }

    public void setServerHello(ProtocolMessage.ServerHelloS2C hello) {
        serverId = hello.serverId();
        serverPluginVersion = hello.serverPluginVersion();
        serverFeatureFlags = hello.serverFeatureFlagsBitset();
    }

    public String serverId() {
        return serverId;
    }

    public String serverPluginVersion() {
        return serverPluginVersion;
    }

    public int serverFeatureFlags() {
        return serverFeatureFlags;
    }

    public void setInventoryItemOverlaysSupported(boolean inventoryItemOverlaysSupported) {
        this.inventoryItemOverlaysSupported = inventoryItemOverlaysSupported;
    }

    public boolean inventoryItemOverlaysSupported() {
        return inventoryItemOverlaysSupported;
    }

    public void replaceInventoryItemOverlays(List<ProtocolMessage.InventoryItemOverlay> overlays) {
        inventoryItemOverlays.clear();

        for (ProtocolMessage.InventoryItemOverlay overlay : overlays) {
            int normalizedSlot = normalizePlayerStorageSlot(overlay.slot());

            if (normalizedSlot < PLAYER_STORAGE_MIN_SLOT
                    || normalizedSlot > PLAYER_STORAGE_MAX_SLOT) {
                continue;
            }

            inventoryItemOverlays.put(
                    normalizedSlot,
                    new ItemOverlayEntry(overlay.overlayType(), overlay.displayText()));
        }
    }

    public void clearInventoryItemOverlays() {
        inventoryItemOverlays.clear();
    }

    public void applyPeacefulMiningPassThroughDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        for (int entityId : addEntityIds) {
            if (entityId < 0) {
                continue;
            }

            peacefulMiningPassThroughIds.add(entityId);
        }

        for (int entityId : removeEntityIds) {
            if (entityId < 0) {
                continue;
            }

            peacefulMiningPassThroughIds.remove(entityId);
        }
    }

    public boolean isPeacefulMiningPassThroughEntity(int entityId) {
        return entityId >= 0 && peacefulMiningPassThroughIds.contains(entityId);
    }

    public IntSet peacefulMiningPassThroughIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(peacefulMiningPassThroughIds));
    }

    public void clearPeacefulMiningPassThroughIds() {
        peacefulMiningPassThroughIds.clear();
    }

    public ItemOverlayEntry getInventoryItemOverlay(int slot) {
        return inventoryItemOverlays.get(slot);
    }

    public Map<Integer, ItemOverlayEntry> inventoryItemOverlaysSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inventoryItemOverlays));
    }

    public boolean helloSent() {
        return helloSent;
    }

    public void markHelloSent() {
        this.helloSent = true;
    }

    public boolean malformedPacketLogged() {
        return malformedPacketLogged;
    }

    public void markMalformedPacketLogged() {
        this.malformedPacketLogged = true;
    }

    private static int normalizePlayerStorageSlot(int rawSlot) {
        if (rawSlot >= PLAYER_STORAGE_MIN_SLOT && rawSlot <= PLAYER_STORAGE_MAX_SLOT) {
            return rawSlot;
        }

        // Compatibility path: some server implementations may send NMS hotbar slots 36-44.
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }

        return -1;
    }

    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
}
