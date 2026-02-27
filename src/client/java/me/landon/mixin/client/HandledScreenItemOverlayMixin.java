package me.landon.mixin.client;

import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenItemOverlayMixin<T extends ScreenHandler> {
    @Shadow protected T handler;

    @Shadow protected int x;

    @Shadow protected int y;

    @Shadow private Slot lastClickedSlot;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void cosmicprisonsmod$drawInventoryItemOverlay(
            DrawContext drawContext, Slot slot, int mouseX, int mouseY, CallbackInfo callbackInfo) {
        CompanionClientRuntime.getInstance()
                .renderHandledScreenSlotOverlay(drawContext, slot, x, y);
    }

    @Inject(
            method =
                    "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"))
    private void cosmicprisonsmod$cacheDraggedOverlayFromClick(
            Slot slot,
            int slotId,
            int button,
            SlotActionType actionType,
            CallbackInfo callbackInfo) {
        CompanionClientRuntime.getInstance().rememberHandledScreenSlotClickOverlay(slot);
    }

    @Inject(
            method =
                    "onMouseClick(Lnet/minecraft/screen/slot/Slot;Lnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"))
    private void cosmicprisonsmod$cacheDraggedOverlayFromClickShort(
            Slot slot, SlotActionType actionType, CallbackInfo callbackInfo) {
        CompanionClientRuntime.getInstance().rememberHandledScreenSlotClickOverlay(slot);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cosmicprisonsmod$drawDraggedItemOverlay(
            DrawContext drawContext,
            int mouseX,
            int mouseY,
            float deltaTicks,
            CallbackInfo callbackInfo) {
        CompanionClientRuntime.getInstance()
                .renderHandledScreenCursorOverlay(
                        drawContext, handler, lastClickedSlot, mouseX, mouseY);
    }
}
