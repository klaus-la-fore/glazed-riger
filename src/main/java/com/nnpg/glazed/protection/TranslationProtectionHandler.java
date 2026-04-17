package com.nnpg.glazed.protection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized handler for key resolution protection alerts.
 * Permanently active protection against Sign Translation Exploit.
 */
public class TranslationProtectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    public enum InterceptionType {
        TRANSLATION("Translation"),
        KEYBIND("Keybind");

        private final String displayName;
        InterceptionType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private record AlertDedupeKey(InterceptionType type, String keyName) {}
    private record LogDedupeKey(InterceptionType type, String packetName, String keyName, String originalValue, String spoofedValue) {}

    private static final Set<AlertDedupeKey> alertedKeys = ConcurrentHashMap.newKeySet();
    private static final Set<LogDedupeKey> loggedKeys = ConcurrentHashMap.newKeySet();

    private static final int MAX_DEDUPE_ENTRIES = 500;

    private static volatile long lastHeaderTime = 0;
    private static volatile boolean headerPending = false;

    private static final long HEADER_COOLDOWN_MS = 5000;

    private TranslationProtectionHandler() {}

    public static void notifyExploitDetected() {
        long now = System.currentTimeMillis();

        if (now - lastHeaderTime < HEADER_COOLDOWN_MS) {
            return;
        }

        headerPending = true;
    }

    private static void emitHeader() {
        String source = PacketContext.getPacketName();

        MinecraftClient mc = MinecraftClient.getInstance();
        Runnable sendAlert = () -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                    Text.literal("[Glazed Protection] ").formatted(Formatting.DARK_PURPLE)
                        .append(Text.literal("Key resolution probe detected").formatted(Formatting.RED)),
                    false);
            }
        };
        if (mc.isOnThread()) {
            sendAlert.run();
        } else {
            mc.execute(sendAlert);
        }

        LOGGER.info("[Glazed Protection] Key resolution exploit detected via {}", source);
    }

    public static void sendDetail(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        if (alertedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            alertedKeys.clear();
        }

        if (!alertedKeys.add(new AlertDedupeKey(type, keyName))) {
            return;
        }

        if (headerPending) {
            headerPending = false;
            lastHeaderTime = System.currentTimeMillis();
            emitHeader();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                    Text.literal("[" + keyName + "] '" + originalValue + "'→'" + spoofedValue + "'")
                        .formatted(Formatting.DARK_GRAY),
                    false);
            }
        });
    }

    public static void sendDetailDebug(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        // Debug mode not implemented in simplified version
    }

    public static void logDetection(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        String packetName = PacketContext.getPacketName();

        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }

        if (!loggedKeys.add(new LogDedupeKey(type, packetName, keyName, originalValue, spoofedValue))) {
            return;
        }

        LOGGER.info("[{}:{}] '{}' '{}' -> '{}'",
            type.getDisplayName(), packetName, keyName, originalValue, spoofedValue);
    }

    public static void clearDedup() {
        alertedKeys.clear();
        loggedKeys.clear();
        headerPending = false;
    }

    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
        lastHeaderTime = 0;
        headerPending = false;
    }
}
