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
import java.util.Map;

/**
 * Tracks translation keys from language files by source.
 * CRITICAL component for Sign Translation Exploit protection.
 * 
 * This mixin intercepts language file loading to populate ModRegistry with:
 * - Vanilla translation keys (always allowed)
 * - Mod translation keys (blocked in exploit context)
 * - Server resource pack keys (allowed for vanilla resolution)
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
            LOGGER.debug("[Glazed Protection] Starting language load, clearing caches");
            glazed$loggedOnce = false;
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error clearing translation keys", t);
        }
    }
    
    /**
     * Mark initialization complete and track all loaded keys.
     * At this point, all language files have been loaded.
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
            TranslationStorage storage = cir.getReturnValue();
            if (storage != null) {
                // Access the translations map via accessor
                if (storage instanceof TranslationStorageAccessor accessor) {
                    Map<String, String> translations = accessor.glazed$getTranslations();
                    glazed$trackLoadedKeys(translations);
                }
            }
            
            ModRegistry.markInitialized();
            
            if (!glazed$loggedOnce) {
                glazed$loggedOnce = true;
                LOGGER.info("[Glazed Protection] Translation system initialized - {} vanilla keys, {} total keys tracked",
                    ModRegistry.getVanillaKeyCount(), ModRegistry.getTranslationKeyCount());
            }
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error in load complete", t);
        }
    }
    
    /**
     * Track all loaded keys by analyzing the translations map.
     * This identifies vanilla vs mod vs server pack keys based on key patterns.
     */
    @Unique
    private static void glazed$trackLoadedKeys(Map<String, String> translations) {
        try {
            // Track all keys currently in the translations map
            for (String key : translations.keySet()) {
                // If we're loading a server pack, mark these as server pack keys
                if (ModRegistry.isLoadingServerPack()) {
                    ModRegistry.recordServerPackKey(key);
                    LOGGER.debug("[Glazed Protection] Tracked server pack key: {}", key);
                }
                // Determine if this is a vanilla key by checking patterns
                else if (glazed$isVanillaKey(key)) {
                    ModRegistry.recordVanillaTranslationKey(key);
                } else {
                    // Try to determine the mod ID from the key
                    String modId = glazed$extractModId(key);
                    if (modId != null) {
                        ModRegistry.recordTranslationKey(modId, key);
                    }
                }
            }
            
            LOGGER.debug("[Glazed Protection] Tracked {} translation keys from language files", 
                translations.size());
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error tracking loaded keys", t);
        }
    }
    
    /**
     * Determine if a translation key is vanilla based on common patterns.
     */
    @Unique
    private static boolean glazed$isVanillaKey(String key) {
        if (key == null) return false;
        
        // Common vanilla prefixes
        return key.startsWith("key.") && !key.contains("-") && !key.contains("_") 
            || key.startsWith("gui.") && !key.contains("-")
            || key.startsWith("menu.")
            || key.startsWith("options.")
            || key.startsWith("chat.")
            || key.startsWith("commands.")
            || key.startsWith("block.minecraft.")
            || key.startsWith("item.minecraft.")
            || key.startsWith("entity.minecraft.")
            || key.startsWith("biome.minecraft.")
            || key.startsWith("enchantment.minecraft.")
            || key.startsWith("effect.minecraft.")
            || key.startsWith("container.")
            || key.startsWith("death.")
            || key.startsWith("gameMode.")
            || key.startsWith("selectWorld.")
            || key.startsWith("createWorld.")
            || key.startsWith("multiplayer.")
            || key.startsWith("lanServer.")
            || key.startsWith("advMode.")
            || key.startsWith("narrator.")
            || key.startsWith("subtitles.")
            || key.startsWith("language.")
            || key.startsWith("resourcePack.")
            || key.startsWith("dataPack.")
            || key.startsWith("tutorial.")
            || key.startsWith("demo.")
            || key.startsWith("disconnect.")
            || key.startsWith("book.")
            || key.startsWith("sign.")
            || key.startsWith("filled_map.")
            || key.startsWith("structure_block.")
            || key.startsWith("jigsaw_block.")
            || key.startsWith("argument.")
            || key.startsWith("parsing.")
            || key.startsWith("color.minecraft.")
            || key.startsWith("stat.")
            || key.startsWith("controls.")
            || key.startsWith("attribute.name.")
            || key.startsWith("attribute.modifier.")
            || key.startsWith("gamerule.")
            || key.startsWith("difficulty.")
            || key.startsWith("potion.")
            || key.startsWith("recipe.")
            || key.startsWith("advancements.")
            || key.startsWith("translation.")
            || key.startsWith("pack.source.")
            || key.startsWith("pack.nameAndSource")
            || key.startsWith("soundCategory.")
            || key.startsWith("title.")
            || key.startsWith("screenshot.")
            || key.startsWith("mco.")
            || key.startsWith("realms.")
            || key.startsWith("telemetry.")
            || key.startsWith("accessibility.")
            || key.startsWith("editGamerule.")
            || key.startsWith("spectatorMenu.")
            || key.startsWith("record.")
            || key.startsWith("instrument.")
            || key.startsWith("painting.")
            || key.startsWith("trim_");
    }
    
    /**
     * Extract mod ID from a translation key.
     * Most mod keys follow the pattern: "category.modid.name" or "modid.category.name"
     */
    @Unique
    private static String glazed$extractModId(String key) {
        if (key == null || !key.contains(".")) return null;
        
        String[] parts = key.split("\\.");
        if (parts.length < 2) return null;
        
        // Common patterns:
        // "key.meteor-client.open-gui" -> "meteor-client"
        // "gui.xaero_minimap.settings" -> "xaero_minimap"
        // "meteor-client.category.name" -> "meteor-client"
        
        // Check second part first (most common)
        String candidate = parts[1];
        if (candidate.contains("-") || candidate.contains("_")) {
            return candidate;
        }
        
        // Check first part
        candidate = parts[0];
        if (candidate.contains("-") || candidate.contains("_")) {
            return candidate;
        }
        
        // Fallback: use second part if it's not a vanilla category
        if (!glazed$isVanillaCategory(parts[0])) {
            return parts[1];
        }
        
        return null;
    }
    
    /**
     * Check if a string is a vanilla category prefix.
     */
    @Unique
    private static boolean glazed$isVanillaCategory(String category) {
        return category.equals("key") || category.equals("gui") || category.equals("menu")
            || category.equals("options") || category.equals("chat") || category.equals("commands")
            || category.equals("block") || category.equals("item") || category.equals("entity")
            || category.equals("biome") || category.equals("enchantment") || category.equals("effect")
            || category.equals("container") || category.equals("death") || category.equals("gameMode")
            || category.equals("selectWorld") || category.equals("createWorld") || category.equals("multiplayer")
            || category.equals("lanServer") || category.equals("advMode") || category.equals("narrator")
            || category.equals("subtitles") || category.equals("language") || category.equals("resourcePack")
            || category.equals("dataPack") || category.equals("tutorial") || category.equals("demo")
            || category.equals("disconnect") || category.equals("book") || category.equals("sign")
            || category.equals("filled_map") || category.equals("structure_block") || category.equals("jigsaw_block")
            || category.equals("argument") || category.equals("parsing") || category.equals("color")
            || category.equals("stat") || category.equals("controls") || category.equals("attribute")
            || category.equals("gamerule") || category.equals("difficulty") || category.equals("potion")
            || category.equals("recipe") || category.equals("advancements") || category.equals("translation")
            || category.equals("pack") || category.equals("soundCategory") || category.equals("title")
            || category.equals("screenshot") || category.equals("mco") || category.equals("realms")
            || category.equals("telemetry") || category.equals("accessibility") || category.equals("editGamerule")
            || category.equals("spectatorMenu") || category.equals("record") || category.equals("instrument")
            || category.equals("painting") || category.equals("trim");
    }
}
