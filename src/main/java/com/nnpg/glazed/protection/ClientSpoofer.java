package com.nnpg.glazed.protection;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Handles channel filtering logic.
 * Ensures mod-specific network channels are blocked to prevent fingerprinting.
 */
public class ClientSpoofer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");
    
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    
    // Channels that are always allowed (vanilla/base)
    private static final Set<String> ALLOWED_CHANNELS = Set.of(
        "minecraft:brand",
        "minecraft:client_information",
        "minecraft:register",
        "minecraft:unregister"
    );

    private ClientSpoofer() {}

    /**
     * Determines if a custom payload channel should be blocked.
     * 
     * @param id The identifier of the payload channel.
     * @return true if the channel should be blocked to maintain privacy.
     */
    public static boolean shouldBlockPayload(Identifier id) {
        if (id == null) return false;
        
        String channel = id.toString();
        String namespace = id.getNamespace();

        // Always allow essential vanilla/base channels
        if (ALLOWED_CHANNELS.contains(channel)) {
            return false;
        }

        // Block all non-minecraft namespaces to hide mod presence from server probes
        if (!MINECRAFT_NAMESPACE.equals(namespace)) {
            LOGGER.info("[Glazed Protection] Blocking mod channel: {}", channel);
            return true;
        }

        return false;
    }
}
