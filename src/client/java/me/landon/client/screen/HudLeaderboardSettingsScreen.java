package me.landon.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.client.runtime.HudWidgetCatalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudLeaderboardSettingsScreen extends Screen {
    private static final int PANEL_TOP = 50;
    private static final int PANEL_BOTTOM_MARGIN = 24;
    private static final int PANEL_HORIZONTAL_MARGIN = 22;
    private static final int MODE_SECTION_HEIGHT = 36;
    private static final int ROW_HEIGHT = 26;

    private final CompanionClientRuntime runtime;
    private final Screen parent;
    private final List<ButtonWidget> visibilityButtons = new ArrayList<>();

    private Map<String, Boolean> visibilityByWidget = Map.of();
    private boolean compactMode;
    private boolean cycleMode;

    public HudLeaderboardSettingsScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.leaderboards_filter.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        visibilityByWidget = new LinkedHashMap<>(runtime.getHudLeaderboardVisibilitySnapshot());
        compactMode = runtime.isHudLeaderboardsCompactMode();
        cycleMode = runtime.isHudLeaderboardsCycleMode();
        visibilityButtons.clear();

        int panelX = panelX();
        int panelWidth = panelWidth();
        int modeY = modeY();
        int modeButtonWidth = 168;
        int modeGap = 12;
        int modeStartX = panelX + ((panelWidth - ((modeButtonWidth * 2) + modeGap)) / 2);

        addDrawableChild(
                ButtonWidget.builder(modeLayoutText(), button -> toggleCycleMode(button))
                        .dimensions(modeStartX, modeY, modeButtonWidth, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(modeCompactText(), button -> toggleCompactMode(button))
                        .dimensions(
                                modeStartX + modeButtonWidth + modeGap, modeY, modeButtonWidth, 20)
                        .build());

        int rowsTop = rowsTop();
        int toggleWidth = 86;
        int buttonX = panelX + panelWidth - toggleWidth - 12;

        for (int index = 0; index < HudWidgetCatalog.leaderboardWidgets().size(); index++) {
            HudWidgetCatalog.WidgetDescriptor descriptor =
                    HudWidgetCatalog.leaderboardWidgets().get(index);
            int rowY = rowsTop + (index * ROW_HEIGHT);

            ButtonWidget toggleButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            toggleText(descriptor.widgetId()),
                                            button ->
                                                    toggleLeaderboard(
                                                            descriptor.widgetId(), button))
                                    .dimensions(buttonX, rowY + 3, toggleWidth, 20)
                                    .build());
            visibilityButtons.add(toggleButton);
        }

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions((width / 2) - 74, doneButtonY(), 148, 20)
                        .build());
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xC40C1421);
        drawContext.fill(0, 0, width, 32, 0x7F26405E);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.leaderboards_filter.subtitle"),
                width / 2,
                36,
                0xFFD1DEEF);

        renderPanel(drawContext);
        renderRows(drawContext);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void renderPanel(DrawContext drawContext) {
        int panelX = panelX();
        int panelWidth = panelWidth();
        int panelBottom = panelBottom();
        int modeContainerY = modeY() - 6;

        drawContext.fill(panelX, PANEL_TOP, panelX + panelWidth, panelBottom, 0xC1101A2B);
        drawContext.fill(panelX, PANEL_TOP, panelX + panelWidth, PANEL_TOP + 1, 0xFF385478);
        drawContext.fill(panelX, panelBottom - 1, panelX + panelWidth, panelBottom, 0xFF2A3E5A);
        drawContext.fill(panelX, PANEL_TOP, panelX + 1, panelBottom, 0xFF2A3E5A);
        drawContext.fill(
                panelX + panelWidth - 1, PANEL_TOP, panelX + panelWidth, panelBottom, 0xFF2A3E5A);

        drawContext.fill(
                panelX + 10,
                modeContainerY,
                panelX + panelWidth - 10,
                modeContainerY + MODE_SECTION_HEIGHT,
                0xA9172438);
        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.leaderboards_filter.mode.section"),
                panelX + 16,
                modeContainerY + 4,
                0xFFD6E6F8);
    }

    private void renderRows(DrawContext drawContext) {
        int panelX = panelX();
        int panelWidth = panelWidth();
        int rowsTop = rowsTop();

        for (int index = 0; index < HudWidgetCatalog.leaderboardWidgets().size(); index++) {
            HudWidgetCatalog.WidgetDescriptor descriptor =
                    HudWidgetCatalog.leaderboardWidgets().get(index);
            int rowY = rowsTop + (index * ROW_HEIGHT);
            int rowBottom = rowY + ROW_HEIGHT - 2;
            int rowColor = (index % 2 == 0) ? 0x9F15253A : 0x9F111F32;

            drawContext.fill(panelX + 10, rowY, panelX + panelWidth - 10, rowBottom, rowColor);
            drawContext.fill(panelX + 10, rowY, panelX + panelWidth - 10, rowY + 1, 0xFF314A69);

            String label = Text.translatable(descriptor.titleTranslationKey()).getString();
            drawContext.drawTextWithShadow(textRenderer, label, panelX + 18, rowY + 8, 0xFFE8F0FA);
        }
    }

    private void toggleLeaderboard(String widgetId, ButtonWidget button) {
        boolean nowVisible = !Boolean.TRUE.equals(visibilityByWidget.get(widgetId));
        visibilityByWidget.put(widgetId, nowVisible);
        runtime.setHudLeaderboardVisibility(widgetId, nowVisible);
        button.setMessage(toggleText(widgetId));
    }

    private void toggleCycleMode(ButtonWidget button) {
        cycleMode = !cycleMode;
        runtime.setHudLeaderboardsCycleMode(cycleMode);
        button.setMessage(modeLayoutText());
    }

    private void toggleCompactMode(ButtonWidget button) {
        compactMode = !compactMode;
        runtime.setHudLeaderboardsCompactMode(compactMode);
        button.setMessage(modeCompactText());
    }

    private Text toggleText(String widgetId) {
        boolean visible = Boolean.TRUE.equals(visibilityByWidget.get(widgetId));
        return visible
                ? Text.translatable("text.cosmicprisonsmod.settings.toggle.on")
                : Text.translatable("text.cosmicprisonsmod.settings.toggle.off");
    }

    private Text modeLayoutText() {
        return Text.translatable(
                "text.cosmicprisonsmod.leaderboards_filter.mode.layout",
                cycleMode
                        ? Text.translatable("text.cosmicprisonsmod.leaderboards_filter.mode.cycle")
                        : Text.translatable(
                                "text.cosmicprisonsmod.leaderboards_filter.mode.individual"));
    }

    private Text modeCompactText() {
        return Text.translatable(
                "text.cosmicprisonsmod.leaderboards_filter.mode.compact",
                compactMode
                        ? Text.translatable("text.cosmicprisonsmod.events_filter.mode.compact")
                        : Text.translatable("text.cosmicprisonsmod.events_filter.mode.full"));
    }

    private int panelWidth() {
        return Math.min(720, width - (PANEL_HORIZONTAL_MARGIN * 2));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelBottom() {
        return height - PANEL_BOTTOM_MARGIN;
    }

    private int modeY() {
        return PANEL_TOP + 18;
    }

    private int rowsTop() {
        return PANEL_TOP + MODE_SECTION_HEIGHT + 26;
    }

    private int doneButtonY() {
        return panelBottom() - 30;
    }
}
