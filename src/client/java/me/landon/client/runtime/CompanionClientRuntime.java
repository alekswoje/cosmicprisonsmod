package me.landon.client.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionClientRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionClientRuntime.class);
    private static final CompanionClientRuntime INSTANCE = new CompanionClientRuntime();
    private static final int CLIENT_CAPABILITIES_BITSET = 63;
    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
    private static final int KNOWN_OVERLAY_STACK_LIMIT = 64;
    private static final int PENDING_CURSOR_OVERLAY_FRAMES = 4;

    private final ProtocolCodec protocolCodec = new ProtocolCodec();
    private final SignatureVerifier signatureVerifier = new SignatureVerifier();
    private final ConnectionSessionState session = new ConnectionSessionState();
    private final CompanionConfigManager configManager = new CompanionConfigManager();
    private final BuildAttestationLoader buildAttestationLoader = new BuildAttestationLoader();
    private final List<KnownOverlayStack> knownOverlayStacks = new ArrayList<>();

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

        ClientPlayNetworking.registerGlobalReceiver(
                CompanionRawPayload.ID, this::onPayloadReceived);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        ScreenEvents.AFTER_INIT.register(this::onScreenAfterInit);

        HudRenderCallback.EVENT.register(this::renderHotbarOverlays);

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
                if (pendingCursorOverlayFrames > 0 && activeCursorOverlayEntry != null) {
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

        if (!session.helloSent() && helloRetryTicks > 0) {
            helloRetryTicks--;
            attemptSendClientHello(client);
        }
    }

    private synchronized void onWorldChange(MinecraftClient client, ClientWorld world) {
        if (world == null) {
            session.clearInventoryItemOverlays();
            session.clearPeacefulMiningPassThroughIds();
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

        boolean supportsInventoryItemOverlays =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS)
                        != 0;
        boolean supportsEntityMarkers =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_ENTITY_MARKERS)
                        != 0;
        session.setInventoryItemOverlaysSupported(supportsInventoryItemOverlays);

        if (!supportsInventoryItemOverlays) {
            session.clearInventoryItemOverlays();
            clearOverlayRenderCaches();
        }

        if (!supportsEntityMarkers) {
            session.clearPeacefulMiningPassThroughIds();
            clearActivePeacefulMiningTarget();
        }
    }

    private void handleInventoryItemOverlays(ProtocolMessage.InventoryItemOverlaysS2C overlays) {
        if (!session.inventoryItemOverlaysSupported()) {
            session.setInventoryItemOverlaysSupported(true);
        }

        session.replaceInventoryItemOverlays(overlays.overlays());

        if (overlays.overlays().isEmpty()) {
            clearOverlayRenderCaches();
        }
    }

    private void handleEntityMarkerDelta(ProtocolMessage.EntityMarkerDeltaS2C markerDelta) {
        if (markerDelta.markerType()
                != ProtocolConstants.MARKER_TYPE_PEACEFUL_MINING_PASS_THROUGH) {
            return;
        }

        session.applyPeacefulMiningPassThroughDelta(
                markerDelta.addEntityIds(), markerDelta.removeEntityIds());
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

        for (int i = knownOverlayStacks.size() - 1; i >= 0; i--) {
            KnownOverlayStack knownOverlayStack = knownOverlayStacks.get(i);

            if (ItemStack.areItemsAndComponentsEqual(knownOverlayStack.stack(), stack)) {
                return knownOverlayStack.overlayEntry();
            }
        }

        return null;
    }

    private void renderHotbarOverlays(DrawContext drawContext, RenderTickCounter tickCounter) {
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

    private record KnownOverlayStack(
            ItemStack stack, ConnectionSessionState.ItemOverlayEntry overlayEntry) {}
}
