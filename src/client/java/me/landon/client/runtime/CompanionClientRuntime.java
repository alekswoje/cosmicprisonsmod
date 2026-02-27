package me.landon.client.runtime;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import me.landon.CosmicPrisonsMod;
import me.landon.client.feature.ClientFeatureDefinition;
import me.landon.client.feature.ClientFeatures;
import me.landon.client.screen.FeatureSettingsScreen;
import me.landon.companion.attestation.BuildAttestation;
import me.landon.companion.attestation.BuildAttestationLoader;
import me.landon.companion.attestation.SignatureVerifier;
import me.landon.companion.config.CompanionConfig;
import me.landon.companion.config.CompanionConfigManager;
import me.landon.companion.network.CompanionRawPayload;
import me.landon.companion.protocol.BinaryDecodingException;
import me.landon.companion.protocol.ProtocolCodec;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;
import me.landon.companion.session.ConnectionGateState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionClientRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionClientRuntime.class);
    private static final CompanionClientRuntime INSTANCE = new CompanionClientRuntime();
    private static final int CLIENT_CAPABILITIES_BITSET = 127;
    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
    private static final int KNOWN_OVERLAY_STACK_LIMIT = 64;
    private static final int PENDING_CURSOR_OVERLAY_FRAMES = 4;
    private static final int HUD_PANEL_MIN_WIDTH = 126;
    private static final int HUD_PANEL_MAX_WIDTH = 214;
    private static final int HUD_PANEL_HEADER_HEIGHT = 12;
    private static final int HUD_PANEL_HORIZONTAL_PADDING = 5;
    private static final int HUD_PANEL_VERTICAL_PADDING = 4;
    private static final int HUD_PANEL_EDGE_INSET = 3;
    private static final int HUD_COOLDOWN_CIRCLE_RADIUS = 4;
    private static final KeyBinding.Category PING_KEYBIND_CATEGORY =
            KeyBinding.Category.create(Identifier.of(CosmicPrisonsMod.MOD_ID, "pings"));
    private static final int PING_PARTICLE_COLUMN_SEGMENTS = 6;

    private final ProtocolCodec protocolCodec = new ProtocolCodec();
    private final SignatureVerifier signatureVerifier = new SignatureVerifier();
    private final ConnectionSessionState session = new ConnectionSessionState();
    private final CompanionConfigManager configManager = new CompanionConfigManager();
    private final BuildAttestationLoader buildAttestationLoader = new BuildAttestationLoader();
    private final List<KnownOverlayStack> knownOverlayStacks = new ArrayList<>();
    private KeyBinding gangPingKeyBinding;
    private KeyBinding trucePingKeyBinding;

    private CompanionConfig config;
    private BuildAttestation buildAttestation;
    private int helloRetryTicks;
    private boolean helloUnavailableLogged;
    private ConnectionSessionState.ItemOverlayEntry activeCursorOverlayEntry;
    private ItemStack activeCursorOverlayStack = ItemStack.EMPTY;
    private int activeCursorOverlaySlot = -1;
    private int pendingCursorOverlayFrames;
    private volatile int activePeacefulMiningTargetEntityId = -1;
    private boolean initialized;

    public static CompanionClientRuntime getInstance() {
        return INSTANCE;
    }

    private CompanionClientRuntime() {}

    public synchronized void initializeClient() {
        if (initialized) {
            return;
        }

        config = configManager.load();
        buildAttestation = buildAttestationLoader.load(resolveModVersion());
        ensureFeatureDefaultsPersisted();
        initializePingKeybinds();

        ClientPlayNetworking.registerGlobalReceiver(
                CompanionRawPayload.ID, this::onPayloadReceived);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        ScreenEvents.AFTER_INIT.register(this::onScreenAfterInit);

        HudRenderCallback.EVENT.register(this::renderHud);

        initialized = true;
    }

    public synchronized boolean isPayloadFallbackEnabled() {
        return getConfig().enablePayloadCodecFallback;
    }

    public synchronized List<ClientFeatureDefinition> getAvailableFeatures() {
        return ClientFeatures.all();
    }

    public synchronized boolean isFeatureEnabled(String featureId) {
        return getFeatureToggleState(featureId);
    }

    public synchronized void setFeatureEnabled(String featureId, boolean enabled) {
        if (ClientFeatures.findById(featureId).isEmpty()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.featureToggles.put(featureId, enabled);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized boolean isFeatureSupportedByServer(String featureId) {
        return ClientFeatures.findById(featureId)
                .map(this::isFeatureSupportedByServer)
                .orElse(false);
    }

    public synchronized Map<String, Boolean> getHudEventVisibilitySnapshot() {
        return new LinkedHashMap<>(getConfig().hudEventVisibility);
    }

    public synchronized void setHudEventVisibility(String eventKey, boolean visible) {
        if (eventKey == null || eventKey.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudEventVisibility.put(
                eventKey.trim().toLowerCase(java.util.Locale.ROOT), visible);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized Map<String, CompanionConfig.HudWidgetPosition> getHudWidgetPositions() {
        Map<String, CompanionConfig.HudWidgetPosition> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, CompanionConfig.HudWidgetPosition> entry :
                getConfig().hudWidgetPositions.entrySet()) {
            CompanionConfig.HudWidgetPosition position = entry.getValue();
            snapshot.put(
                    entry.getKey(), new CompanionConfig.HudWidgetPosition(position.x, position.y));
        }
        return snapshot;
    }

    public synchronized CompanionConfig.HudWidgetPosition getHudWidgetPosition(String widgetId) {
        CompanionConfig.HudWidgetPosition configured =
                getConfig().hudWidgetPositions.get(normalizeWidgetId(widgetId));
        if (configured == null) {
            CompanionConfig fallback = CompanionConfig.defaults();
            CompanionConfig.HudWidgetPosition fallbackPosition =
                    fallback.hudWidgetPositions.get(normalizeWidgetId(widgetId));
            if (fallbackPosition != null) {
                return new CompanionConfig.HudWidgetPosition(
                        fallbackPosition.x, fallbackPosition.y);
            }
            return new CompanionConfig.HudWidgetPosition(0.5D, 0.5D);
        }
        return new CompanionConfig.HudWidgetPosition(configured.x, configured.y);
    }

    public synchronized void setHudWidgetPosition(
            String widgetId, double normalizedX, double normalizedY) {
        if (widgetId == null || widgetId.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetPositions.put(
                normalizeWidgetId(widgetId),
                new CompanionConfig.HudWidgetPosition(normalizedX, normalizedY));
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized void resetHudLayout() {
        CompanionConfig defaults = CompanionConfig.defaults();
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetPositions = new LinkedHashMap<>(defaults.hudWidgetPositions);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized Text gangPingKeybindLabel() {
        if (gangPingKeyBinding == null) {
            initializePingKeybinds();
        }

        return gangPingKeyBinding.getBoundKeyLocalizedText();
    }

    public synchronized Text trucePingKeybindLabel() {
        if (trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        return trucePingKeyBinding.getBoundKeyLocalizedText();
    }

    public synchronized void bindGangPingKey(KeyInput keyInput) {
        if (gangPingKeyBinding == null) {
            initializePingKeybinds();
        }

        bindKey(gangPingKeyBinding, keyInput);
    }

    public synchronized void bindTrucePingKey(KeyInput keyInput) {
        if (trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        bindKey(trucePingKeyBinding, keyInput);
    }

    public synchronized void resetPingKeybindsToDefault() {
        if (gangPingKeyBinding == null || trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        gangPingKeyBinding.setBoundKey(gangPingKeyBinding.getDefaultKey());
        trucePingKeyBinding.setBoundKey(trucePingKeyBinding.getDefaultKey());
        KeyBinding.updateKeysByCode();
        persistGameOptions();
    }

    public void onCrosshairTargetUpdated(MinecraftClient client, float tickDelta) {
        synchronized (this) {
            if (!shouldApplyPeacefulMiningPassThrough(client)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            HitResult currentTarget = client.crosshairTarget;

            if (!(currentTarget instanceof EntityHitResult entityHitResult)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            int entityId = entityHitResult.getEntity().getId();

            if (!session.isPeacefulMiningPassThroughEntity(entityId)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            Entity cameraEntity = client.getCameraEntity();

            if (cameraEntity == null || client.player == null) {
                clearActivePeacefulMiningTarget();
                return;
            }

            HitResult blockOnlyHit =
                    cameraEntity.raycast(
                            client.player.getBlockInteractionRange(), tickDelta, false);

            if (!(blockOnlyHit instanceof BlockHitResult blockHitResult)
                    || blockHitResult.getType() != HitResult.Type.BLOCK) {
                clearActivePeacefulMiningTarget();
                return;
            }

            client.crosshairTarget = blockHitResult;
            client.targetedEntity = null;
            activePeacefulMiningTargetEntityId = entityId;
        }
    }

    public boolean isPeacefulMiningGhostedEntity(int entityId) {
        return entityId >= 0 && entityId == activePeacefulMiningTargetEntityId;
    }

    public void renderHandledScreenSlotOverlay(DrawContext drawContext, Slot slot) {
        if (slot == null || !slot.hasStack()) {
            return;
        }

        ConnectionSessionState.ItemOverlayEntry overlayEntry;

        synchronized (this) {
            if (!shouldRenderInventoryItemOverlays()) {
                return;
            }

            seedKnownOverlayStacksFromPlayerInventory(
                    MinecraftClient.getInstance(), session.inventoryItemOverlaysSnapshot());
            overlayEntry = resolveSlotOverlayEntry(slot);
        }

        if (overlayEntry == null) {
            return;
        }

        drawOverlayText(drawContext, slot.x, slot.y, overlayEntry);
    }

    public void renderHandledScreenCursorOverlay(
            DrawContext drawContext,
            ScreenHandler handler,
            Slot lastClickedSlot,
            int mouseX,
            int mouseY) {
        if (drawContext == null || handler == null) {
            return;
        }

        ItemStack cursorStack = handler.getCursorStack();

        ConnectionSessionState.ItemOverlayEntry overlayEntry;

        synchronized (this) {
            if (!shouldRenderInventoryItemOverlays()) {
                clearOverlayRenderCaches();
                return;
            }

            seedKnownOverlayStacksFromPlayerInventory(
                    MinecraftClient.getInstance(), session.inventoryItemOverlaysSnapshot());

            if (cursorStack.isEmpty()) {
                if (activeCursorOverlayEntry != null
                        && isMouseButtonHeld(MinecraftClient.getInstance())) {
                    overlayEntry = activeCursorOverlayEntry;
                } else if (pendingCursorOverlayFrames > 0 && activeCursorOverlayEntry != null) {
                    pendingCursorOverlayFrames--;
                    overlayEntry = activeCursorOverlayEntry;
                } else {
                    clearActiveCursorOverlay();
                    return;
                }
            } else {
                overlayEntry = resolveCursorOverlayEntry(handler, lastClickedSlot, cursorStack);
                boolean usedPendingFallback = false;

                if (overlayEntry == null) {
                    if (pendingCursorOverlayFrames > 0 && activeCursorOverlayEntry != null) {
                        pendingCursorOverlayFrames--;
                        overlayEntry = activeCursorOverlayEntry;
                        usedPendingFallback = true;
                    } else {
                        return;
                    }
                }

                if (!usedPendingFallback) {
                    pendingCursorOverlayFrames = 0;
                }
            }
        }

        int slotX = mouseX - 8;
        int slotY = mouseY - 8;
        drawOverlayText(drawContext, slotX, slotY, overlayEntry);
    }

    public synchronized void rememberHandledScreenSlotClickOverlay(Slot slot) {
        if (slot == null || !shouldRenderInventoryItemOverlays() || !slot.hasStack()) {
            return;
        }

        int slotIndex = playerStorageSlotIndex(slot);

        if (slotIndex < PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry knownOverlayEntry =
                    findKnownOverlayForStack(slot.getStack());

            if (knownOverlayEntry == null) {
                clearActiveCursorOverlay();
                return;
            }

            cacheActiveCursorOverlay(-1, knownOverlayEntry, slot.getStack());
            pendingCursorOverlayFrames = PENDING_CURSOR_OVERLAY_FRAMES;
            return;
        }

        ConnectionSessionState.ItemOverlayEntry overlayEntry =
                session.getInventoryItemOverlay(slotIndex);

        if (overlayEntry == null) {
            clearActiveCursorOverlay();
            return;
        }

        cacheActiveCursorOverlay(slotIndex, overlayEntry, slot.getStack());
        pendingCursorOverlayFrames = PENDING_CURSOR_OVERLAY_FRAMES;
    }

    private synchronized void onJoin(MinecraftClient client) {
        session.reset();
        clearOverlayRenderCaches();
        clearActivePeacefulMiningTarget();
        helloRetryTicks = ProtocolConstants.CLIENT_HELLO_RETRY_TICKS;
        helloUnavailableLogged = false;
        attemptSendClientHello(client);
    }

    private synchronized void onDisconnect() {
        session.reset();
        clearOverlayRenderCaches();
        clearActivePeacefulMiningTarget();
        helloRetryTicks = 0;
        helloUnavailableLogged = false;
    }

    private synchronized void onEndTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        processPingKeybinds(client);
        emitPingBeaconParticles(client);

        if (!session.helloSent() && helloRetryTicks > 0) {
            helloRetryTicks--;
            attemptSendClientHello(client);
        }
    }

    private synchronized void onWorldChange(MinecraftClient client, ClientWorld world) {
        if (world == null) {
            session.clearInventoryItemOverlays();
            session.clearHudWidgets();
            session.clearPeacefulMiningPassThroughIds();
            session.clearGangPingBeaconIds();
            session.clearTrucePingBeaconIds();
            clearOverlayRenderCaches();
            clearActivePeacefulMiningTarget();
        }
    }

    private synchronized void onScreenAfterInit(
            MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof GameMenuScreen)) {
            return;
        }

        int buttonWidth = 98;
        int buttonHeight = 20;
        int buttonX = screen.width - buttonWidth - 8;
        int buttonY = 8;
        Screens.getButtons(screen)
                .add(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.settings.button.open"),
                                        button -> client.setScreen(new FeatureSettingsScreen(this)))
                                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                                .build());
    }

    private void onPayloadReceived(
            CompanionRawPayload payload, ClientPlayNetworking.Context context) {
        byte[] payloadBytes = payload.payloadBytes();
        MinecraftClient client = context.client();
        client.execute(() -> onPayloadReceivedOnClient(payloadBytes, client));
    }

    private synchronized void onPayloadReceivedOnClient(
            byte[] payloadBytes, MinecraftClient client) {
        ProtocolCodec.DecodedFrame frame;

        try {
            frame = protocolCodec.decode(payloadBytes);
        } catch (BinaryDecodingException ex) {
            logMalformedOncePerConnection(ex);
            return;
        }

        ProtocolMessage message = frame.message();

        if (!session.gateState().shouldProcessIncoming(message)) {
            return;
        }

        switch (message) {
            case ProtocolMessage.ServerHelloS2C serverHello ->
                    handleServerHello(frame.protocolVersion(), serverHello, client);
            case ProtocolMessage.HudWidgetStateS2C hudWidgetState ->
                    handleHudWidgetState(hudWidgetState);
            case ProtocolMessage.EntityMarkerDeltaS2C markerDelta ->
                    handleEntityMarkerDelta(markerDelta);
            case ProtocolMessage.InventoryItemOverlaysS2C overlays ->
                    handleInventoryItemOverlays(overlays);
            default -> {}
        }
    }

    private void handleServerHello(
            int protocolVersion,
            ProtocolMessage.ServerHelloS2C serverHello,
            MinecraftClient client) {
        CompanionConfig currentConfig = getConfig();
        ConnectionGateState.EnableResult result =
                session.gateState()
                        .tryEnable(
                                protocolVersion,
                                serverHello,
                                currentConfig,
                                signatureVerifier::verifyServerHello);

        if (!result.enabled()) {
            LOGGER.debug("Ignored ServerHello due to {}", result);

            if (result == ConnectionGateState.EnableResult.SERVER_NOT_ALLOWED) {
                sendClientMessage(
                        client,
                        Text.translatable(
                                "text.cosmicprisonsmod.server_not_allowed",
                                serverHello.serverId()));
            }

            return;
        }

        session.setServerHello(serverHello);

        boolean supportsHudWidgets =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS)
                        != 0;
        boolean supportsInventoryItemOverlays =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS)
                        != 0;
        boolean supportsEntityMarkers =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_ENTITY_MARKERS)
                        != 0;
        boolean supportsGangTrucePings =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.FEATURE_GANG_TRUCE_PINGS)
                        != 0;
        session.setHudWidgetsSupported(supportsHudWidgets);
        session.setInventoryItemOverlaysSupported(supportsInventoryItemOverlays);

        if (!supportsHudWidgets) {
            session.clearHudWidgets();
        }

        if (!supportsInventoryItemOverlays) {
            session.clearInventoryItemOverlays();
            clearOverlayRenderCaches();
        }

        if (!supportsEntityMarkers) {
            session.clearPeacefulMiningPassThroughIds();
            clearActivePeacefulMiningTarget();
        }

        if (!supportsGangTrucePings) {
            session.clearGangPingBeaconIds();
            session.clearTrucePingBeaconIds();
        }
    }

    private void handleInventoryItemOverlays(ProtocolMessage.InventoryItemOverlaysS2C overlays) {
        if (!session.inventoryItemOverlaysSupported()) {
            session.setInventoryItemOverlaysSupported(true);
        }

        session.replaceInventoryItemOverlays(overlays.overlays());
        cacheKnownOverlayStacksFromCurrentSnapshot();
    }

    private void handleHudWidgetState(ProtocolMessage.HudWidgetStateS2C hudWidgetState) {
        if (!session.hudWidgetsSupported()) {
            session.setHudWidgetsSupported(true);
        }

        session.replaceHudWidgets(hudWidgetState.widgets());
    }

    private void handleEntityMarkerDelta(ProtocolMessage.EntityMarkerDeltaS2C markerDelta) {
        switch (markerDelta.markerType()) {
            case ProtocolConstants.MARKER_TYPE_PEACEFUL_MINING_PASS_THROUGH ->
                    session.applyPeacefulMiningPassThroughDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            case ProtocolConstants.MARKER_TYPE_GANG_PING_BEACON ->
                    session.applyGangPingBeaconDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            case ProtocolConstants.MARKER_TYPE_TRUCE_PING_BEACON ->
                    session.applyTrucePingBeaconDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            default -> {}
        }
    }

    private ConnectionSessionState.ItemOverlayEntry resolveSlotOverlayEntry(Slot slot) {
        int slotIndex = playerStorageSlotIndex(slot);

        if (slotIndex >= PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry directOverlayEntry =
                    session.getInventoryItemOverlay(slotIndex);

            if (directOverlayEntry != null) {
                rememberKnownOverlayStack(slot.getStack(), directOverlayEntry);
            }

            return directOverlayEntry;
        }

        return findKnownOverlayForStack(slot.getStack());
    }

    private ConnectionSessionState.ItemOverlayEntry resolveCursorOverlayEntry(
            ScreenHandler handler, Slot lastClickedSlot, ItemStack cursorStack) {
        if (activeCursorOverlayEntry != null && !activeCursorOverlayStack.isEmpty()) {
            if (activeCursorOverlaySlot >= PLAYER_STORAGE_MIN_SLOT) {
                ConnectionSessionState.ItemOverlayEntry refreshedOverlayEntry =
                        session.getInventoryItemOverlay(activeCursorOverlaySlot);

                if (refreshedOverlayEntry != null) {
                    activeCursorOverlayEntry = refreshedOverlayEntry;
                }
            }

            activeCursorOverlayStack = cursorStack.copy();
            rememberKnownOverlayStack(cursorStack, activeCursorOverlayEntry);
            return activeCursorOverlayEntry;
        }

        int clickedSlotIndex = playerStorageSlotIndex(lastClickedSlot);

        if (clickedSlotIndex >= PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry clickedSlotOverlayEntry =
                    session.getInventoryItemOverlay(clickedSlotIndex);

            if (clickedSlotOverlayEntry != null) {
                cacheActiveCursorOverlay(clickedSlotIndex, clickedSlotOverlayEntry, cursorStack);
                return clickedSlotOverlayEntry;
            }
        }

        for (Slot slot : handler.slots) {
            int slotIndex = playerStorageSlotIndex(slot);

            if (slotIndex < PLAYER_STORAGE_MIN_SLOT || !slot.hasStack()) {
                continue;
            }

            if (!ItemStack.areItemsAndComponentsEqual(slot.getStack(), cursorStack)) {
                continue;
            }

            ConnectionSessionState.ItemOverlayEntry matchedOverlayEntry =
                    session.getInventoryItemOverlay(slotIndex);

            if (matchedOverlayEntry == null) {
                continue;
            }

            cacheActiveCursorOverlay(slotIndex, matchedOverlayEntry, cursorStack);
            return matchedOverlayEntry;
        }

        ConnectionSessionState.ItemOverlayEntry knownOverlayEntry =
                findKnownOverlayForStack(cursorStack);

        if (knownOverlayEntry != null) {
            cacheActiveCursorOverlay(-1, knownOverlayEntry, cursorStack);
            return knownOverlayEntry;
        }

        return null;
    }

    private static int playerStorageSlotIndex(Slot slot) {
        if (slot == null || !(slot.inventory instanceof PlayerInventory)) {
            return -1;
        }

        int slotIndex = slot.getIndex();

        if (slotIndex < PLAYER_STORAGE_MIN_SLOT || slotIndex > PLAYER_STORAGE_MAX_SLOT) {
            return -1;
        }

        return slotIndex;
    }

    private void cacheKnownOverlayStacksFromCurrentSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) {
            return;
        }

        seedKnownOverlayStacksFromPlayerInventory(client, session.inventoryItemOverlaysSnapshot());
    }

    private void seedKnownOverlayStacksFromPlayerInventory(
            MinecraftClient client,
            Map<Integer, ConnectionSessionState.ItemOverlayEntry> overlaySnapshot) {
        if (client.player == null || overlaySnapshot.isEmpty()) {
            return;
        }

        PlayerInventory playerInventory = client.player.getInventory();

        for (Map.Entry<Integer, ConnectionSessionState.ItemOverlayEntry> entry :
                overlaySnapshot.entrySet()) {
            int slotIndex = entry.getKey();

            if (slotIndex < PLAYER_STORAGE_MIN_SLOT || slotIndex > PLAYER_STORAGE_MAX_SLOT) {
                continue;
            }

            ItemStack stack = playerInventory.getStack(slotIndex);

            if (stack.isEmpty()) {
                continue;
            }

            rememberKnownOverlayStack(stack, entry.getValue());
        }
    }

    private void cacheActiveCursorOverlay(
            int slotIndex,
            ConnectionSessionState.ItemOverlayEntry overlayEntry,
            ItemStack cursorStack) {
        activeCursorOverlayEntry = overlayEntry;
        activeCursorOverlayStack = cursorStack.copy();
        activeCursorOverlaySlot = slotIndex;
        rememberKnownOverlayStack(cursorStack, overlayEntry);
    }

    private void clearActiveCursorOverlay() {
        activeCursorOverlayEntry = null;
        activeCursorOverlayStack = ItemStack.EMPTY;
        activeCursorOverlaySlot = -1;
        pendingCursorOverlayFrames = 0;
    }

    private void clearActivePeacefulMiningTarget() {
        activePeacefulMiningTargetEntityId = -1;
    }

    private void clearOverlayRenderCaches() {
        clearActiveCursorOverlay();
        knownOverlayStacks.clear();
    }

    private void rememberKnownOverlayStack(
            ItemStack stack, ConnectionSessionState.ItemOverlayEntry overlayEntry) {
        if (stack.isEmpty()) {
            return;
        }

        for (int i = 0; i < knownOverlayStacks.size(); i++) {
            KnownOverlayStack knownOverlayStack = knownOverlayStacks.get(i);

            if (!ItemStack.areItemsAndComponentsEqual(knownOverlayStack.stack(), stack)) {
                continue;
            }

            knownOverlayStacks.set(i, new KnownOverlayStack(stack.copy(), overlayEntry));
            return;
        }

        knownOverlayStacks.add(new KnownOverlayStack(stack.copy(), overlayEntry));

        if (knownOverlayStacks.size() > KNOWN_OVERLAY_STACK_LIMIT) {
            knownOverlayStacks.remove(0);
        }
    }

    private ConnectionSessionState.ItemOverlayEntry findKnownOverlayForStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ConnectionSessionState.ItemOverlayEntry itemAndComponentMatch = null;
        String stackName = stack.getName().getString();

        for (int i = knownOverlayStacks.size() - 1; i >= 0; i--) {
            KnownOverlayStack knownOverlayStack = knownOverlayStacks.get(i);

            if (ItemStack.areItemsAndComponentsEqual(knownOverlayStack.stack(), stack)) {
                return knownOverlayStack.overlayEntry();
            }

            if (itemAndComponentMatch != null) {
                continue;
            }

            ItemStack knownStack = knownOverlayStack.stack();

            if (knownStack.getItem() != stack.getItem()) {
                continue;
            }

            if (!knownStack.getName().getString().equals(stackName)) {
                continue;
            }

            itemAndComponentMatch = knownOverlayStack.overlayEntry();
        }

        return itemAndComponentMatch;
    }

    private static boolean isMouseButtonHeld(MinecraftClient client) {
        long windowHandle = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                        == GLFW.GLFW_PRESS;
    }

    private void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        renderHotbarOverlays(drawContext);
        renderHudWidgetPanels(drawContext, false, Map.of());
    }

    public void renderHudWidgetPanelsForEditor(
            DrawContext drawContext,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        renderHudWidgetPanels(drawContext, true, positionOverrides);
    }

    public synchronized List<HudWidgetPanel> collectHudWidgetPanelsForEditor(
            int screenWidth,
            int screenHeight,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        return collectHudWidgetPanels(screenWidth, screenHeight, true, positionOverrides);
    }

    private void renderHotbarOverlays(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        if (client.player == null || textRenderer == null) {
            return;
        }

        Map<Integer, ConnectionSessionState.ItemOverlayEntry> overlaySnapshot;

        synchronized (this) {
            if (!shouldRenderInventoryItemOverlays()) {
                return;
            }

            overlaySnapshot = session.inventoryItemOverlaysSnapshot();
            seedKnownOverlayStacksFromPlayerInventory(client, overlaySnapshot);
        }

        int centerX = drawContext.getScaledWindowWidth() / 2;
        int slotY = drawContext.getScaledWindowHeight() - 19;

        for (int slot = PLAYER_STORAGE_MIN_SLOT; slot <= HOTBAR_MAX_SLOT; slot++) {
            ConnectionSessionState.ItemOverlayEntry overlayEntry = overlaySnapshot.get(slot);

            if (overlayEntry == null) {
                continue;
            }

            int slotX = centerX - 90 + (slot * 20) + 2;
            drawOverlayText(drawContext, slotX, slotY, overlayEntry);
        }
    }

    private void renderHudWidgetPanels(
            DrawContext drawContext,
            boolean editorMode,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null) {
            return;
        }

        List<HudWidgetPanel> panels =
                collectHudWidgetPanels(
                        drawContext.getScaledWindowWidth(),
                        drawContext.getScaledWindowHeight(),
                        editorMode,
                        positionOverrides);

        for (HudWidgetPanel panel : panels) {
            drawHudWidgetPanel(drawContext, textRenderer, panel, editorMode);
        }
    }

    private synchronized List<HudWidgetPanel> collectHudWidgetPanels(
            int screenWidth,
            int screenHeight,
            boolean editorMode,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null || screenWidth <= 0 || screenHeight <= 0) {
            return List.of();
        }

        CompanionConfig currentConfig = getConfig();
        Map<String, ConnectionSessionState.HudWidgetEntry> widgetSnapshot =
                session.hudWidgetsSnapshot();
        long now = System.currentTimeMillis();
        List<HudWidgetPanel> panels = new ArrayList<>();

        for (HudWidgetCatalog.WidgetDescriptor widget : HudWidgetCatalog.widgets()) {
            if (!editorMode && !shouldRenderWidget(widget.featureId())) {
                continue;
            }

            List<String> lines =
                    resolveWidgetLines(
                            widget.widgetId(),
                            widget.previewLines(),
                            widgetSnapshot,
                            currentConfig,
                            now,
                            editorMode);

            if (lines.isEmpty()) {
                continue;
            }

            int lineHeight = widgetLineHeight(widget.widgetId());
            int titleWidth =
                    textRenderer.getWidth(Text.translatable(widget.titleTranslationKey()))
                            + (HUD_PANEL_HORIZONTAL_PADDING * 2);
            int contentWidth = measureWidgetLineWidth(widget.widgetId(), lines, textRenderer);
            int desiredWidth =
                    Math.max(basePanelWidth(widget.widgetId()), Math.max(titleWidth, contentWidth));
            int clampedPanelWidth =
                    clamp(
                            desiredWidth,
                            HUD_PANEL_MIN_WIDTH,
                            Math.max(
                                    HUD_PANEL_MIN_WIDTH,
                                    Math.min(screenWidth - 4, HUD_PANEL_MAX_WIDTH)));
            int panelHeight =
                    HUD_PANEL_HEADER_HEIGHT
                            + (HUD_PANEL_VERTICAL_PADDING * 2)
                            + (lineHeight * lines.size());

            CompanionConfig.HudWidgetPosition position =
                    resolveWidgetPosition(currentConfig, widget.widgetId(), positionOverrides);
            int maxX = Math.max(0, screenWidth - clampedPanelWidth);
            int maxY = Math.max(0, screenHeight - panelHeight);
            int panelX = clamp((int) Math.round(position.x * maxX), 0, maxX);
            int panelY = clamp((int) Math.round(position.y * maxY), 0, maxY);

            panels.add(
                    new HudWidgetPanel(
                            widget.widgetId(),
                            widget.titleTranslationKey(),
                            widget.accentColor(),
                            panelX,
                            panelY,
                            clampedPanelWidth,
                            panelHeight,
                            lineHeight,
                            lines));
        }

        return panels;
    }

    private List<String> resolveWidgetLines(
            String widgetId,
            List<String> previewLines,
            Map<String, ConnectionSessionState.HudWidgetEntry> widgetSnapshot,
            CompanionConfig currentConfig,
            long now,
            boolean editorMode) {
        if (!shouldRenderHudWidgets()) {
            return editorMode ? List.copyOf(previewLines) : List.of();
        }

        ConnectionSessionState.HudWidgetEntry entry =
                widgetSnapshot.get(normalizeWidgetId(widgetId));
        if (entry == null || isHudWidgetExpired(entry, now)) {
            return editorMode ? List.copyOf(previewLines) : List.of();
        }

        List<String> lines = new ArrayList<>(entry.lines().size());
        for (String line : entry.lines()) {
            if (line != null && !line.isBlank()) {
                lines.add(line.trim());
            }
        }

        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            lines = resolveEventLines(lines, currentConfig.hudEventVisibility);
        } else if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            lines.removeIf(line -> !HudWidgetCatalog.isCooldownLineActive(line));
        }

        if (lines.size() > ProtocolConstants.MAX_WIDGET_LINES) {
            lines = new ArrayList<>(lines.subList(0, ProtocolConstants.MAX_WIDGET_LINES));
        }

        if (lines.isEmpty()) {
            return editorMode ? List.copyOf(previewLines) : List.of();
        }

        return lines;
    }

    private static List<String> resolveEventLines(
            List<String> rawLines, Map<String, Boolean> eventVisibilityConfig) {
        List<HudWidgetCatalog.ParsedLine> sorted =
                HudWidgetCatalog.sortEventsClosestFirst(rawLines, eventVisibilityConfig);
        List<String> lines = new ArrayList<>(sorted.size());

        for (HudWidgetCatalog.ParsedLine parsedLine : sorted) {
            if (parsedLine.label().isEmpty()) {
                continue;
            }

            lines.add(parsedLine.asText());
            if (lines.size() >= ProtocolConstants.MAX_WIDGET_LINES) {
                break;
            }
        }

        return lines;
    }

    private static CompanionConfig.HudWidgetPosition resolveWidgetPosition(
            CompanionConfig config,
            String widgetId,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        CompanionConfig.HudWidgetPosition override = positionOverrides.get(normalizedWidgetId);
        if (override != null) {
            return new CompanionConfig.HudWidgetPosition(override.x, override.y);
        }

        CompanionConfig.HudWidgetPosition configured =
                config.hudWidgetPositions.get(normalizedWidgetId);
        if (configured != null) {
            return new CompanionConfig.HudWidgetPosition(configured.x, configured.y);
        }

        CompanionConfig.HudWidgetPosition fallback =
                CompanionConfig.defaults().hudWidgetPositions.get(normalizedWidgetId);
        if (fallback != null) {
            return fallback;
        }

        return new CompanionConfig.HudWidgetPosition(0.5D, 0.5D);
    }

    private static boolean isHudWidgetExpired(
            ConnectionSessionState.HudWidgetEntry widget, long now) {
        int ttlSeconds = widget.ttlSeconds();
        if (ttlSeconds <= 0) {
            return false;
        }

        long expiresAt = widget.receivedAtEpochMillis() + (ttlSeconds * 1000L);
        return now > expiresAt;
    }

    private static void drawHudWidgetPanel(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            boolean editorMode) {
        int x = panel.x();
        int y = panel.y();
        int right = x + panel.width();
        int bottom = y + panel.height();
        int accent = panel.accentColor();
        int contentTop = y + HUD_PANEL_HEADER_HEIGHT + HUD_PANEL_VERTICAL_PADDING;
        int contentLeft = x + HUD_PANEL_HORIZONTAL_PADDING;
        int contentRight = right - HUD_PANEL_HORIZONTAL_PADDING;

        drawContext.fill(
                x - 1 - HUD_PANEL_EDGE_INSET,
                y - 1 - HUD_PANEL_EDGE_INSET,
                right + 1 + HUD_PANEL_EDGE_INSET,
                bottom + 1 + HUD_PANEL_EDGE_INSET,
                withAlpha(0x000000, 80));
        drawContext.fill(x, y, right, bottom, withAlpha(0x0B111A, editorMode ? 188 : 210));
        drawContext.fill(x, y, right, y + HUD_PANEL_HEADER_HEIGHT, withAlpha(0x111A2A, 228));
        drawContext.fill(
                x + 1, y + 1, right - 1, y + HUD_PANEL_HEADER_HEIGHT - 1, withAlpha(accent, 48));
        drawContext.fill(x, y, right, y + 1, withAlpha(accent, 255));
        drawContext.fill(x, bottom - 1, right, bottom, withAlpha(0x2D3A4F, 255));
        drawContext.fill(x, y, x + 1, bottom, withAlpha(0x2D3A4F, 255));
        drawContext.fill(right - 1, y, right, bottom, withAlpha(0x2D3A4F, 255));

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable(panel.titleTranslationKey()),
                x + HUD_PANEL_HORIZONTAL_PADDING,
                y + 2,
                0xFFFFFFFF);

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(panel.widgetId())) {
            drawCooldownRows(
                    drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(panel.widgetId())) {
            drawEventRows(drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(panel.widgetId())) {
            drawSatchelRows(
                    drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else {
            int lineY = contentTop;
            for (String line : panel.lines()) {
                drawContext.drawTextWithShadow(textRenderer, line, contentLeft, lineY, 0xFFE8EDF8);
                lineY += panel.lineHeight();
            }
        }

        if (editorMode) {
            drawContext.drawTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.hud.editor.drag_hint"),
                    right - 33,
                    y + 2,
                    withAlpha(0xFFFFFF, 225));
        }
    }

    private static void drawCooldownRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        long maxRemaining = 1L;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            if (remaining.isPresent() && remaining.orElse(0L) > 0L) {
                maxRemaining = Math.max(maxRemaining, remaining.orElse(0L));
            }
        }

        maxRemaining = Math.max(maxRemaining, 3600L);

        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            float progress = progressForRemaining(remaining, maxRemaining);
            int statusColor = statusColor(parsedLine.value(), remaining);

            int circleCenterX = contentLeft + HUD_COOLDOWN_CIRCLE_RADIUS + 1;
            int circleCenterY = rowY + (panel.lineHeight() / 2);
            drawCircularMeter(
                    drawContext,
                    circleCenterX,
                    circleCenterY,
                    HUD_COOLDOWN_CIRCLE_RADIUS,
                    progress,
                    statusColor,
                    withAlpha(0x41526D, 200));

            String valueText = compactStatusText(parsedLine.value(), true);
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 35, contentRight - valueWidth);
            String labelText = compactCooldownLabel(parsedLine.label());
            int labelMaxWidth = Math.max(12, valueX - (contentLeft + 13) - 3);
            labelText = textRenderer.trimToWidth(labelText, labelMaxWidth);

            drawContext.drawTextWithShadow(
                    textRenderer, labelText, contentLeft + 13, rowY + 1, 0xFFE4EDF8);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, rowY + 1, statusColor);

            rowY += panel.lineHeight();
        }
    }

    private static void drawEventRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        long maxRemaining = 1L;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            if (remaining.isPresent() && remaining.orElse(0L) > 0L) {
                maxRemaining = Math.max(maxRemaining, remaining.orElse(0L));
            }
        }
        maxRemaining = Math.max(maxRemaining, 7200L);

        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            var descriptor = HudWidgetCatalog.findEventByLabel(parsedLine.label());
            String iconTag = descriptor.map(HudWidgetCatalog.EventDescriptor::iconTag).orElse("EV");
            String eventKey = descriptor.map(HudWidgetCatalog.EventDescriptor::key).orElse("");
            int rowAccent = eventAccentColor(eventKey, panel.accentColor());
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            int statusColor = statusColor(parsedLine.value(), remaining);
            float progress = progressForRemaining(remaining, maxRemaining);

            int iconLeft = contentLeft;
            int iconWidth = 18;
            int iconTop = rowY;
            int iconBottom = rowY + panel.lineHeight() - 1;
            drawContext.fill(
                    iconLeft, iconTop, iconLeft + iconWidth, iconBottom, withAlpha(rowAccent, 76));
            drawContext.fill(
                    iconLeft,
                    iconBottom - 1,
                    iconLeft + iconWidth,
                    iconBottom,
                    withAlpha(rowAccent, 160));
            drawContext.drawCenteredTextWithShadow(
                    textRenderer, iconTag, iconLeft + (iconWidth / 2), rowY + 1, 0xFFF3F7FF);

            String labelText = compactEventLabel(parsedLine.label());
            String valueText = compactStatusText(parsedLine.value(), false);
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 64, contentRight - valueWidth);
            int labelX = iconLeft + iconWidth + 4;
            int labelMaxWidth = Math.max(12, valueX - labelX - 3);
            labelText = textRenderer.trimToWidth(labelText, labelMaxWidth);

            drawContext.drawTextWithShadow(textRenderer, labelText, labelX, rowY, 0xFFE4EDF8);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, rowY, statusColor);

            int barX = labelX;
            int barY = rowY + panel.lineHeight() - 2;
            int barWidth = Math.max(8, valueX - barX - 3);
            drawContext.fill(barX, barY, barX + barWidth, barY + 1, withAlpha(0x31415C, 190));
            int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (filled > 0) {
                drawContext.fill(barX, barY, barX + filled, barY + 1, withAlpha(rowAccent, 240));
            }

            rowY += panel.lineHeight();
        }
    }

    private static void drawSatchelRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            float progress = parseSatchelProgress(parsedLine.value());
            String valueText = compactSatchelValue(parsedLine.value());
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 48, contentRight - valueWidth);

            String label = parsedLine.label();
            String icon =
                    label.isEmpty()
                            ? "S"
                            : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            int iconLeft = contentLeft;
            int iconTop = rowY;
            int iconWidth = 10;
            int iconBottom = rowY + panel.lineHeight() - 1;
            drawContext.fill(
                    iconLeft, iconTop, iconLeft + iconWidth, iconBottom, withAlpha(0x2B5B44, 190));
            drawContext.drawCenteredTextWithShadow(
                    textRenderer, icon, iconLeft + (iconWidth / 2), rowY, 0xFFDEFFE9);

            int labelX = iconLeft + iconWidth + 3;
            int labelMaxWidth = Math.max(12, valueX - labelX - 3);
            String labelText = textRenderer.trimToWidth(label, labelMaxWidth);
            drawContext.drawTextWithShadow(textRenderer, labelText, labelX, rowY, 0xFFE5F4EA);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, rowY, 0xFF9EF0BC);

            int barX = labelX;
            int barY = rowY + panel.lineHeight() - 2;
            int barWidth = Math.max(8, valueX - barX - 3);
            drawContext.fill(barX, barY, barX + barWidth, barY + 1, withAlpha(0x33503F, 185));
            int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (filled > 0) {
                drawContext.fill(barX, barY, barX + filled, barY + 1, withAlpha(0x66D89A, 235));
            }

            rowY += panel.lineHeight();
        }
    }

    private static int widgetLineHeight(String widgetId) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            return 12;
        }

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            return 11;
        }

        return 10;
    }

    private static int basePanelWidth(String widgetId) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            return 184;
        }

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            return 166;
        }

        if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
            return 158;
        }

        return 146;
    }

    private static int measureWidgetLineWidth(
            String widgetId, List<String> lines, TextRenderer textRenderer) {
        int max = 0;
        for (String line : lines) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
                int width =
                        textRenderer.getWidth(compactEventLabel(parsedLine.label()))
                                + textRenderer.getWidth(
                                        compactStatusText(parsedLine.value(), false))
                                + 32;
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
                int width =
                        textRenderer.getWidth(compactCooldownLabel(parsedLine.label()))
                                + textRenderer.getWidth(compactStatusText(parsedLine.value(), true))
                                + 28;
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
                int width =
                        textRenderer.getWidth(parsedLine.label())
                                + textRenderer.getWidth(compactSatchelValue(parsedLine.value()))
                                + 22;
                max = Math.max(max, width);
                continue;
            }

            max = Math.max(max, textRenderer.getWidth(line));
        }

        return max + (HUD_PANEL_HORIZONTAL_PADDING * 2);
    }

    private static void drawCircularMeter(
            DrawContext drawContext,
            int centerX,
            int centerY,
            int radius,
            float progress,
            int fillColor,
            int trackColor) {
        int segments = 28;
        int filledSegments = Math.max(0, Math.min(segments, Math.round(progress * segments)));

        for (int segment = 0; segment < segments; segment++) {
            double angle = (-Math.PI / 2.0D) + ((Math.PI * 2.0D * segment) / segments);
            int pixelX = centerX + (int) Math.round(Math.cos(angle) * radius);
            int pixelY = centerY + (int) Math.round(Math.sin(angle) * radius);
            int color = segment < filledSegments ? fillColor : trackColor;
            drawContext.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
        }

        drawContext.fill(centerX, centerY, centerX + 1, centerY + 1, withAlpha(0xD8E7FF, 180));
    }

    private static float progressForRemaining(OptionalLong remaining, long referenceMaxSeconds) {
        if (remaining.isEmpty()) {
            return 0.0F;
        }

        long remainingSeconds = Math.max(0L, remaining.orElse(0L));
        if (remainingSeconds <= 0L) {
            return 1.0F;
        }

        long maxSeconds = Math.max(1L, referenceMaxSeconds);
        if (maxSeconds <= 1L) {
            return 0.0F;
        }

        double denominator = Math.log1p(maxSeconds);
        if (denominator <= 0.0D) {
            return 0.0F;
        }

        float progress =
                (float) (1.0D - (Math.log1p(Math.min(remainingSeconds, maxSeconds)) / denominator));
        return clamp01(progress);
    }

    private static int statusColor(String statusText, OptionalLong remaining) {
        String normalized = normalizeStatusToken(statusText);

        if ("now".equals(normalized) || "live".equals(normalized) || "active".equals(normalized)) {
            return 0xFF7CF2A0;
        }

        if (isUnavailableStatus(normalized)) {
            return 0xFF8A95AB;
        }

        if (remaining.isPresent() && remaining.orElse(0L) > 0L) {
            long seconds = remaining.orElse(0L);
            if (seconds <= 60L) {
                return 0xFFFF7F80;
            }
            if (seconds <= 300L) {
                return 0xFFFFB473;
            }
            if (seconds <= 1800L) {
                return 0xFFF2D67A;
            }
            return 0xFF7CC6FF;
        }

        return 0xFFD8E3F4;
    }

    private static int eventAccentColor(String eventKey, int defaultAccent) {
        return switch (eventKey) {
            case CompanionConfig.HUD_EVENT_METEORITE -> 0xFFF4AB56;
            case CompanionConfig.HUD_EVENT_METEOR -> 0xFFFF866A;
            case CompanionConfig.HUD_EVENT_ALTAR_SPAWN -> 0xFFB689FF;
            case CompanionConfig.HUD_EVENT_KOTH -> 0xFF7D99FF;
            case CompanionConfig.HUD_EVENT_CREDIT_SHOP_RESET -> 0xFF5FD3CC;
            case CompanionConfig.HUD_EVENT_JACKPOT -> 0xFFFFD56A;
            case CompanionConfig.HUD_EVENT_FLASH_SALE -> 0xFFF48AB1;
            case CompanionConfig.HUD_EVENT_MERCHANT -> 0xFF86D87A;
            case CompanionConfig.HUD_EVENT_NEXT_REBOOT -> 0xFF62B5FF;
            case CompanionConfig.HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK -> 0xFFEFA86A;
            default -> defaultAccent;
        };
    }

    private static String compactEventLabel(String label) {
        String normalized = normalizeStatusToken(label);
        return switch (normalized) {
            case "next level cap day unlock" -> "Lvl Cap Unlock";
            case "credit shop reset" -> "Credit Reset";
            case "altar spawn" -> "Altar";
            case "next reboot" -> "Reboot";
            default -> label;
        };
    }

    private static String compactCooldownLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Cooldown";
        }
        return label;
    }

    private static String compactSatchelValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "--";
        }
        return trimmed;
    }

    private static String compactStatusText(String statusText, boolean cooldownMode) {
        String trimmed = statusText == null ? "" : statusText.trim();
        if (trimmed.isEmpty()) {
            return "--";
        }

        String normalized = normalizeStatusToken(trimmed);
        if (normalized.startsWith("check in ")) {
            return trimmed.substring(9).trim();
        }
        if ("unavailable".equals(normalized)) {
            return cooldownMode ? "--" : "N/A";
        }
        if ("not scheduled".equals(normalized)) {
            return "No plan";
        }
        if ("max day".equals(normalized)) {
            return "MAX";
        }
        if ("now".equals(normalized)) {
            return "LIVE";
        }
        return trimmed;
    }

    private static float parseSatchelProgress(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return 0.0F;
        }

        String trimmed = valueText.trim();
        int split = trimmed.indexOf(' ');
        String ratio = split > 0 ? trimmed.substring(0, split) : trimmed;
        int slashIndex = ratio.indexOf('/');
        if (slashIndex <= 0 || slashIndex >= ratio.length() - 1) {
            return 0.0F;
        }

        long stored = parseCompactAmount(ratio.substring(0, slashIndex));
        long capacity = parseCompactAmount(ratio.substring(slashIndex + 1));
        if (stored < 0L || capacity <= 0L) {
            return 0.0F;
        }

        return clamp01((float) stored / (float) capacity);
    }

    private static long parseCompactAmount(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }

        String trimmed = value.trim().toUpperCase(java.util.Locale.ROOT).replace(",", "");
        if (trimmed.isEmpty()) {
            return -1L;
        }

        long multiplier = 1L;
        char suffix = trimmed.charAt(trimmed.length() - 1);
        if (suffix == 'K' || suffix == 'M' || suffix == 'B' || suffix == 'T') {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
            multiplier =
                    switch (suffix) {
                        case 'K' -> 1_000L;
                        case 'M' -> 1_000_000L;
                        case 'B' -> 1_000_000_000L;
                        case 'T' -> 1_000_000_000_000L;
                        default -> 1L;
                    };
        }

        try {
            double parsed = Double.parseDouble(trimmed);
            if (parsed < 0.0D) {
                return -1L;
            }
            return Math.round(parsed * multiplier);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String normalizeStatusToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isUnavailableStatus(String normalized) {
        return "unavailable".equals(normalized)
                || "not scheduled".equals(normalized)
                || "max day".equals(normalized)
                || "n/a".equals(normalized);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private void processPingKeybinds(MinecraftClient client) {
        if (gangPingKeyBinding == null || trucePingKeyBinding == null) {
            return;
        }

        boolean canSendPings = shouldHandlePingKeybinds(client);

        while (gangPingKeyBinding.wasPressed()) {
            if (canSendPings) {
                sendPingIntent(ProtocolConstants.PING_TYPE_GANG);
            }
        }

        while (trucePingKeyBinding.wasPressed()) {
            if (canSendPings) {
                sendPingIntent(ProtocolConstants.PING_TYPE_TRUCE);
            }
        }
    }

    private void sendPingIntent(int pingType) {
        sendC2S(new ProtocolMessage.PingIntentC2S(pingType));
    }

    private void emitPingBeaconParticles(MinecraftClient client) {
        if (!shouldRenderPingBeacons(client) || client.world == null) {
            return;
        }

        if ((client.world.getTime() & 1L) != 0L) {
            return;
        }

        renderPingBeaconParticles(
                client.world,
                session.gangPingBeaconIdsSnapshot(),
                ParticleTypes.SOUL_FIRE_FLAME,
                ParticleTypes.END_ROD);
        renderPingBeaconParticles(
                client.world,
                session.trucePingBeaconIdsSnapshot(),
                ParticleTypes.FLAME,
                ParticleTypes.SMALL_FLAME);
    }

    private static void renderPingBeaconParticles(
            ClientWorld world,
            IntSet entityIds,
            ParticleEffect coreParticle,
            ParticleEffect orbitParticle) {
        for (int entityId : entityIds) {
            Entity entity = world.getEntityById(entityId);

            if (entity == null || entity.isRemoved()) {
                continue;
            }

            spawnPingBeaconParticles(world, entity, coreParticle, orbitParticle);
        }
    }

    private static void spawnPingBeaconParticles(
            ClientWorld world,
            Entity entity,
            ParticleEffect coreParticle,
            ParticleEffect orbitParticle) {
        double baseX = entity.getX();
        double baseY = entity.getY() + 0.3D;
        double baseZ = entity.getZ();

        for (int segment = 0; segment < PING_PARTICLE_COLUMN_SEGMENTS; segment++) {
            double y = baseY + (segment * 0.42D);
            world.addImportantParticleClient(coreParticle, baseX, y, baseZ, 0.0D, 0.012D, 0.0D);

            double radius = 0.2D + (segment * 0.025D);
            double angle = (world.getTime() * 0.22D) + (segment * 1.17D) + (entity.getId() * 0.13D);
            double orbitX = baseX + (Math.cos(angle) * radius);
            double orbitZ = baseZ + (Math.sin(angle) * radius);
            world.addParticleClient(orbitParticle, orbitX, y, orbitZ, 0.0D, 0.01D, 0.0D);
        }

        double topY = baseY + (PING_PARTICLE_COLUMN_SEGMENTS * 0.42D) + 0.2D;
        world.addImportantParticleClient(orbitParticle, baseX, topY, baseZ, 0.0D, 0.02D, 0.0D);
    }

    private synchronized void attemptSendClientHello(MinecraftClient client) {
        if (session.helloSent() || client.player == null) {
            return;
        }

        if (!ClientPlayNetworking.canSend(CompanionRawPayload.ID)) {
            if (!helloUnavailableLogged) {
                helloUnavailableLogged = true;
                LOGGER.debug("ClientHello postponed because channel cannot send yet");
            }
            return;
        }

        helloUnavailableLogged = false;
        ProtocolMessage.ClientHelloC2S hello =
                new ProtocolMessage.ClientHelloC2S(
                        buildAttestation.asStructuredClientVersion(), CLIENT_CAPABILITIES_BITSET);
        sendC2S(hello);
        session.markHelloSent();
    }

    private synchronized void sendC2S(ProtocolMessage message) {
        if (MinecraftClient.getInstance().player == null
                || !ClientPlayNetworking.canSend(CompanionRawPayload.ID)) {
            return;
        }

        byte[] bytes = protocolCodec.encode(message);
        ClientPlayNetworking.send(new CompanionRawPayload(bytes));
    }

    private synchronized void logMalformedOncePerConnection(BinaryDecodingException ex) {
        CompanionConfig currentConfig = getConfig();

        if (currentConfig.logMalformedOncePerConnection && session.malformedPacketLogged()) {
            return;
        }

        if (currentConfig.logMalformedOncePerConnection) {
            session.markMalformedPacketLogged();
        }

        LOGGER.warn("Dropped malformed companion payload: {}", ex.getMessage());
    }

    private synchronized void ensureFeatureDefaultsPersisted() {
        CompanionConfig currentConfig = getConfig();
        boolean changed = false;

        for (ClientFeatureDefinition feature : ClientFeatures.all()) {
            if (!currentConfig.featureToggles.containsKey(feature.id())) {
                currentConfig.featureToggles.put(feature.id(), feature.defaultEnabled());
                changed = true;
            }
        }

        if (changed) {
            configManager.save(currentConfig);
            config = currentConfig;
        }
    }

    private void initializePingKeybinds() {
        if (gangPingKeyBinding == null) {
            gangPingKeyBinding =
                    registerPingKeybind("key.cosmicprisonsmod.ping.gang", GLFW.GLFW_KEY_G);
        }

        if (trucePingKeyBinding == null) {
            trucePingKeyBinding =
                    registerPingKeybind("key.cosmicprisonsmod.ping.truce", GLFW.GLFW_KEY_H);
        }
    }

    private CompanionConfig getConfig() {
        if (config == null) {
            config = configManager.getOrLoad();
        }

        return config;
    }

    private boolean getFeatureToggleState(String featureId) {
        CompanionConfig currentConfig = getConfig();
        Boolean stored = currentConfig.featureToggles.get(featureId);

        if (stored != null) {
            return stored;
        }

        return ClientFeatures.findById(featureId)
                .map(ClientFeatureDefinition::defaultEnabled)
                .orElse(false);
    }

    private boolean isFeatureSupportedByServer(ClientFeatureDefinition feature) {
        if (feature.requiredServerFeatureBit().isEmpty()) {
            return true;
        }

        if (!session.gateState().isEnabled()) {
            return false;
        }

        int requiredBit = feature.requiredServerFeatureBit().getAsInt();
        return (session.serverFeatureFlags() & requiredBit) != 0;
    }

    private boolean shouldRenderInventoryItemOverlays() {
        return session.gateState().isEnabled()
                && session.inventoryItemOverlaysSupported()
                && getFeatureToggleState(ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID);
    }

    private boolean shouldRenderWidget(String featureId) {
        return getFeatureToggleState(featureId) && isFeatureSupportedByServer(featureId);
    }

    private boolean shouldRenderHudWidgets() {
        return session.gateState().isEnabled() && session.hudWidgetsSupported();
    }

    private boolean shouldHandlePingKeybinds(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.currentScreen == null
                && isGangTrucePingsFeatureEnabled();
    }

    private boolean shouldRenderPingBeacons(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.world != null
                && isGangTrucePingsFeatureEnabled();
    }

    private boolean isGangTrucePingsFeatureEnabled() {
        return session.gateState().isEnabled()
                && isFeatureSupportedByServer(ClientFeatures.PINGS_ID)
                && getFeatureToggleState(ClientFeatures.PINGS_ID);
    }

    private boolean shouldApplyPeacefulMiningPassThrough(MinecraftClient client) {
        if (client == null
                || client.player == null
                || client.world == null
                || client.options == null) {
            return false;
        }

        if (!session.gateState().isEnabled()
                || !isFeatureSupportedByServer(ClientFeatures.PEACEFUL_MINING_ID)
                || !getFeatureToggleState(ClientFeatures.PEACEFUL_MINING_ID)) {
            return false;
        }

        return client.options.attackKey.isPressed()
                || (client.interactionManager != null
                        && client.interactionManager.isBreakingBlock());
    }

    private static void drawOverlayText(
            DrawContext drawContext,
            int slotX,
            int slotY,
            ConnectionSessionState.ItemOverlayEntry overlayEntry) {
        if (overlayEntry.displayText() == null || overlayEntry.displayText().isEmpty()) {
            return;
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if (textRenderer == null) {
            return;
        }

        String displayText = overlayEntry.displayText();
        int textWidth = textRenderer.getWidth(displayText);

        if (textWidth <= 0) {
            return;
        }

        float baseScale = 0.68F;
        float fittedScale = Math.min(baseScale, 15.0F / textWidth);
        float textScale = Math.max(0.35F, fittedScale);
        int scaledTextWidth = Math.max(1, Math.round(textWidth * textScale));
        int scaledTextHeight = Math.max(1, Math.round(textRenderer.fontHeight * textScale));
        int textX = Math.max(slotX + 1, slotX + 16 - scaledTextWidth - 1);
        int textY = Math.max(slotY + 1, slotY + 16 - scaledTextHeight - 1);
        int textColor = colorForOverlayType(overlayEntry.overlayType());
        int backgroundColor = backgroundColorForOverlayType(overlayEntry.overlayType());
        int backgroundLeft = Math.max(slotX, textX - 1);
        int backgroundTop = Math.max(slotY, textY - 1);
        int backgroundRight = Math.min(slotX + 16, textX + scaledTextWidth + 1);
        int backgroundBottom = Math.min(slotY + 16, textY + scaledTextHeight + 1);

        drawContext.fill(
                backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, backgroundColor);
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(textX, textY);
        drawContext.getMatrices().scale(textScale, textScale);
        drawContext.drawTextWithShadow(textRenderer, displayText, 0, 0, textColor);
        drawContext.getMatrices().popMatrix();
    }

    private static int colorForOverlayType(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xFF5DE6FF;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xFF84F08F;
            default -> 0xFFE6E6E6;
        };
    }

    private static int backgroundColorForOverlayType(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xA01E3F52;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xA01B3C25;
            default -> 0xA0333333;
        };
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String normalizeWidgetId(String widgetId) {
        if (widgetId == null) {
            return "";
        }
        return widgetId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static KeyBinding registerPingKeybind(String translationKey, int defaultKeyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        translationKey,
                        InputUtil.Type.KEYSYM,
                        defaultKeyCode,
                        PING_KEYBIND_CATEGORY));
    }

    private static void bindKey(KeyBinding keyBinding, KeyInput keyInput) {
        keyBinding.setBoundKey(InputUtil.fromKeyCode(keyInput));
        KeyBinding.updateKeysByCode();
        persistGameOptions();
    }

    private static void persistGameOptions() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.options != null) {
            client.options.write();
        }
    }

    private static void sendClientMessage(MinecraftClient client, Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(CosmicPrisonsMod.MOD_ID)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public record HudWidgetPanel(
            String widgetId,
            String titleTranslationKey,
            int accentColor,
            int x,
            int y,
            int width,
            int height,
            int lineHeight,
            List<String> lines) {
        public HudWidgetPanel {
            lines = List.copyOf(lines);
        }
    }

    private record KnownOverlayStack(
            ItemStack stack, ConnectionSessionState.ItemOverlayEntry overlayEntry) {}
}
