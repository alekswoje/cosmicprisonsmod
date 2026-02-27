package me.landon.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.client.runtime.HudWidgetCatalog;
import me.landon.companion.config.CompanionConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudLayoutEditorScreen extends Screen {
    private final CompanionClientRuntime runtime;
    private final Screen parent;
    private final Map<String, CompanionConfig.HudWidgetPosition> workingPositions =
            new LinkedHashMap<>();
    private final Map<String, Double> workingScales = new LinkedHashMap<>();
    private final Map<String, Double> workingWidthMultipliers = new LinkedHashMap<>();

    private String selectedWidgetId;
    private String draggingWidgetId;
    private int draggingPanelWidth;
    private int draggingPanelHeight;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean positionsChanged;
    private boolean scalesChanged;
    private boolean widthsChanged;
    private ButtonWidget scaleDownButton;
    private ButtonWidget scaleUpButton;
    private ButtonWidget scaleResetButton;
    private ButtonWidget widthDownButton;
    private ButtonWidget widthUpButton;
    private ButtonWidget widthResetButton;

    public HudLayoutEditorScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.hud.editor.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        reloadWorkingState();

        int buttonWidth = 104;
        int gap = 8;
        int rowOneY = height - 80;
        int rowTwoY = height - 54;
        int rowThreeY = height - 28;
        int startX = (width - ((buttonWidth * 3) + (gap * 2))) / 2;

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.hud.editor.events_button"),
                                button -> {
                                    persistWorkingState();
                                    if (client != null) {
                                        client.setScreen(
                                                new HudEventVisibilityScreen(runtime, this));
                                    }
                                })
                        .dimensions(startX, rowOneY, buttonWidth, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.hud.editor.reset_button"),
                                button -> {
                                    runtime.resetHudLayout();
                                    reloadWorkingState();
                                    positionsChanged = false;
                                    scalesChanged = false;
                                    widthsChanged = false;
                                    selectedWidgetId = null;
                                    clearDragging();
                                    updateControlButtonsState();
                                })
                        .dimensions(startX + buttonWidth + gap, rowOneY, buttonWidth, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions(startX + ((buttonWidth + gap) * 2), rowOneY, buttonWidth, 20)
                        .build());

        scaleDownButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.scale_down_button"),
                                        button -> nudgeSelectedScale(-0.05D))
                                .dimensions(startX, rowTwoY, buttonWidth, 20)
                                .build());

        scaleUpButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.scale_up_button"),
                                        button -> nudgeSelectedScale(0.05D))
                                .dimensions(startX + buttonWidth + gap, rowTwoY, buttonWidth, 20)
                                .build());

        scaleResetButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.scale_reset_button"),
                                        button -> resetSelectedScale())
                                .dimensions(
                                        startX + ((buttonWidth + gap) * 2),
                                        rowTwoY,
                                        buttonWidth,
                                        20)
                                .build());

        widthDownButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.width_down_button"),
                                        button -> nudgeSelectedWidthMultiplier(-0.05D))
                                .dimensions(startX, rowThreeY, buttonWidth, 20)
                                .build());

        widthUpButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.width_up_button"),
                                        button -> nudgeSelectedWidthMultiplier(0.05D))
                                .dimensions(startX + buttonWidth + gap, rowThreeY, buttonWidth, 20)
                                .build());

        widthResetButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.hud.editor.width_reset_button"),
                                        button -> resetSelectedWidthMultiplier())
                                .dimensions(
                                        startX + ((buttonWidth + gap) * 2),
                                        rowThreeY,
                                        buttonWidth,
                                        20)
                                .build());

        updateControlButtonsState();
    }

    @Override
    public void close() {
        persistWorkingState();
        clearDragging();

        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        List<CompanionClientRuntime.HudWidgetPanel> panels =
                runtime.collectHudWidgetPanelsForEditor(
                        width, height, workingPositions, workingScales, workingWidthMultipliers);

        for (int index = panels.size() - 1; index >= 0; index--) {
            CompanionClientRuntime.HudWidgetPanel panel = panels.get(index);
            if (!isWithin(
                    click.x(),
                    click.y(),
                    panel.x(),
                    panel.y(),
                    panel.physicalWidth(),
                    panel.physicalHeight())) {
                continue;
            }

            selectedWidgetId = panel.widgetId();
            draggingWidgetId = panel.widgetId();
            draggingPanelWidth = panel.physicalWidth();
            draggingPanelHeight = panel.physicalHeight();
            dragOffsetX = click.x() - panel.x();
            dragOffsetY = click.y() - panel.y();
            updateControlButtonsState();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingWidgetId == null) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        int maxX = Math.max(0, width - draggingPanelWidth);
        int maxY = Math.max(0, height - draggingPanelHeight);

        int panelX = clamp((int) Math.round(click.x() - dragOffsetX), 0, maxX);
        int panelY = clamp((int) Math.round(click.y() - dragOffsetY), 0, maxY);

        double normalizedX = maxX == 0 ? 0.0D : panelX / (double) maxX;
        double normalizedY = maxY == 0 ? 0.0D : panelY / (double) maxY;

        workingPositions.put(
                draggingWidgetId, new CompanionConfig.HudWidgetPosition(normalizedX, normalizedY));
        positionsChanged = true;
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingWidgetId != null) {
            persistWorkingState();
            clearDragging();
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xBF101723);
        drawContext.fill(0, 0, width, 32, 0x6B294264);

        runtime.renderHudWidgetPanelsForEditor(
                drawContext, workingPositions, workingScales, workingWidthMultipliers);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.hud.editor.subtitle"),
                width / 2,
                36,
                0xFFC3D2E8);

        if (selectedWidgetId != null) {
            int scalePercent = (int) Math.round(currentSelectedScale() * 100.0D);
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable(
                            "text.cosmicprisonsmod.hud.editor.selected_widget",
                            selectedWidgetTitle(selectedWidgetId),
                            scalePercent + "%"),
                    width / 2,
                    50,
                    0xFFDCEBFF);

            if (isWidthAdjustableWidgetSelected()) {
                int widthPercent = (int) Math.round(currentSelectedWidthMultiplier() * 100.0D);
                drawContext.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.translatable(
                                "text.cosmicprisonsmod.hud.editor.selected_width",
                                selectedWidgetTitle(selectedWidgetId),
                                widthPercent + "%"),
                        width / 2,
                        62,
                        0xFFCEE8FF);
            }
        }

        if (draggingWidgetId != null) {
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.hud.editor.dragging"),
                    width / 2,
                    height - 92,
                    0xFFD7E6FC);
        }

        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void reloadWorkingState() {
        workingPositions.clear();
        workingPositions.putAll(runtime.getHudWidgetPositions());
        workingScales.clear();
        workingScales.putAll(runtime.getHudWidgetScales());
        workingWidthMultipliers.clear();
        workingWidthMultipliers.putAll(runtime.getHudWidgetWidthMultipliers());
    }

    private void persistWorkingState() {
        if (!positionsChanged && !scalesChanged && !widthsChanged) {
            return;
        }

        if (positionsChanged) {
            for (Map.Entry<String, CompanionConfig.HudWidgetPosition> entry :
                    workingPositions.entrySet()) {
                CompanionConfig.HudWidgetPosition position = entry.getValue();
                runtime.setHudWidgetPosition(entry.getKey(), position.x, position.y);
            }
            positionsChanged = false;
        }

        if (scalesChanged) {
            for (Map.Entry<String, Double> entry : workingScales.entrySet()) {
                runtime.setHudWidgetScale(entry.getKey(), entry.getValue());
            }
            scalesChanged = false;
        }

        if (widthsChanged) {
            for (Map.Entry<String, Double> entry : workingWidthMultipliers.entrySet()) {
                runtime.setHudWidgetWidthMultiplier(entry.getKey(), entry.getValue());
            }
            widthsChanged = false;
        }
    }

    private void nudgeSelectedScale(double delta) {
        if (selectedWidgetId == null) {
            return;
        }

        double currentScale = currentSelectedScale();
        double nextScale = clampScale(currentScale + delta);
        if (Math.abs(nextScale - currentScale) < 0.0001D) {
            return;
        }

        workingScales.put(selectedWidgetId, nextScale);
        scalesChanged = true;
        updateControlButtonsState();
    }

    private void resetSelectedScale() {
        if (selectedWidgetId == null) {
            return;
        }

        double defaultScale =
                CompanionConfig.defaults().hudWidgetScales.getOrDefault(selectedWidgetId, 1.0D);
        workingScales.put(selectedWidgetId, clampScale(defaultScale));
        scalesChanged = true;
        updateControlButtonsState();
    }

    private void nudgeSelectedWidthMultiplier(double delta) {
        if (!isWidthAdjustableWidgetSelected()) {
            return;
        }

        double current = currentSelectedWidthMultiplier();
        double next = clampWidthMultiplier(current + delta);
        if (Math.abs(next - current) < 0.0001D) {
            return;
        }

        workingWidthMultipliers.put(selectedWidgetId, next);
        widthsChanged = true;
        updateControlButtonsState();
    }

    private void resetSelectedWidthMultiplier() {
        if (!isWidthAdjustableWidgetSelected()) {
            return;
        }

        double defaultWidth =
                CompanionConfig.defaults()
                        .hudWidgetWidthMultipliers
                        .getOrDefault(selectedWidgetId, 1.0D);
        workingWidthMultipliers.put(selectedWidgetId, clampWidthMultiplier(defaultWidth));
        widthsChanged = true;
        updateControlButtonsState();
    }

    private void updateControlButtonsState() {
        if (scaleDownButton == null
                || scaleUpButton == null
                || scaleResetButton == null
                || widthDownButton == null
                || widthUpButton == null
                || widthResetButton == null) {
            return;
        }

        boolean hasSelection = selectedWidgetId != null;
        double currentScale = currentSelectedScale();
        scaleDownButton.active =
                hasSelection && currentScale > CompanionConfig.HUD_WIDGET_SCALE_MIN;
        scaleUpButton.active = hasSelection && currentScale < CompanionConfig.HUD_WIDGET_SCALE_MAX;
        scaleResetButton.active = hasSelection;

        boolean widthAdjustableSelected = isWidthAdjustableWidgetSelected();
        double widthMultiplier = currentSelectedWidthMultiplier();
        widthDownButton.active =
                widthAdjustableSelected
                        && widthMultiplier > CompanionConfig.HUD_WIDGET_WIDTH_MULTIPLIER_MIN;
        widthUpButton.active =
                widthAdjustableSelected
                        && widthMultiplier < CompanionConfig.HUD_WIDGET_WIDTH_MULTIPLIER_MAX;
        widthResetButton.active = widthAdjustableSelected;
    }

    private double currentSelectedScale() {
        if (selectedWidgetId == null) {
            return 1.0D;
        }
        return clampScale(
                workingScales.getOrDefault(
                        selectedWidgetId, runtime.getHudWidgetScale(selectedWidgetId)));
    }

    private double currentSelectedWidthMultiplier() {
        if (selectedWidgetId == null) {
            return 1.0D;
        }
        return clampWidthMultiplier(
                workingWidthMultipliers.getOrDefault(
                        selectedWidgetId, runtime.getHudWidgetWidthMultiplier(selectedWidgetId)));
    }

    private boolean isWidthAdjustableWidgetSelected() {
        if (selectedWidgetId == null) {
            return false;
        }
        return CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(selectedWidgetId)
                || CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(selectedWidgetId)
                || HudWidgetCatalog.isLeaderboardWidget(selectedWidgetId);
    }

    private static String selectedWidgetTitle(String widgetId) {
        return HudWidgetCatalog.findWidget(widgetId)
                .map(widget -> Text.translatable(widget.titleTranslationKey()).getString())
                .orElse(widgetId);
    }

    private void clearDragging() {
        draggingWidgetId = null;
        draggingPanelWidth = 0;
        draggingPanelHeight = 0;
        dragOffsetX = 0.0D;
        dragOffsetY = 0.0D;
    }

    private static double clampScale(double scale) {
        return CompanionConfig.clampHudWidgetScale(scale);
    }

    private static double clampWidthMultiplier(double widthMultiplier) {
        return CompanionConfig.clampHudWidgetWidthMultiplier(widthMultiplier);
    }

    private static boolean isWithin(
            double x, double y, int areaX, int areaY, int areaWidth, int areaHeight) {
        return x >= areaX && y >= areaY && x < areaX + areaWidth && y < areaY + areaHeight;
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
}
