package com.nnpg.glazed.mixin.protection;

import com.nnpg.glazed.protection.TranslationProtectionHandler;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles session-based protection state.
 * Clears deduplication caches when joining or leaving a server to ensure 
 * fresh alerts for each session while preventing spam within a session.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class SessionProtectionMixin {

    /**
     * Clear cache on disconnect.
     * In 1.21.4, the parameter is DisconnectionInfo.
     */
    @Inject(method = "onDisconnected", at = @At("HEAD"), require = 0)
    private void glazed$onDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        TranslationProtectionHandler.clearCache();
    }

    /**
     * Clear cache on game join.
     */
    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class JoinMixin {
        @Inject(method = "onGameJoin", at = @At("HEAD"), require = 0)
        private void glazed$onJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
            TranslationProtectionHandler.clearCache();
        }
    }
}
