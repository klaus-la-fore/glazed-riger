package com.nnpg.glazed.protection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking vanilla vs mod translation keys and keybinds.
 * Used by the protection system to determine what to block.
 */
public class ModRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");
    
    /** Vanilla translation keys (always allowed) */
    private static final Set<String> vanillaTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** Vanilla keybinds (always allowed) */
    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();
    
    /** Server resource pack translation keys (allowed for vanilla resolution) */
    private static final Set<String> serverPackKeys = ConcurrentHashMap.newKeySet();
    
    /** All known translation keys */
    private static final Set<String> allKnownTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** Reverse index: translation key -> mod ID */
    private static final Map<String, String> translationKeyToModId = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;
    
    private ModRegistry() {}
    
    // ==================== TRANSLATION KEY TRACKING ====================
    
    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;
        allKnownTranslationKeys.add(key);
        translationKeyToModId.put(key, modId);
    }
    
    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;
        vanillaTranslationKeys.add(key);
        allKnownTranslationKeys.add(key);
    }
    
    public static void recordServerPackKey(String key) {
        if (key == null) return;
        serverPackKeys.add(key);
        allKnownTranslationKeys.add(key);
    }
    
    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }
    
    public static boolean isServerPackTranslationKey(String key) {
        return key != null && serverPackKeys.contains(key);
    }

    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        return translationKeyToModId.get(key);
    }

    public static void clearTranslationKeys() {
        vanillaTranslationKeys.clear();
        serverPackKeys.clear();
        allKnownTranslationKeys.clear();
        translationKeyToModId.clear();
        LOGGER.debug("[ModRegistry] Cleared translation key cache");
    }
    
    // ==================== KEYBIND TRACKING ====================
    
    public static void recordVanillaKeybind(String keybindName) {
        if (keybindName == null) return;
        vanillaKeybinds.add(keybindName);
    }
    
    public static boolean isVanillaKeybind(String keybindName) {
        return keybindName != null && vanillaKeybinds.contains(keybindName);
    }
    
    // ==================== INITIALIZATION ====================
    
    public static void markInitialized() {
        initialized = true;
        LOGGER.debug("[ModRegistry] Initialized with {} translation keys",
            allKnownTranslationKeys.size());
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    // ==================== STATISTICS ====================
    
    public static int getVanillaKeyCount() {
        return vanillaTranslationKeys.size();
    }
    
    public static int getServerPackKeyCount() {
        return serverPackKeys.size();
    }
    
    public static int getTranslationKeyCount() {
        return allKnownTranslationKeys.size();
    }
}
