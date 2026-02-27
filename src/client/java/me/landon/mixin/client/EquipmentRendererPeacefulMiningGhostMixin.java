package me.landon.mixin.client;

import me.landon.client.runtime.PeacefulMiningRenderStateKeys;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererPeacefulMiningGhostMixin {
    @Unique private static final int GHOST_ALPHA = 0x26;
    @Unique private boolean cosmicprisonsmod$ghostArmorRenderActive;

    @Inject(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At("HEAD"))
    private void cosmicprisonsmod$captureGhostArmorContext(
            EquipmentModel.LayerType layerType,
            RegistryKey<?> assetId,
            Model<?> model,
            Object renderState,
            ItemStack stack,
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            Identifier playerTexture,
            int outlineColor,
            int layerIndex,
            CallbackInfo callbackInfo) {
        cosmicprisonsmod$ghostArmorRenderActive =
                renderState instanceof FabricRenderState fabricRenderState
                        && Boolean.TRUE.equals(
                                fabricRenderState.getData(PeacefulMiningRenderStateKeys.GHOSTED));
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At("RETURN"))
    private void cosmicprisonsmod$clearGhostArmorContext(
            EquipmentModel.LayerType layerType,
            RegistryKey<?> assetId,
            Model<?> model,
            Object renderState,
            ItemStack stack,
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            Identifier playerTexture,
            int outlineColor,
            int layerIndex,
            CallbackInfo callbackInfo) {
        cosmicprisonsmod$ghostArmorRenderActive = false;
    }

    @ModifyArg(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 0),
            index = 6)
    private int cosmicprisonsmod$applyGhostAlphaPrimaryArmorLayer(int color) {
        return applyGhostAlpha(color);
    }

    @ModifyArg(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 1),
            index = 6)
    private int cosmicprisonsmod$applyGhostAlphaGlintArmorLayer(int color) {
        return applyGhostAlpha(color);
    }

    @ModifyArg(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 2),
            index = 6)
    private int cosmicprisonsmod$applyGhostAlphaTrimArmorLayer(int color) {
        return applyGhostAlpha(color);
    }

    @Unique
    private int applyGhostAlpha(int color) {
        if (!cosmicprisonsmod$ghostArmorRenderActive) {
            return color;
        }

        return (GHOST_ALPHA << 24) | (color & 0x00FFFFFF);
    }
}
