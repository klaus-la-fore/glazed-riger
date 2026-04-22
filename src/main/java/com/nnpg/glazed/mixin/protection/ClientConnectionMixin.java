package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.ClientSpoofer;
import com.nnpg.glazed.protection.PacketContext;
import com.nnpg.glazed.protection.TranslationProtectionHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts packet handling to mark content created during lazy deserialization.
 * Layer 1 of Sign Translation Exploit protection - Packet Context Tracking.
 * 
 * Wraps Packet.apply() to set the processing flag and packet name during handling.
 */
@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @WrapOperation(
        method = "handlePacket",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V")
    )
    private static <T extends PacketListener> void glazed$wrapApply(
            Packet<T> packet, 
            T listener,
            Operation<Void> original) {
        PacketContext.setPacketName(packet);
        PacketContext.setProcessingPacket(true);
        try {
            original.call(packet, listener);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
    
    /**
     * Intercept outgoing packets to spoof channels.
     * Blocks custom payload C2S packets for non-vanilla channels (mod channels).
     */
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void glazed$onSend(Packet<?> packet, CallbackInfo ci) {
        // Use reflection to avoid mapping issues with CustomPayload and its ID in 1.21.4
        if (packet.getClass().getName().contains("CustomPayloadC2SPacket")) {
            try {
                java.lang.reflect.Method payloadMethod = packet.getClass().getMethod("payload");
                Object payload = payloadMethod.invoke(packet);
                
                java.lang.reflect.Method idAccessor = payload.getClass().getMethod("id");
                Object idObj = idAccessor.invoke(payload);
                
                // If id() returned an Identifier directly, or a record with an id() accessor
                Identifier id;
                if (idObj instanceof Identifier) {
                    id = (Identifier) idObj;
                } else {
                    java.lang.reflect.Method idMethod = idObj.getClass().getMethod("id");
                    id = (Identifier) idMethod.invoke(idObj);
                }
                
                if (ClientSpoofer.shouldBlockPayload(id)) {
                    ci.cancel();
                }
            } catch (Throwable t) {
                // If anything fails, safest is to NOT cancel to avoid breaking vanilla functionality
            }
        }
    }
}
