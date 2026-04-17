package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.PacketContext;
import com.nnpg.glazed.protection.TranslationProtectionHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
        TranslationProtectionHandler.clearDedup();
        PacketContext.setPacketName(packet);
        PacketContext.setProcessingPacket(true);
        try {
            original.call(packet, listener);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}
