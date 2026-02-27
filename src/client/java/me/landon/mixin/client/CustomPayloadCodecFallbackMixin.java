package me.landon.mixin.client;

import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.companion.network.CompanionRawPayload;
import me.landon.companion.network.PayloadFallbackDecider;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.network.packet.CustomPayload$1")
public abstract class CustomPayloadCodecFallbackMixin<B extends PacketByteBuf> {
    @Inject(method = "getCodec", at = @At("HEAD"), cancellable = true)
    private void cosmicprisonsmod$overrideCodec(
            Identifier id,
            CallbackInfoReturnable<PacketCodec<? super B, ? extends CustomPayload>> cir) {
        CompanionClientRuntime runtime = CompanionClientRuntime.getInstance();

        if (!PayloadFallbackDecider.shouldOverrideCodec(runtime.isPayloadFallbackEnabled(), id)) {
            return;
        }

        @SuppressWarnings("unchecked")
        PacketCodec<? super B, ? extends CustomPayload> codec =
                (PacketCodec<? super B, ? extends CustomPayload>)
                        (PacketCodec<?, ?>) CompanionRawPayload.CODEC;
        cir.setReturnValue(codec);
    }
}
