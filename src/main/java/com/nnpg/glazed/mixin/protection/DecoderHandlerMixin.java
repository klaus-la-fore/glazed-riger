package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.PacketContext;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts packet decoding to mark content created during deserialization.
 * Layer 1 of Sign Translation Exploit protection - Packet Context Tracking.
 * 
 * Wraps PacketCodec.decode() to set the processing flag during eager deserialization.
 */
@Mixin(DecoderHandler.class)
public class DecoderHandlerMixin {

    @WrapOperation(
        method = "decode",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object glazed$wrapDecode(PacketCodec<?, ?> instance, Object buffer, Operation<Object> original) {
        PacketContext.setProcessingPacket(true);
        try {
            return original.call(instance, buffer);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}
