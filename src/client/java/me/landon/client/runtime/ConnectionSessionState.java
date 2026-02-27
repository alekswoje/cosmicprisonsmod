package me.landon.client.runtime;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;
import me.landon.companion.session.ConnectionGateState;

/**
 * Mutable per-connection client state populated from server companion payloads.
 *
 * <p>This class is reset on disconnect and owns transient runtime data such as widget snapshots,
 * item overlays, marker ids, and handshake tracking.
 */
public final class ConnectionSessionState {
    /** Lightweight overlay snapshot for a single inventory slot. */
    public record ItemOverlayEntry(int overlayType, String displayText) {}

    /** Snapshot of one HUD widget payload as received from the server. */
    public record HudWidgetEntry(List<String> lines, int ttlSeconds, long receivedAtEpochMillis) {
        public HudWidgetEntry {
            lines = List.copyOf(lines);
        }
    }

    private final ConnectionGateState gateState = new ConnectionGateState();
    private final Map<Integer, ItemOverlayEntry> inventoryItemOverlays = new LinkedHashMap<>();
    private final Map<String, HudWidgetEntry> hudWidgets = new LinkedHashMap<>();
    private final IntSet peacefulMiningPassThroughIds = new IntOpenHashSet();
    private final IntSet gangPingBeaconIds = new IntOpenHashSet();
    private final IntSet trucePingBeaconIds = new IntOpenHashSet();

    private String serverId = "";
    private String serverPluginVersion = "";
    private int serverFeatureFlags;

    private boolean helloSent;
    private boolean malformedPacketLogged;
    private boolean hudWidgetsSupported;
    private boolean inventoryItemOverlaysSupported;

    /** Returns the handshake gate state used to authorize companion message processing. */
    public ConnectionGateState gateState() {
        return gateState;
    }

    /** Clears all handshake/session fields and payload snapshots for a new connection. */
    public void reset() {
        gateState.reset();
        clearInventoryItemOverlays();
        clearHudWidgets();
        clearPeacefulMiningPassThroughIds();
        clearGangPingBeaconIds();
        clearTrucePingBeaconIds();
        serverId = "";
        serverPluginVersion = "";
        serverFeatureFlags = 0;
        helloSent = false;
        malformedPacketLogged = false;
        hudWidgetsSupported = false;
        inventoryItemOverlaysSupported = false;
    }

    /** Stores authoritative server hello metadata from the validated handshake frame. */
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

    public void setHudWidgetsSupported(boolean hudWidgetsSupported) {
        this.hudWidgetsSupported = hudWidgetsSupported;
    }

    public boolean hudWidgetsSupported() {
        return hudWidgetsSupported;
    }

    /**
     * Replaces the full inventory overlay snapshot with normalized slot mappings.
     *
     * <p>Invalid slots are ignored.
     */
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

    /**
     * Replaces the full HUD widget snapshot.
     *
     * <p>Null widget ids and over-limit lines are discarded to preserve protocol bounds.
     */
    public void replaceHudWidgets(List<ProtocolMessage.HudWidget> widgets) {
        hudWidgets.clear();
        long receivedAt = System.currentTimeMillis();

        for (ProtocolMessage.HudWidget widget : widgets) {
            if (widget == null) {
                continue;
            }

            String widgetId = normalizeWidgetId(widget.widgetId());
            if (widgetId.isEmpty()) {
                continue;
            }

            List<String> lines = new ArrayList<>(widget.lines().size());
            for (String line : widget.lines()) {
                lines.add(line == null ? "" : line);
                if (lines.size() >= ProtocolConstants.MAX_WIDGET_LINES) {
                    break;
                }
            }

            int ttlSeconds = Math.max(0, widget.ttlSeconds());
            hudWidgets.put(widgetId, new HudWidgetEntry(lines, ttlSeconds, receivedAt));
        }
    }

    public void clearHudWidgets() {
        hudWidgets.clear();
    }

    public HudWidgetEntry getHudWidget(String widgetId) {
        if (widgetId == null) {
            return null;
        }

        return hudWidgets.get(normalizeWidgetId(widgetId));
    }

    public Map<String, HudWidgetEntry> hudWidgetsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(hudWidgets));
    }

    /** Applies an add/remove delta for peaceful-mining pass-through entity markers. */
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

    /** Applies an add/remove delta for gang ping beacon entities. */
    public void applyGangPingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        applyEntityIdDelta(gangPingBeaconIds, addEntityIds, removeEntityIds);
    }

    /** Applies an add/remove delta for truce ping beacon entities. */
    public void applyTrucePingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        applyEntityIdDelta(trucePingBeaconIds, addEntityIds, removeEntityIds);
    }

    public IntSet gangPingBeaconIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(gangPingBeaconIds));
    }

    public IntSet trucePingBeaconIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(trucePingBeaconIds));
    }

    public void clearGangPingBeaconIds() {
        gangPingBeaconIds.clear();
    }

    public void clearTrucePingBeaconIds() {
        trucePingBeaconIds.clear();
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

    /**
     * Normalizes slot indexes into player storage space (0-35).
     *
     * <p>Supports legacy server payloads that send NMS hotbar slots (36-44).
     */
    private static int normalizePlayerStorageSlot(int rawSlot) {
        if (rawSlot >= PLAYER_STORAGE_MIN_SLOT && rawSlot <= PLAYER_STORAGE_MAX_SLOT) {
            return rawSlot;
        }

        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }

        return -1;
    }

    private static String normalizeWidgetId(String widgetId) {
        if (widgetId == null) {
            return "";
        }

        return widgetId.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyEntityIdDelta(
            IntSet target, List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        for (int entityId : addEntityIds) {
            if (entityId < 0) {
                continue;
            }

            target.add(entityId);
        }

        for (int entityId : removeEntityIds) {
            if (entityId < 0) {
                continue;
            }

            target.remove(entityId);
        }
    }

    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
}
