package me.landon.mixin.client;

import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.client.runtime.PeacefulMiningRenderStateKeys;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPeacefulMiningGhostMixin {
    @Inject(
            method =
                    "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void cosmicprisonsmod$applyPeacefulMiningGhost(
            LivingEntity entity,
            LivingEntityRenderState state,
            float tickDelta,
            CallbackInfo callbackInfo) {
        boolean ghosted =
                CompanionClientRuntime.getInstance().isPeacefulMiningGhostedEntity(entity.getId());
        state.setData(PeacefulMiningRenderStateKeys.GHOSTED, ghosted);

        if (!ghosted) {
            return;
        }

        state.invisible = true;
        state.invisibleToPlayer = false;
    }
}
