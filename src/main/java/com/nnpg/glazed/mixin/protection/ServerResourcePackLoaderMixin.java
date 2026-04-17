package com.nnpg.glazed.mixin.protection;

import com.nnpg.glazed.protection.ModRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.resource.ResourcePackProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Tracks when server resource packs are loaded.
 * This allows us to mark keys from server packs as "safe" to resolve.
 */
@Mixin(ServerResourcePackLoader.class)
public class ServerResourcePackLoaderMixin {
    
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");
    
    /**
     * Called when server resource packs are loaded.
     * We mark this event so TranslationStorageMixin can identify server pack keys.
     */
    @Inject(
        method = "loadServerPack",
        at = @At("HEAD"),
        require = 0
    )
    private static void glazed$onServerPackLoad(
            ResourcePackProfile profile,
            List<ResourcePackProfile> profiles,
            CallbackInfo ci) {
        try {
            LOGGER.debug("[Glazed Protection] Server resource pack loading: {}", 
                profile != null ? profile.getId() : "unknown");
            ModRegistry.markServerPackLoading(true);
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error marking server pack load", t);
        }
    }
    
    /**
     * Called after server resource packs are loaded.
     */
    @Inject(
        method = "loadServerPack",
        at = @At("RETURN"),
        require = 0
    )
    private static void glazed$afterServerPackLoad(
            ResourcePackProfile profile,
            List<ResourcePackProfile> profiles,
            CallbackInfo ci) {
        try {
            ModRegistry.markServerPackLoading(false);
            LOGGER.info("[Glazed Protection] Server resource pack loaded, keys will be tracked as server pack keys");
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error after server pack load", t);
        }
    }
}
