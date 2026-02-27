package me.landon.client.screen;

import java.util.ArrayList;
import java.util.List;
import me.landon.client.feature.ClientFeatureDefinition;
import me.landon.client.feature.ClientFeatures;
import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.companion.protocol.ProtocolConstants;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class FeatureSettingsScreen extends Screen {
    private static final int GRID_TOP = 42;
    private static final int CARD_WIDTH = 176;
    private static final int CARD_HEIGHT = 92;
    private static final int CARD_GAP = 10;
    private static final int POPUP_BASE_WIDTH = 320;
    private static final int POPUP_BASE_HEIGHT = 208;

    private final CompanionClientRuntime runtime;
    private final List<ButtonWidget> mainButtons = new ArrayList<>();
    private List<ClientFeatureDefinition> features = List.of();

    private String popupFeatureId;
    private boolean popupOpening;
    private float popupProgress;

    public FeatureSettingsScreen(CompanionClientRuntime runtime) {
        super(Text.translatable("text.cosmicprisonsmod.settings.title"));
        this.runtime = runtime;
    }

    @Override
    protected void init() {
        super.init();
        features = runtime.getAvailableFeatures();
        mainButtons.clear();

        for (int index = 0; index < features.size(); index++) {
            ClientFeatureDefinition feature = features.get(index);
            CardBounds cardBounds = cardBounds(index);
            int toggleWidth = 56;
            int seeMoreWidth = 84;
            int buttonY = cardBounds.y + cardBounds.height - 24;
            int toggleX = cardBounds.right() - toggleWidth - 8;
            int seeMoreX = toggleX - seeMoreWidth - 6;

            ButtonWidget seeMoreButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            Text.translatable(
                                                    "text.cosmicprisonsmod.settings.see_more"),
                                            button -> openPopup(feature.id()))
                                    .dimensions(seeMoreX, buttonY, seeMoreWidth, 18)
                                    .build());
            mainButtons.add(seeMoreButton);

            ButtonWidget toggleButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            toggleButtonText(feature.id()),
                                            button -> {
                                                runtime.setFeatureEnabled(
                                                        feature.id(),
                                                        !runtime.isFeatureEnabled(feature.id()));
                                                button.setMessage(toggleButtonText(feature.id()));
                                            })
                                    .dimensions(toggleX, buttonY, toggleWidth, 18)
                                    .build());
            mainButtons.add(toggleButton);
        }

        ButtonWidget doneButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.settings.button.done"),
                                        button -> close())
                                .dimensions((width / 2) - 84, height - 30, 168, 20)
                                .build());
        mainButtons.add(doneButton);

        updateMainButtonsState();
    }

    @Override
    public void tick() {
        super.tick();

        if (popupFeatureId == null) {
            popupProgress = 0.0F;
            return;
        }

        float speed = 0.16F;

        if (popupOpening) {
            popupProgress = Math.min(1.0F, popupProgress + speed);
        } else {
            popupProgress = Math.max(0.0F, popupProgress - speed);

            if (popupProgress <= 0.0F) {
                popupFeatureId = null;
            }
        }

        updateMainButtonsState();
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE && popupFeatureId != null) {
            closePopup();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (popupFeatureId != null && popupProgress > 0.05F) {
            PopupLayout popupLayout = popupLayout(popupProgress);
            boolean clickedClose =
                    isPointWithin(
                            click.x(),
                            click.y(),
                            popupLayout.closeX,
                            popupLayout.closeY,
                            popupLayout.closeWidth,
                            popupLayout.closeHeight);
            boolean clickedInsidePopup =
                    isPointWithin(
                            click.x(),
                            click.y(),
                            popupLayout.x,
                            popupLayout.y,
                            popupLayout.width,
                            popupLayout.height);

            if (clickedClose || !clickedInsidePopup) {
                closePopup();
            }

            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(new GameMenuScreen(true));
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xB010131A);
        drawContext.fill(0, 0, width, 24, 0x6B213655);
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.subtitle"),
                width / 2,
                27,
                0xFFB8C7DB);

        renderFeatureCards(drawContext);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
        renderPopup(drawContext);
    }

    private void renderFeatureCards(DrawContext drawContext) {
        if (features.isEmpty()) {
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.settings.empty"),
                    width / 2,
                    GRID_TOP + 16,
                    0xFFBFC6D2);
            return;
        }

        for (int index = 0; index < features.size(); index++) {
            ClientFeatureDefinition feature = features.get(index);
            CardBounds cardBounds = cardBounds(index);
            int accentColor = featureAccentColor(feature.id());

            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x141B29, 232));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.y + 2,
                    withAlpha(accentColor, 255));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.bottom() - 1,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.x + 1,
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));
            drawContext.fill(
                    cardBounds.right() - 1,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));

            drawFeatureIcon(drawContext, feature, cardBounds.x + 8, cardBounds.y + 8, accentColor);

            drawContext.drawTextWithShadow(
                    textRenderer,
                    Text.translatable(feature.nameTranslationKey()),
                    cardBounds.x + 50,
                    cardBounds.y + 12,
                    0xFFFFFFFF);

            String summary = summaryText(feature, cardBounds.width - 20);
            drawContext.drawWrappedTextWithShadow(
                    textRenderer,
                    Text.literal(summary),
                    cardBounds.x + 10,
                    cardBounds.y + 42,
                    cardBounds.width - 20,
                    0xFFB9C5D8);
        }
    }

    private void renderPopup(DrawContext drawContext) {
        if (popupFeatureId == null || popupProgress <= 0.0F) {
            return;
        }

        ClientFeatureDefinition feature = featureById(popupFeatureId);

        if (feature == null) {
            return;
        }

        float eased = easeOutBack(popupProgress);
        int overlayAlpha = (int) (170.0F * Math.min(1.0F, popupProgress * 1.2F));
        drawContext.fill(0, 0, width, height, overlayAlpha << 24);

        PopupLayout popupLayout = popupLayout(popupProgress);
        int x = popupLayout.x;
        int y = popupLayout.y;
        int right = x + popupLayout.width;
        int bottom = y + popupLayout.height;
        int accentColor = featureAccentColor(feature.id());

        drawContext.fill(x - 2, y - 2, right + 2, bottom + 2, withAlpha(0x02050A, 150));
        drawContext.fill(x, y, right, bottom, withAlpha(0x111929, 246));
        drawContext.fill(x, y, right, y + 2, withAlpha(accentColor, 255));
        drawContext.fill(x, y, x + 1, bottom, withAlpha(0x3A4C70, 255));
        drawContext.fill(right - 1, y, right, bottom, withAlpha(0x3A4C70, 255));
        drawContext.fill(x, bottom - 1, right, bottom, withAlpha(0x3A4C70, 255));

        drawFeatureIcon(drawContext, feature, x + 12, y + 12, accentColor);

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable(feature.nameTranslationKey()),
                x + 56,
                y + 18,
                0xFFFFFFFF);

        drawContext.drawWrappedTextWithShadow(
                textRenderer,
                Text.translatable(feature.descriptionTranslationKey()),
                x + 14,
                y + 48,
                popupLayout.width - 28,
                0xFFE5EDF8);

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.examples"),
                x + 14,
                y + 102,
                0xFFE6EEFB);

        int examplesY = y + 116;
        int examplesGap = 8;
        int exampleWidth = (popupLayout.width - 28 - examplesGap) / 2;
        int leftExampleX = x + 14;
        int rightExampleX = leftExampleX + exampleWidth + examplesGap;
        renderPopupExamples(
                drawContext, feature, leftExampleX, rightExampleX, examplesY, exampleWidth);

        drawContext.fill(
                popupLayout.closeX,
                popupLayout.closeY,
                popupLayout.closeX + popupLayout.closeWidth,
                popupLayout.closeY + popupLayout.closeHeight,
                withAlpha(0x324B73, 255));
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.close"),
                popupLayout.closeX + (popupLayout.closeWidth / 2),
                popupLayout.closeY + 5,
                0xFFFFFFFF);

        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.hint"),
                x + (popupLayout.width / 2),
                bottom - 9,
                withAlpha(0xC0D0E5, (int) (215.0F * eased)));
    }

    private void drawPopupExample(
            DrawContext drawContext,
            int x,
            int y,
            int width,
            Item item,
            int overlayType,
            String overlayText,
            Text label) {
        drawContext.fill(x, y, x + width, y + 31, withAlpha(0x1A2436, 220));
        drawContext.fill(x, y, x + width, y + 1, withAlpha(0x354B70, 255));
        int itemX = x + 6;
        int itemY = y + 7;
        drawContext.drawItem(item.getDefaultStack(), itemX, itemY);
        drawOverlayTextOnItem(drawContext, itemX, itemY, overlayType, overlayText, 0.56F);
        drawContext.drawTextWithShadow(textRenderer, label, x + 27, y + 11, 0xFFE7EEF9);
    }

    private void drawPopupTextExample(DrawContext drawContext, int x, int y, int width, Text text) {
        drawContext.fill(x, y, x + width, y + 31, withAlpha(0x1A2436, 220));
        drawContext.fill(x, y, x + width, y + 1, withAlpha(0x354B70, 255));
        drawContext.drawWrappedTextWithShadow(
                textRenderer, text, x + 7, y + 9, width - 14, 0xFFE7EEF9);
    }

    private void renderPopupExamples(
            DrawContext drawContext,
            ClientFeatureDefinition feature,
            int leftExampleX,
            int rightExampleX,
            int examplesY,
            int exampleWidth) {
        if (ClientFeatures.PEACEFUL_MINING_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.peaceful_mining.example.player_blocking"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.peaceful_mining.example.mine_through"));
            return;
        }

        drawPopupExample(
                drawContext,
                leftExampleX,
                examplesY,
                exampleWidth,
                Items.LIGHT_BLUE_DYE,
                ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY,
                Text.translatable(
                                "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_value")
                        .getString(),
                Text.translatable(
                        "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_label"));
        drawPopupExample(
                drawContext,
                rightExampleX,
                examplesY,
                exampleWidth,
                Items.PAPER,
                ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE,
                Text.translatable(
                                "text.cosmicprisonsmod.feature.inventory_item_overlays.example.money_value")
                        .getString(),
                Text.translatable(
                        "text.cosmicprisonsmod.feature.inventory_item_overlays.example.money_label"));
    }

    private void openPopup(String featureId) {
        popupFeatureId = featureId;
        popupProgress = 0.0F;
        popupOpening = true;
        updateMainButtonsState();
    }

    private void closePopup() {
        popupOpening = false;
    }

    private void updateMainButtonsState() {
        boolean popupActive = popupFeatureId != null;

        for (ButtonWidget button : mainButtons) {
            button.active = !popupActive;
        }
    }

    private ClientFeatureDefinition featureById(String featureId) {
        for (ClientFeatureDefinition feature : features) {
            if (feature.id().equals(featureId)) {
                return feature;
            }
        }

        return null;
    }

    private String summaryText(ClientFeatureDefinition feature, int maxWidth) {
        String full = Text.translatable(feature.descriptionTranslationKey()).getString();
        String trimmed = textRenderer.trimToWidth(full, maxWidth * 2);

        if (trimmed.length() >= full.length()) {
            return full;
        }

        if (trimmed.length() <= 3) {
            return "...";
        }

        return trimmed.substring(0, trimmed.length() - 3) + "...";
    }

    private void drawFeatureIcon(
            DrawContext drawContext,
            ClientFeatureDefinition feature,
            int x,
            int y,
            int accentColor) {
        int size = 34;
        drawContext.fill(x, y, x + size, y + size, withAlpha(0x11131A, 230));
        drawContext.fill(x, y, x + size, y + 1, withAlpha(accentColor, 255));
        drawContext.fill(x, y, x + 1, y + size, withAlpha(0x3A4B6A, 255));
        drawContext.fill(x + size - 1, y, x + size, y + size, withAlpha(0x3A4B6A, 255));
        drawContext.fill(x, y + size - 1, x + size, y + size, withAlpha(0x3A4B6A, 255));

        int itemX = x + 9;
        int itemY = y + 9;

        if (ClientFeatures.PEACEFUL_MINING_ID.equals(feature.id())) {
            drawContext.drawItem(Items.IRON_PICKAXE.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.PAPER.getDefaultStack(), itemX, itemY);
            drawOverlayTextOnItem(
                    drawContext,
                    itemX,
                    itemY,
                    ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY,
                    Text.translatable(
                                    "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_value")
                            .getString(),
                    0.54F);
            return;
        }

        Text icon = Text.translatable(feature.iconTranslationKey());
        int iconWidth = textRenderer.getWidth(icon);
        drawContext.drawTextWithShadow(
                textRenderer, icon, x + ((size - iconWidth) / 2), y + 13, 0xFFFFFFFF);
    }

    private void drawOverlayTextOnItem(
            DrawContext drawContext,
            int slotX,
            int slotY,
            int overlayType,
            String displayText,
            float baseScale) {
        if (textRenderer == null || displayText == null || displayText.isEmpty()) {
            return;
        }

        int textWidth = textRenderer.getWidth(displayText);

        if (textWidth <= 0) {
            return;
        }

        float fittedScale = Math.min(baseScale, 15.0F / textWidth);
        float textScale = Math.max(0.34F, fittedScale);
        int scaledTextWidth = Math.max(1, Math.round(textWidth * textScale));
        int scaledTextHeight = Math.max(1, Math.round(textRenderer.fontHeight * textScale));
        int textX = Math.max(slotX + 1, slotX + 16 - scaledTextWidth - 1);
        int textY = Math.max(slotY + 1, slotY + 16 - scaledTextHeight - 1);
        int textColor = overlayTextColor(overlayType);
        int backgroundColor = overlayBackgroundColor(overlayType);
        int backgroundLeft = Math.max(slotX, textX - 1);
        int backgroundTop = Math.max(slotY, textY - 1);
        int backgroundRight = Math.min(slotX + 16, textX + scaledTextWidth + 1);
        int backgroundBottom = Math.min(slotY + 16, textY + scaledTextHeight + 1);

        drawContext.enableScissor(slotX, slotY, slotX + 16, slotY + 16);
        drawContext.fill(
                backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, backgroundColor);
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(textX, textY);
        drawContext.getMatrices().scale(textScale, textScale);
        drawContext.drawTextWithShadow(textRenderer, displayText, 0, 0, textColor);
        drawContext.getMatrices().popMatrix();
        drawContext.disableScissor();
    }

    private int featureAccentColor(String featureId) {
        if (ClientFeatures.PEACEFUL_MINING_ID.equals(featureId)) {
            return 0x61C08D;
        }

        if (ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID.equals(featureId)) {
            return 0x46A9FF;
        }

        return 0x6C7FA0;
    }

    private int gridColumns() {
        int availableWidth = Math.max(CARD_WIDTH, width - 36);
        int columns = (availableWidth + CARD_GAP) / (CARD_WIDTH + CARD_GAP);
        return Math.max(1, Math.min(3, columns));
    }

    private CardBounds cardBounds(int index) {
        int columns = gridColumns();
        int row = index / columns;
        int column = index % columns;
        int gridWidth = (columns * CARD_WIDTH) + ((columns - 1) * CARD_GAP);
        int left = (width - gridWidth) / 2;
        int x = left + (column * (CARD_WIDTH + CARD_GAP));
        int y = GRID_TOP + (row * (CARD_HEIGHT + CARD_GAP));
        return new CardBounds(x, y, CARD_WIDTH, CARD_HEIGHT);
    }

    private PopupLayout popupLayout(float progress) {
        float eased = easeOutBack(progress);
        int targetWidth = Math.min(POPUP_BASE_WIDTH, width - 26);
        int targetHeight = Math.min(POPUP_BASE_HEIGHT, height - 28);
        float scale = 0.86F + (0.14F * eased);
        int popupWidth = Math.max(240, Math.round(targetWidth * scale));
        int popupHeight = Math.max(160, Math.round(targetHeight * scale));
        int x = (width - popupWidth) / 2;
        int y = ((height - popupHeight) / 2) + Math.round((1.0F - eased) * 12.0F);
        int closeWidth = 64;
        int closeHeight = 18;
        int closeX = x + popupWidth - closeWidth - 10;
        int closeY = y + popupHeight - closeHeight - 20;
        return new PopupLayout(
                x, y, popupWidth, popupHeight, closeX, closeY, closeWidth, closeHeight);
    }

    private static int overlayTextColor(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xFF5DE6FF;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xFF84F08F;
            default -> 0xFFE6E6E6;
        };
    }

    private static int overlayBackgroundColor(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xA01E3F52;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xA01B3C25;
            default -> 0xA0333333;
        };
    }

    private static float easeOutBack(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float shifted = clamped - 1.0F;
        return 1.0F + (c3 * shifted * shifted * shifted) + (c1 * shifted * shifted);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static boolean isPointWithin(
            double x, double y, int areaX, int areaY, int areaWidth, int areaHeight) {
        return x >= areaX && y >= areaY && x < (areaX + areaWidth) && y < (areaY + areaHeight);
    }

    private Text toggleButtonText(String featureId) {
        return runtime.isFeatureEnabled(featureId)
                ? Text.translatable("text.cosmicprisonsmod.settings.toggle.on")
                : Text.translatable("text.cosmicprisonsmod.settings.toggle.off");
    }

    private record CardBounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }

    private record PopupLayout(
            int x,
            int y,
            int width,
            int height,
            int closeX,
            int closeY,
            int closeWidth,
            int closeHeight) {}
}
