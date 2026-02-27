package me.landon.mixin.client;

import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPeacefulMiningMixin {
    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void cosmicprisonsmod$applyPeacefulMiningPassThrough(
            float tickDelta, CallbackInfo callbackInfo) {
        CompanionClientRuntime.getInstance()
                .onCrosshairTargetUpdated(MinecraftClient.getInstance(), tickDelta);
    }
}
