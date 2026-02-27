package me.landon.client.screen;

import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class PingKeybindSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 56;

    private final CompanionClientRuntime runtime;
    private final Screen parent;

    private ButtonWidget gangKeyButton;
    private ButtonWidget truceKeyButton;
    private CaptureTarget captureTarget = CaptureTarget.NONE;

    public PingKeybindSettingsScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.ping_keys.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int rowWidth = Math.min(430, width - 32);
        int rowX = (width - rowWidth) / 2;

        gangKeyButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.empty(), button -> beginCapture(CaptureTarget.GANG))
                                .dimensions(rowX + rowWidth - 128, LIST_TOP + 4, 120, 20)
                                .build());
        truceKeyButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.empty(), button -> beginCapture(CaptureTarget.TRUCE))
                                .dimensions(
                                        rowX + rowWidth - 128, LIST_TOP + ROW_HEIGHT + 4, 120, 20)
                                .build());

        int footerY = LIST_TOP + (ROW_HEIGHT * 2) + 20;
        int footerButtonWidth = 120;
        int footerGap = 8;
        int footerStartX = (width - ((footerButtonWidth * 2) + footerGap)) / 2;
        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.ping_keys.reset_button"),
                                button -> {
                                    runtime.resetPingKeybindsToDefault();
                                    captureTarget = CaptureTarget.NONE;
                                    refreshButtonLabels();
                                })
                        .dimensions(footerStartX, footerY, footerButtonWidth, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions(
                                footerStartX + footerButtonWidth + footerGap,
                                footerY,
                                footerButtonWidth,
                                20)
                        .build());

        refreshButtonLabels();
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (captureTarget != CaptureTarget.NONE) {
            if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
                captureTarget = CaptureTarget.NONE;
                refreshButtonLabels();
                return true;
            }

            if (captureTarget == CaptureTarget.GANG) {
                runtime.bindGangPingKey(keyInput);
            } else if (captureTarget == CaptureTarget.TRUCE) {
                runtime.bindTrucePingKey(keyInput);
            }

            captureTarget = CaptureTarget.NONE;
            refreshButtonLabels();
            return true;
        }

        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(keyInput);
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
        drawContext.fill(0, 0, width, 32, 0x6B273D5C);
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.ping_keys.subtitle"),
                width / 2,
                36,
                0xFFB8C7DA);
        renderRows(drawContext);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.ping_keys.capture_hint"),
                width / 2,
                LIST_TOP + (ROW_HEIGHT * 2) + 6,
                0xFFC6D4E8);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void renderRows(DrawContext drawContext) {
        int rowWidth = Math.min(430, width - 32);
        int rowX = (width - rowWidth) / 2;

        renderRow(
                drawContext,
                rowX,
                LIST_TOP,
                rowWidth,
                Text.translatable("text.cosmicprisonsmod.ping_keys.gang_label"),
                0xFF406D97);
        renderRow(
                drawContext,
                rowX,
                LIST_TOP + ROW_HEIGHT,
                rowWidth,
                Text.translatable("text.cosmicprisonsmod.ping_keys.truce_label"),
                0xFF9A693A);
    }

    private void renderRow(
            DrawContext drawContext, int x, int y, int width, Text label, int accentColor) {
        int bottom = y + ROW_HEIGHT - 2;
        drawContext.fill(x, y, x + width, bottom, 0xB4131E30);
        drawContext.fill(x, y, x + width, y + 1, accentColor);
        drawContext.fill(x, bottom - 1, x + width, bottom, 0xFF25364D);
        drawContext.drawTextWithShadow(textRenderer, label, x + 9, y + 9, 0xFFE6EEF8);
    }

    private void beginCapture(CaptureTarget target) {
        captureTarget = target;
        refreshButtonLabels();
    }

    private void refreshButtonLabels() {
        if (gangKeyButton == null || truceKeyButton == null) {
            return;
        }

        gangKeyButton.setMessage(
                bindingButtonText(CaptureTarget.GANG, runtime.gangPingKeybindLabel()));
        truceKeyButton.setMessage(
                bindingButtonText(CaptureTarget.TRUCE, runtime.trucePingKeybindLabel()));
    }

    private Text bindingButtonText(CaptureTarget target, Text currentBinding) {
        if (captureTarget == target) {
            return Text.translatable("text.cosmicprisonsmod.ping_keys.waiting");
        }

        return currentBinding;
    }

    private enum CaptureTarget {
        NONE,
        GANG,
        TRUCE
    }
}
