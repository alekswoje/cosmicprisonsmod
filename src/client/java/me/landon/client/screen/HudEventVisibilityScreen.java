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

public final class HudEventVisibilityScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 46;

    private final CompanionClientRuntime runtime;
    private final Screen parent;
    private final List<ButtonWidget> toggleButtons = new ArrayList<>();

    private Map<String, Boolean> visibility = Map.of();

    public HudEventVisibilityScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.events_filter.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        visibility = new LinkedHashMap<>(runtime.getHudEventVisibilitySnapshot());
        toggleButtons.clear();

        int listWidth = Math.min(420, width - 36);
        int listX = (width - listWidth) / 2;

        for (int index = 0; index < HudWidgetCatalog.eventDescriptors().size(); index++) {
            HudWidgetCatalog.EventDescriptor descriptor =
                    HudWidgetCatalog.eventDescriptors().get(index);
            int rowY = LIST_TOP + (index * ROW_HEIGHT);

            ButtonWidget toggleButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            toggleText(descriptor.key()),
                                            button -> toggleEvent(descriptor.key(), button))
                                    .dimensions(listX + listWidth - 76, rowY + 2, 72, 18)
                                    .build());
            toggleButtons.add(toggleButton);
        }

        int footerY =
                Math.min(
                        height - 28,
                        LIST_TOP + (HudWidgetCatalog.eventDescriptors().size() * ROW_HEIGHT) + 10);
        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions((width / 2) - 70, footerY, 140, 20)
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
        drawContext.fill(0, 0, width, height, 0xBF0E131D);
        drawContext.fill(0, 0, width, 28, 0x6B273D5C);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.events_filter.subtitle"),
                width / 2,
                30,
                0xFFB8C7DA);

        renderRows(drawContext);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void renderRows(DrawContext drawContext) {
        int listWidth = Math.min(420, width - 36);
        int listX = (width - listWidth) / 2;

        for (int index = 0; index < HudWidgetCatalog.eventDescriptors().size(); index++) {
            HudWidgetCatalog.EventDescriptor descriptor =
                    HudWidgetCatalog.eventDescriptors().get(index);
            int rowY = LIST_TOP + (index * ROW_HEIGHT);
            int rowBottom = rowY + ROW_HEIGHT - 2;

            int background = (index % 2 == 0) ? 0xB5162133 : 0xB1111B2B;
            drawContext.fill(listX, rowY, listX + listWidth, rowBottom, background);
            drawContext.fill(listX, rowY, listX + listWidth, rowY + 1, 0xFF304A70);
            drawContext.fill(listX, rowBottom - 1, listX + listWidth, rowBottom, 0xFF25364D);

            String label = "[" + descriptor.iconTag() + "] " + descriptor.label();
            drawContext.drawTextWithShadow(textRenderer, label, listX + 8, rowY + 7, 0xFFE6EEF8);
        }
    }

    private void toggleEvent(String eventKey, ButtonWidget button) {
        boolean nowVisible = !Boolean.TRUE.equals(visibility.get(eventKey));
        visibility.put(eventKey, nowVisible);
        runtime.setHudEventVisibility(eventKey, nowVisible);
        button.setMessage(toggleText(eventKey));
    }

    private Text toggleText(String eventKey) {
        boolean visible = Boolean.TRUE.equals(visibility.get(eventKey));
        return visible
                ? Text.translatable("text.cosmicprisonsmod.settings.toggle.on")
                : Text.translatable("text.cosmicprisonsmod.settings.toggle.off");
    }
}
