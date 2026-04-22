package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * DwellEntitiesESP - Combined ESP for Llama, Pillager, Villager and Wandering Trader.
 *
 * Surface-spawning entities (Llama, Pillager, Wandering Trader) are indicators of
 * prolonged area loading and can hint at underground bases below the surface.
 * Villagers directly indicate above-ground or underground bases.
 */
public class DwellEntitiesESP extends Module {

    // ─────────────────────────────────────────────
    //  Setting Groups
    // ─────────────────────────────────────────────
    private final SettingGroup sgGeneral        = settings.getDefaultGroup();

    // Feature toggles
    private final SettingGroup sgFeatures       = settings.createGroup("Features");

    // Per-entity groups
    private final SettingGroup sgLlama          = settings.createGroup("Llama");
    private final SettingGroup sgPillager       = settings.createGroup("Pillager");
    private final SettingGroup sgVillager       = settings.createGroup("Villager");
    private final SettingGroup sgWandering      = settings.createGroup("Wandering Trader");

    // Shared webhook group
    private final SettingGroup sgWebhook        = settings.createGroup("Webhook");

    // ─────────────────────────────────────────────
    //  General Settings
    // ─────────────────────────────────────────────
    private final Setting<NotificationMode> notificationMode = sgGeneral.add(new EnumSetting.Builder<NotificationMode>()
        .name("notification-mode")
        .description("How to notify when entities are detected")
        .defaultValue(NotificationMode.Both)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when any entity is detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when any entity is detected")
        .defaultValue(false)
        .build()
    );

    // ─────────────────────────────────────────────
    //  Feature Toggles
    // ─────────────────────────────────────────────
    private final Setting<Boolean> llamaEnabled = sgFeatures.add(new BoolSetting.Builder()
        .name("llama-esp")
        .description("Enable Llama detection (surface spawn = underground base hint)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pillagerEnabled = sgFeatures.add(new BoolSetting.Builder()
        .name("pillager-esp")
        .description("Enable Pillager detection (surface spawn = underground base hint)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> villagerEnabled = sgFeatures.add(new BoolSetting.Builder()
        .name("villager-esp")
        .description("Enable Villager / Zombie Villager detection (base indicator)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wanderingEnabled = sgFeatures.add(new BoolSetting.Builder()
        .name("wandering-trader-esp")
        .description("Enable Wandering Trader detection (surface spawn = underground base hint)")
        .defaultValue(true)
        .build()
    );

    // ─────────────────────────────────────────────
    //  Llama Settings
    // ─────────────────────────────────────────────
    private final Setting<Boolean> llamaShowTracers = sgLlama.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to llamas")
        .defaultValue(true)
        .visible(llamaEnabled::get)
        .build()
    );

    private final Setting<SettingColor> llamaTracerColor = sgLlama.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer lines for llamas")
        .defaultValue(new SettingColor(255, 165, 0, 127))
        .visible(() -> llamaEnabled.get() && llamaShowTracers.get())
        .build()
    );

    // ─────────────────────────────────────────────
    //  Pillager Settings
    // ─────────────────────────────────────────────
    private final Setting<Integer> pillagerMaxDistance = sgPillager.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render pillagers")
        .defaultValue(128)
        .range(16, 256)
        .sliderRange(16, 256)
        .visible(pillagerEnabled::get)
        .build()
    );

    private final Setting<SettingColor> pillagerEspColor = sgPillager.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of the pillager ESP box")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(pillagerEnabled::get)
        .build()
    );

    private final Setting<ShapeMode> pillagerShapeMode = sgPillager.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the ESP shapes are rendered for pillagers")
        .defaultValue(ShapeMode.Both)
        .visible(pillagerEnabled::get)
        .build()
    );

    private final Setting<Boolean> pillagerTracersEnabled = sgPillager.add(new BoolSetting.Builder()
        .name("tracers-enabled")
        .description("Enable tracers to pillagers")
        .defaultValue(true)
        .visible(pillagerEnabled::get)
        .build()
    );

    private final Setting<SettingColor> pillagerTracerColor = sgPillager.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of tracers to pillagers")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> pillagerEnabled.get() && pillagerTracersEnabled.get())
        .build()
    );

    private final Setting<TracersMode> pillagerTracersMode = sgPillager.add(new EnumSetting.Builder<TracersMode>()
        .name("tracers-mode")
        .description("How pillager tracers are rendered")
        .defaultValue(TracersMode.Line)
        .visible(() -> pillagerEnabled.get() && pillagerTracersEnabled.get())
        .build()
    );

    private final Setting<Boolean> pillagerShowCount = sgPillager.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show pillager count in chat when it changes")
        .defaultValue(true)
        .visible(pillagerEnabled::get)
        .build()
    );

    // ─────────────────────────────────────────────
    //  Villager Settings
    // ─────────────────────────────────────────────
    private final Setting<VillagerDetectionMode> villagerDetectionMode = sgVillager.add(new EnumSetting.Builder<VillagerDetectionMode>()
        .name("detection-mode")
        .description("What type of villagers to detect")
        .defaultValue(VillagerDetectionMode.Both)
        .visible(villagerEnabled::get)
        .build()
    );

    private final Setting<Boolean> villagerShowTracers = sgVillager.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to villagers")
        .defaultValue(true)
        .visible(villagerEnabled::get)
        .build()
    );

    private final Setting<SettingColor> villagerTracerColor = sgVillager.add(new ColorSetting.Builder()
        .name("villager-tracer-color")
        .description("Color of the tracer lines for regular villagers")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(() -> villagerEnabled.get() && villagerShowTracers.get() &&
            (villagerDetectionMode.get() == VillagerDetectionMode.Villagers || villagerDetectionMode.get() == VillagerDetectionMode.Both))
        .build()
    );

    private final Setting<SettingColor> zombieVillagerTracerColor = sgVillager.add(new ColorSetting.Builder()
        .name("zombie-villager-tracer-color")
        .description("Color of the tracer lines for zombie villagers")
        .defaultValue(new SettingColor(255, 0, 0, 127))
        .visible(() -> villagerEnabled.get() && villagerShowTracers.get() &&
            (villagerDetectionMode.get() == VillagerDetectionMode.ZombieVillagers || villagerDetectionMode.get() == VillagerDetectionMode.Both))
        .build()
    );

    // ─────────────────────────────────────────────
    //  Wandering Trader Settings
    // ─────────────────────────────────────────────
    private final Setting<Boolean> wanderingShowTracers = sgWandering.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to wandering traders")
        .defaultValue(true)
        .visible(wanderingEnabled::get)
        .build()
    );

    private final Setting<SettingColor> wanderingTracerColor = sgWandering.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer lines for wandering traders")
        .defaultValue(new SettingColor(0, 191, 255, 127))
        .visible(() -> wanderingEnabled.get() && wanderingShowTracers.get())
        .build()
    );

    // ─────────────────────────────────────────────
    //  Shared Webhook Settings
    // ─────────────────────────────────────────────
    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Send webhook notifications when entities are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );

    // ─────────────────────────────────────────────
    //  State
    // ─────────────────────────────────────────────
    private final Set<Integer> detectedLlamas    = new HashSet<>();
    private final Set<Integer> detectedPillagers = new HashSet<>();
    private final Set<Integer> detectedVillagers = new HashSet<>();
    private final Set<Integer> detectedTraders   = new HashSet<>();

    private final List<PillagerEntity> pillagerList = new ArrayList<>();
    private int lastPillagerCount = 0;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // ─────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────
    public DwellEntitiesESP() {
        super(GlazedAddon.esp, "dwell-entities-esp",
            "Detects Llamas, Pillagers, Villagers and Wandering Traders. " +
            "Surface-spawning entities hint at underground bases below.");
    }

    // ─────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────
    @Override
    public void onActivate() {
        clearAll();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        detectedLlamas.clear();
        detectedPillagers.clear();
        detectedVillagers.clear();
        detectedTraders.clear();
        pillagerList.clear();
        lastPillagerCount = 0;
    }

    // ─────────────────────────────────────────────
    //  Render Loop
    // ─────────────────────────────────────────────
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<Integer> currentLlamas    = new HashSet<>();
        Set<Integer> currentPillagers = new HashSet<>();
        Set<Integer> currentVillagers = new HashSet<>();
        Set<Integer> currentTraders   = new HashSet<>();

        pillagerList.clear();
        int villagerCount = 0;
        int zombieVillagerCount = 0;

        for (Entity entity : mc.world.getEntities()) {

            // ── Llama ──
            if (llamaEnabled.get() && entity instanceof LlamaEntity llama) {
                currentLlamas.add(entity.getId());
                if (llamaShowTracers.get()) {
                    drawTracer(event, entity, llama.getBoundingBox(), new Color(llamaTracerColor.get()));
                }
            }

            // ── Pillager ──
            else if (pillagerEnabled.get() && entity instanceof PillagerEntity pillager) {
                double dist = mc.player.distanceTo(pillager);
                if (dist <= pillagerMaxDistance.get()) {
                    currentPillagers.add(entity.getId());
                    pillagerList.add(pillager);
                    renderPillager(pillager, event);
                }
            }

            // ── Villager / Zombie Villager ──
            else if (villagerEnabled.get()) {
                Color tracerColor = null;

                if (entity instanceof VillagerEntity &&
                    (villagerDetectionMode.get() == VillagerDetectionMode.Villagers ||
                     villagerDetectionMode.get() == VillagerDetectionMode.Both)) {
                    tracerColor = new Color(villagerTracerColor.get());
                    villagerCount++;
                } else if (entity instanceof ZombieVillagerEntity &&
                    (villagerDetectionMode.get() == VillagerDetectionMode.ZombieVillagers ||
                     villagerDetectionMode.get() == VillagerDetectionMode.Both)) {
                    tracerColor = new Color(zombieVillagerTracerColor.get());
                    zombieVillagerCount++;
                }

                if (tracerColor != null) {
                    currentVillagers.add(entity.getId());
                    if (villagerShowTracers.get()) {
                        drawTracer(event, entity, entity.getBoundingBox(), tracerColor);
                    }
                }
            }

            // ── Wandering Trader ──
            else if (wanderingEnabled.get() && entity instanceof WanderingTraderEntity trader) {
                currentTraders.add(entity.getId());
                if (wanderingShowTracers.get()) {
                    drawTracer(event, entity, trader.getBoundingBox(), new Color(wanderingTracerColor.get()));
                }
            }
        }

        // ── Pillager count notification ──
        if (pillagerEnabled.get() && pillagerShowCount.get() && pillagerList.size() != lastPillagerCount) {
            if (!pillagerList.isEmpty()) {
                String msg = "Found " + pillagerList.size() + " pillager(s) nearby";
                sendNotification(msg, Items.CROSSBOW);
            }
            lastPillagerCount = pillagerList.size();
        }

        // ── New-entity detection & actions ──
        checkNewEntities(currentLlamas,    detectedLlamas,    "Llama",            llamaEnabled.get());
        checkNewPillagers(currentPillagers);
        checkNewVillagers(currentVillagers, villagerCount, zombieVillagerCount);
        checkNewEntities(currentTraders,   detectedTraders,   "Wandering Trader",  wanderingEnabled.get());
    }

    // ─────────────────────────────────────────────
    //  Detection Helpers
    // ─────────────────────────────────────────────
    private void checkNewEntities(Set<Integer> current, Set<Integer> detected, String name, boolean featureEnabled) {
        if (!featureEnabled) return;

        if (!current.isEmpty() && !current.equals(detected)) {
            Set<Integer> newOnes = new HashSet<>(current);
            newOnes.removeAll(detected);
            if (!newOnes.isEmpty()) {
                detected.addAll(newOnes);
                int count = current.size();
                String msg = count == 1
                    ? name + " detected!"
                    : count + " " + name + "s detected!";
                sendNotification(msg, Items.LEAD);
                if (enableWebhook.get()) sendWebhook(name, count, getEmoji(name));
                handleActions(msg);
            }
        } else if (current.isEmpty()) {
            detected.clear();
        }
    }

    private void checkNewPillagers(Set<Integer> current) {
        if (!pillagerEnabled.get()) return;

        if (!current.isEmpty() && !current.equals(detectedPillagers)) {
            Set<Integer> newOnes = new HashSet<>(current);
            newOnes.removeAll(detectedPillagers);
            if (!newOnes.isEmpty()) {
                detectedPillagers.addAll(newOnes);
                if (enableWebhook.get()) sendWebhook("Pillager", pillagerList.size(), "⚔️");
                if (enableDisconnect.get()) {
                    String msg = pillagerList.size() == 1 ? "Pillager detected!" : pillagerList.size() + " pillagers detected!";
                    disconnectFromServer(msg);
                }
                if (toggleOnFind.get()) toggle();
            }
        } else if (current.isEmpty()) {
            detectedPillagers.clear();
        }
    }

    private void checkNewVillagers(Set<Integer> current, int villagerCount, int zombieVillagerCount) {
        if (!villagerEnabled.get()) return;

        if (!current.isEmpty() && !current.equals(detectedVillagers)) {
            Set<Integer> newOnes = new HashSet<>(current);
            newOnes.removeAll(detectedVillagers);
            if (!newOnes.isEmpty()) {
                detectedVillagers.addAll(newOnes);
                String msg = buildVillagerMessage(villagerCount, zombieVillagerCount);
                sendNotification(msg, Items.EMERALD);
                if (enableWebhook.get()) sendVillagerWebhook(villagerCount, zombieVillagerCount);
                handleActions(msg);
            }
        } else if (current.isEmpty()) {
            detectedVillagers.clear();
        }
    }

    private void handleActions(String message) {
        if (toggleOnFind.get()) toggle();
        if (enableDisconnect.get()) disconnectFromServer(message);
    }

    // ─────────────────────────────────────────────
    //  Render Helpers
    // ─────────────────────────────────────────────
    private void drawTracer(Render3DEvent event, Entity entity, Box boundingBox, Color color) {
        double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
        double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
        double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;
        double height = boundingBox.maxY - boundingBox.minY;
        y += height / 2;
        event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);
    }

    private void renderPillager(PillagerEntity pillager, Render3DEvent event) {
        Box box = pillager.getBoundingBox();
        Color espCol = new Color(pillagerEspColor.get());
        event.renderer.box(box, espCol, espCol, pillagerShapeMode.get(), 0);

        if (pillagerTracersEnabled.get()) {
            Color tracerCol = new Color(pillagerTracerColor.get());
            Vec3d center = Vec3d.ofCenter(new BlockPos(
                (int) pillager.getX(),
                (int) (pillager.getY() + pillager.getHeight() / 2),
                (int) pillager.getZ()
            ));

            switch (pillagerTracersMode.get()) {
                case Line ->
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        center.x, center.y, center.z, tracerCol);
                case Dot -> {
                    Box dot = new Box(center.x - 0.1, center.y - 0.1, center.z - 0.1,
                        center.x + 0.1, center.y + 0.1, center.z + 0.1);
                    event.renderer.box(dot, tracerCol, tracerCol, ShapeMode.Both, 0);
                }
                case Both -> {
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        center.x, center.y, center.z, tracerCol);
                    Box dot = new Box(center.x - 0.1, center.y - 0.1, center.z - 0.1,
                        center.x + 0.1, center.y + 0.1, center.z + 0.1);
                    event.renderer.box(dot, tracerCol, tracerCol, ShapeMode.Both, 0);
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Notifications
    // ─────────────────────────────────────────────
    private void sendNotification(String message, net.minecraft.item.Item toastItem) {
        switch (notificationMode.get()) {
            case Chat  -> { if (notifications.get()) info("(highlight)%s", message); }
            case Toast -> mc.getToastManager().add(new MeteorToast(toastItem, title, message));
            case Both  -> {
                if (notifications.get()) info("(highlight)%s", message);
                mc.getToastManager().add(new MeteorToast(toastItem, title, message));
            }
        }
    }

    private String buildVillagerMessage(int vc, int zvc) {
        return switch (villagerDetectionMode.get()) {
            case Villagers     -> vc == 1 ? "Villager detected!" : vc + " villagers detected!";
            case ZombieVillagers -> zvc == 1 ? "Zombie villager detected!" : zvc + " zombie villagers detected!";
            case Both -> {
                if (vc > 0 && zvc > 0) yield vc + " villagers and " + zvc + " zombie villagers detected!";
                else if (vc > 0) yield vc == 1 ? "Villager detected!" : vc + " villagers detected!";
                else yield zvc == 1 ? "Zombie villager detected!" : zvc + " zombie villagers detected!";
            }
        };
    }

    private void disconnectFromServer(String reason) {
        if (mc.world != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            if (notifications.get()) info("Disconnected from server - " + reason);
        }
    }

    // ─────────────────────────────────────────────
    //  Webhook
    // ─────────────────────────────────────────────
    private String getEmoji(String name) {
        return switch (name) {
            case "Llama"           -> "🦙";
            case "Wandering Trader"-> "🛒";
            default                -> "❗";
        };
    }

    private void sendWebhook(String entityName, int count, String emoji) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo   = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Unknown Server";
                String mentionPart  = (selfPing.get() && !discordId.get().trim().isEmpty())
                    ? String.format("<@%s>", discordId.get().trim()) : "";
                String coordinates  = mc.player != null
                    ? String.format("X: %.0f, Y: %.0f, Z: %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    : "Unknown";
                String entityText   = count == 1 ? entityName : entityName + "s";
                String description  = count + " " + entityText + " detected!";

                String payload = String.format(
                    "{\"content\":\"%s\",\"username\":\"DwellEntitiesESP\",\"embeds\":[{" +
                    "\"title\":\"%s %s Alert\",\"description\":\"%s\",\"color\":16753920," +
                    "\"fields\":[" +
                    "{\"name\":\"Count\",\"value\":\"%d\",\"inline\":true}," +
                    "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Coordinates\",\"value\":\"%s\",\"inline\":false}," +
                    "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                    "],\"footer\":{\"text\":\"Sent by Glazed\"}}]}",
                    mentionPart.replace("\"", "\\\""),
                    emoji, entityName,
                    description.replace("\"", "\\\""),
                    count,
                    serverInfo.replace("\"", "\\\""),
                    coordinates.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000
                );

                postWebhook(url, payload);
            } catch (Exception e) {
                if (notifications.get()) error("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private void sendVillagerWebhook(int vc, int zvc) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo  = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Unknown Server";
                String mentionPart = (selfPing.get() && !discordId.get().trim().isEmpty())
                    ? String.format("<@%s>", discordId.get().trim()) : "";
                String coordinates = mc.player != null
                    ? String.format("X: %.0f, Y: %.0f, Z: %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    : "Unknown";
                String description = buildVillagerMessage(vc, zvc);

                StringBuilder fields = new StringBuilder();
                fields.append(String.format("{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true},", serverInfo.replace("\"", "\\\"")));
                if (vc > 0) fields.append(String.format("{\"name\":\"Villagers\",\"value\":\"%d\",\"inline\":true},", vc));
                if (zvc > 0) fields.append(String.format("{\"name\":\"Zombie Villagers\",\"value\":\"%d\",\"inline\":true},", zvc));
                fields.append(String.format(
                    "{\"name\":\"Coordinates\",\"value\":\"%s\",\"inline\":false}," +
                    "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}",
                    coordinates.replace("\"", "\\\""), System.currentTimeMillis() / 1000
                ));

                String payload = String.format(
                    "{\"content\":\"%s\",\"username\":\"DwellEntitiesESP\",\"embeds\":[{" +
                    "\"title\":\"🏘️ Villager Alert\",\"description\":\"%s\",\"color\":65280," +
                    "\"fields\":[%s],\"footer\":{\"text\":\"Sent by Glazed\"}}]}",
                    mentionPart.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    fields
                );

                postWebhook(url, payload);
            } catch (Exception e) {
                if (notifications.get()) error("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private void postWebhook(String url, String jsonPayload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204) {
            if (notifications.get()) info("Webhook notification sent successfully");
        } else {
            if (notifications.get()) error("Webhook failed with status: " + response.statusCode());
        }
    }

    // ─────────────────────────────────────────────
    //  Info String
    // ─────────────────────────────────────────────
    @Override
    public String getInfoString() {
        int total = detectedLlamas.size() + detectedPillagers.size() +
                    detectedVillagers.size() + detectedTraders.size();
        return total == 0 ? null : String.valueOf(total);
    }

    // ─────────────────────────────────────────────
    //  Enums
    // ─────────────────────────────────────────────
    public enum NotificationMode { Chat, Toast, Both }

    public enum TracersMode { Line, Dot, Both }

    public enum VillagerDetectionMode {
        Villagers("Villagers"),
        ZombieVillagers("Zombie Villagers"),
        Both("Both");

        private final String name;
        VillagerDetectionMode(String name) { this.name = name; }

        @Override
        public String toString() { return name; }
    }
}