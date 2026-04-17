package com.nnpg.glazed.mixin.protection;

import com.nnpg.glazed.protection.ModRegistry;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Tracks translation keys from language files by source.
 * Layer 4 of Sign Translation Exploit protection - Alert & Logging.
 * 
 * This is a simplified version that tracks keys during the load process.
 * The actual key tracking happens in the TranslatableTextContentMixin when keys are resolved.
 */
@Mixin(TranslationStorage.class)
public class TranslationStorageMixin {
    
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");
    
    @Unique
    private static boolean glazed$loggedOnce = false;
    
    /**
     * Clear translation key caches before loading new language.
     */
    @Inject(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;", 
        at = @At("HEAD"),
        require = 0
    )
    private static void glazed$onLoadStart(
            ResourceManager resourceManager, 
            List<String> definitions,
            boolean rightToLeft, 
            CallbackInfoReturnable<TranslationStorage> cir) {
        try {
            ModRegistry.clearTranslationKeys();
            LOGGER.debug("[Glazed Protection] Starting language load");
            glazed$loggedOnce = false;
        } catch (Throwable t) {
            // Ignore errors during initialization
        }
    }
    
    /**
     * Mark initialization complete after loading.
     */
    @Inject(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;", 
        at = @At("RETURN"),
        require = 0
    )
    private static void glazed$onLoadComplete(
            ResourceManager resourceManager, 
            List<String> definitions,
            boolean rightToLeft, 
            CallbackInfoReturnable<TranslationStorage> cir) {
        try {
            ModRegistry.markInitialized();
            
            if (!glazed$loggedOnce) {
                glazed$loggedOnce = true;
                LOGGER.info("[Glazed Protection] Translation system initialized - Protection active");
            }
        } catch (Throwable t) {
            // Ignore errors during initialization
        }
    }
}
