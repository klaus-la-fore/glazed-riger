package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.PacketContext;
import com.nnpg.glazed.protection.TranslationProtectionHandler;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts main-thread packet processing to maintain protection context.
 * Layer 1 of Sign Translation Exploit protection - Packet Context Tracking.
 * 
 * This is CRITICAL for exploits like Sign Translation, as sign text is often 
 * resolved during NBT processing on the main thread, not during initial decoding.
 */
@Mixin(NetworkThreadUtils.class)
public class PacketUtilsMixin {

    /**
     * Wrap the execution of the main-thread task to set the processing flag.
     * This ensures that any TranslatableTextContent created during the task 
     * (e.g. from sign NBT) is correctly tagged as coming from a packet.
     */
    @WrapOperation(
        method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/ThreadExecutor;execute(Ljava/lang/Runnable;)V"),
        require = 0
    )
    private static void glazed$wrapForceMainThread(ThreadExecutor<?> engine, Runnable task, Operation<Void> original, 
                                                  Packet<?> packet, PacketListener listener) {
        // We wrap the task to set the context flag during its execution on the main thread
        original.call(engine, (Runnable) () -> {
            PacketContext.setPacketName(packet.getClass().getSimpleName());
            PacketContext.setProcessingPacket(true);
            try {
                task.run();
            } finally {
                PacketContext.setProcessingPacket(false);
            }
        });
    }
}
