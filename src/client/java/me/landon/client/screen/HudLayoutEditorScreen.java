package me.landon.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudLayoutEditorScreen extends Screen {
    private final CompanionClientRuntime runtime;
    private final Screen parent;

    private final Map<String, me.landon.companion.config.CompanionConfig.HudWidgetPosition>
            workingPositions = new LinkedHashMap<>();

    private String draggingWidgetId;
    private int draggingPanelWidth;
    private int draggingPanelHeight;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean positionsChanged;

    public HudLayoutEditorScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.hud.editor.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        reloadWorkingPositions();

        int buttonY = height - 28;
        int buttonWidth = 116;
        int gap = 8;
        int startX = (width - ((buttonWidth * 3) + (gap * 2))) / 2;

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.hud.editor.events_button"),
                                button -> {
                                    persistWorkingPositions();
                                    if (client != null) {
                                        client.setScreen(
                                                new HudEventVisibilityScreen(runtime, this));
                                    }
                                })
                        .dimensions(startX, buttonY, buttonWidth, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.hud.editor.reset_button"),
                                button -> {
                                    runtime.resetHudLayout();
                                    reloadWorkingPositions();
                                    positionsChanged = false;
                                    clearDragging();
                                })
                        .dimensions(startX + buttonWidth + gap, buttonY, buttonWidth, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions(startX + ((buttonWidth + gap) * 2), buttonY, buttonWidth, 20)
                        .build());
    }

    @Override
    public void close() {
        persistWorkingPositions();
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
                runtime.collectHudWidgetPanelsForEditor(width, height, workingPositions);

        for (int index = panels.size() - 1; index >= 0; index--) {
            CompanionClientRuntime.HudWidgetPanel panel = panels.get(index);
            if (!isWithin(
                    click.x(), click.y(), panel.x(), panel.y(), panel.width(), panel.height())) {
                continue;
            }

            draggingWidgetId = panel.widgetId();
            draggingPanelWidth = panel.width();
            draggingPanelHeight = panel.height();
            dragOffsetX = click.x() - panel.x();
            dragOffsetY = click.y() - panel.y();
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
                draggingWidgetId,
                new me.landon.companion.config.CompanionConfig.HudWidgetPosition(
                        normalizedX, normalizedY));
        positionsChanged = true;
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingWidgetId != null) {
            persistWorkingPositions();
            clearDragging();
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xBF101723);
        drawContext.fill(0, 0, width, 26, 0x6B294264);

        runtime.renderHudWidgetPanelsForEditor(drawContext, workingPositions);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.hud.editor.subtitle"),
                width / 2,
                30,
                0xFFC3D2E8);

        if (draggingWidgetId != null) {
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.hud.editor.dragging"),
                    width / 2,
                    height - 42,
                    0xFFD7E6FC);
        }

        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void reloadWorkingPositions() {
        workingPositions.clear();
        workingPositions.putAll(runtime.getHudWidgetPositions());
    }

    private void persistWorkingPositions() {
        if (!positionsChanged) {
            return;
        }

        for (Map.Entry<String, me.landon.companion.config.CompanionConfig.HudWidgetPosition> entry :
                workingPositions.entrySet()) {
            me.landon.companion.config.CompanionConfig.HudWidgetPosition position =
                    entry.getValue();
            runtime.setHudWidgetPosition(entry.getKey(), position.x, position.y);
        }

        positionsChanged = false;
    }

    private void clearDragging() {
        draggingWidgetId = null;
        draggingPanelWidth = 0;
        draggingPanelHeight = 0;
        dragOffsetX = 0.0D;
        dragOffsetY = 0.0D;
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
