package me.landon.client.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.landon.companion.protocol.ProtocolMessage;
import org.junit.jupiter.api.Test;

class ConnectionSessionStateTest {
    @Test
    void replacesEntireOverlaySnapshot() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(
                        new ProtocolMessage.InventoryItemOverlay(0, 1, "12K"),
                        new ProtocolMessage.InventoryItemOverlay(9, 2, "4.5M")));

        sessionState.replaceInventoryItemOverlays(
                List.of(new ProtocolMessage.InventoryItemOverlay(1, 1, "22K")));

        assertNull(sessionState.getInventoryItemOverlay(0));
        assertNull(sessionState.getInventoryItemOverlay(9));
        ConnectionSessionState.ItemOverlayEntry entry = sessionState.getInventoryItemOverlay(1);
        assertEquals(1, entry.overlayType());
        assertEquals("22K", entry.displayText());
    }

    @Test
    void emptySnapshotClearsOverlayMap() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(new ProtocolMessage.InventoryItemOverlay(0, 1, "8K")));
        assertTrue(sessionState.inventoryItemOverlaysSnapshot().containsKey(0));

        sessionState.replaceInventoryItemOverlays(List.of());

        assertTrue(sessionState.inventoryItemOverlaysSnapshot().isEmpty());
    }

    @Test
    void normalizesNmsHotbarSlots() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(new ProtocolMessage.InventoryItemOverlay(36, 1, "9K")));

        ConnectionSessionState.ItemOverlayEntry entry = sessionState.getInventoryItemOverlay(0);
        assertNotNull(entry);
        assertEquals("9K", entry.displayText());
    }

    @Test
    void resetClearsOverlaysAndSupportFlag() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.setInventoryItemOverlaysSupported(true);
        sessionState.replaceInventoryItemOverlays(
                List.of(new ProtocolMessage.InventoryItemOverlay(0, 1, "8K")));

        sessionState.reset();

        assertTrue(sessionState.inventoryItemOverlaysSnapshot().isEmpty());
        assertFalse(sessionState.inventoryItemOverlaysSupported());
    }
}
