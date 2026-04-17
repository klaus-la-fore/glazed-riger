package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.PacketContext;
import com.nnpg.glazed.protection.TranslationProtectionHandler;
import com.nnpg.glazed.protection.TranslationProtectionHandler.InterceptionType;
import com.nnpg.glazed.protection.ModRegistry;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Intercepts TranslatableTextContent to block mod translation resolution.
 * Core component of Sign Translation Exploit protection - Layer 3.
 * 
 * Blocks resolution of mod translation keys when they come from network packets,
 * preventing servers from detecting installed mods.
 */
@Mixin(value = TranslatableTextContent.class, priority = 1500)
public abstract class TranslatableTextContentMixin {

    @Shadow @Final private String key;
    @Shadow @Final private String fallback;

    @Unique
    private boolean glazed$fromPacket = false;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("TAIL"), require = 0)
    private void glazed$tagFromPacket(String key, String fallback, Object[] args, CallbackInfo ci) {
        try {
            this.glazed$fromPacket = PacketContext.isProcessingPacket();
        } catch (Throwable t) {
            // Ignore during early initialization
            this.glazed$fromPacket = false;
        }
    }

    /** Sentinel value indicating the original call should proceed. */
    @Unique
    private static final String GLAZED_ALLOW_ORIGINAL = "\0__glazed_allow__";

    /**
     * Wrap the Language.get(String, String) call in updateTranslations().
     * This is the ONLY method that needs interception since get(String) internally calls get(String, String).
     */
    @WrapOperation(
        method = "updateTranslations",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        require = 0
    )
    private String glazed$wrapGet(Language instance, String keyArg, String fallbackArg, Operation<String> original) {
        // Early exit if not from packet - avoid any class loading
        if (!this.glazed$fromPacket) {
            return original.call(instance, keyArg, fallbackArg);
        }
        
        String result = glazed$handleTranslationLookup(keyArg, fallbackArg);
        if (result == GLAZED_ALLOW_ORIGINAL) {
            return original.call(instance, keyArg, fallbackArg);
        }
        return result;
    }

    /**
     * Shared handler for translation lookup interception.
     */
    @Unique
    private String glazed$handleTranslationLookup(String translationKey, String defaultValue) {
        // Safety check: Don't intercept during early initialization
        try {
            // Not from a packet or in singleplayer — allow normal resolution
            if (!this.glazed$fromPacket || glazed$isIntegratedServerRunning()) {
                return GLAZED_ALLOW_ORIGINAL;
            }
        } catch (Throwable t) {
            // During early initialization, allow everything
            return GLAZED_ALLOW_ORIGINAL;
        }

        // In exploit context — always notify
        TranslationProtectionHandler.notifyExploitDetected();

        // Always allow vanilla keys
        if (ModRegistry.isVanillaTranslationKey(translationKey)) {
            return GLAZED_ALLOW_ORIGINAL;
        }

        // Allow server resource pack keys
        if (ModRegistry.isServerPackTranslationKey(translationKey)) {
            return GLAZED_ALLOW_ORIGINAL;
        }

        // Block all mod keys - return fallback value
        String blockedValue = defaultValue;
        glazed$logBlocked(translationKey, blockedValue);
        return blockedValue;
    }
    
    /**
     * Check if integrated server is running without triggering early class loading.
     */
    @Unique
    private static boolean glazed$isIntegratedServerRunning() {
        try {
            // Delay class loading until runtime
            return net.minecraft.client.MinecraftClient.getInstance().isIntegratedServerRunning();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Log detection when a mod translation key is blocked.
     */
    @Unique
    private void glazed$logBlocked(String translationKey, String defaultValue) {
        String originalValue = glazed$getRealTranslation(translationKey, defaultValue);

        if (!originalValue.equals(defaultValue)) {
            TranslationProtectionHandler.sendDetail(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
        }
        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
    }

    /**
     * Get the real translation value by directly accessing TranslationStorage's translations map.
     */
    @Unique
    private String glazed$getRealTranslation(String translationKey, String defaultValue) {
        try {
            Language lang = Language.getInstance();
            if (lang instanceof TranslationStorageAccessor accessor) {
                Map<String, String> translations = accessor.glazed$getTranslations();
                String value = translations.get(translationKey);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {
            // Fallback to default
        }
        return defaultValue;
    }
}
