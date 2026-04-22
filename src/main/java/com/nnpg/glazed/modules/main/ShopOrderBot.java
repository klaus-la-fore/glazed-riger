package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nnpg.glazed.GlazedAddon;

/**
 * ShopOrderBot — unified shop-to-order flipper.
 *
 * Flow per cycle:
 *   /shop → Overview (slot 11-14) → Category screen (slot 9-17) → Buy screen:
 *     slot 17 = "Set to Stack" → click once
 *     slot 23 = CONFIRM        → click 36 times (fills inventory)
 *   → /orders → find matching order → click order slot → delivery screen opens →
 *     shift-click (QUICK_MOVE) all matching items from inventory (slots 0-35) →
 *     close delivery screen → confirm screen (green glass) → click confirm → repeat
 *
 * Buy screen:
 *   Slot 17 = "Set to Stack" button  → click once to set quantity to a full stack
 *   Slot 23 = CONFIRM                → click 36 times (= full inventory of stacks)
 */
public class ShopOrderBot extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ── State Machine ─────────────────────────────────────────────────────────

    private enum Stage {
        IDLE,
        SHOP_OPEN,        // /shop sent, waiting for overview screen
        SHOP_CATEGORY,    // overview visible, clicking category nav item
        SHOP_ITEM,        // category screen visible, clicking target item
        SHOP_SET_STACK,   // buy screen visible, clicking "set to stack" (slot 17)
        SHOP_BUY_LOOP,    // buy screen visible, clicking CONFIRM (slot 23) 36 times
        ORDERS_OPEN,      // /orders sent, waiting for orders screen
        ORDERS_SCAN,      // orders screen visible, scanning for matching order slot → click it
        ORDERS_SELECT,    // order selected, shift-clicking our items from inventory into order
        ORDERS_CONFIRM,   // items transferred, clicking green glass confirm button
        ORDERS_FINAL_EXIT,// confirm clicked, closing remaining screens
        CYCLE_PAUSE       // brief pause before next cycle
    }

    private Stage stage     = Stage.IDLE;
    private long  stageMs   = 0L;

    // how long before a stuck stage is aborted and cycled (ms)
    private static final long STAGE_TIMEOUT_MS = 3_000L;
    // pause between cycles (ms)
    private static final long CYCLE_PAUSE_MS   = 200L;

    // last click tick for delay enforcement
    private long lastClickTick = 0L;
    private long currentTick   = 0L;

    // counter for SHOP_BUY_LOOP — how many CONFIRM clicks done this cycle
    private int buyLoopCount = 0;
    private static final int BUY_STACKS = 36;

    // delivery state — tracks which inventory slot we're shift-clicking in ORDERS_SELECT
    private int  deliverIndex  = 0;
    private long lastDeliverMs = 0L;

    // targeting resolved at activation time
    private String  resolvedTarget  = "";
    private boolean targetingActive = false;

    // ── Settings ──────────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgOrders    = settings.createGroup("Orders");
    private final SettingGroup sgTargeting = settings.createGroup("Player Targeting");

    // -- General --

    private final Setting<ShopCategory> shopCategory = sgGeneral.add(new EnumSetting.Builder<ShopCategory>()
        .name("category")
        .description("Shop category to navigate to.")
        .defaultValue(ShopCategory.END)
        .build()
    );

    // One item-picker per category — only the matching one is visible
    private final Setting<EndItem> endItem = sgGeneral.add(new EnumSetting.Builder<EndItem>()
        .name("end-item")
        .description("End shop item to buy and flip.")
        .defaultValue(EndItem.SHULKER_SHELL)
        .visible(() -> shopCategory.get() == ShopCategory.END)
        .build()
    );

    private final Setting<NetherItem> netherItem = sgGeneral.add(new EnumSetting.Builder<NetherItem>()
        .name("nether-item")
        .description("Nether shop item to buy and flip.")
        .defaultValue(NetherItem.BLAZE_ROD)
        .visible(() -> shopCategory.get() == ShopCategory.NETHER)
        .build()
    );

    private final Setting<GearItem> gearItem = sgGeneral.add(new EnumSetting.Builder<GearItem>()
        .name("gear-item")
        .description("Gear shop item to buy and flip.")
        .defaultValue(GearItem.TOTEM_OF_UNDYING)
        .visible(() -> shopCategory.get() == ShopCategory.GEAR)
        .build()
    );

    private final Setting<FoodItem> foodItem = sgGeneral.add(new EnumSetting.Builder<FoodItem>()
        .name("food-item")
        .description("Food shop item to buy and flip.")
        .defaultValue(FoodItem.GOLDEN_APPLE)
        .visible(() -> shopCategory.get() == ShopCategory.FOOD)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show status notifications in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> clickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Delay between navigation clicks (category, item) in ticks.")
        .defaultValue(10)
        .min(0)
        .max(100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> confirmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Delay between the 36 CONFIRM buy clicks in ticks.")
        .defaultValue(2)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> deliverDelay = sgGeneral.add(new IntSetting.Builder()
        .name("deliver-delay")
        .description("Delay in ms between each item shift-click when delivering to orders.")
        .defaultValue(50)
        .min(0)
        .max(500)
        .sliderMin(0)
        .sliderMax(500)
        .build()
    );

    // -- Orders --

    private final Setting<String> minPrice = sgOrders.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum order price. Supports K/M/B suffixes (e.g. 1.5K = 1500).")
        .defaultValue("50")
        .build()
    );

    // -- Targeting --

    private final Setting<Boolean> enableTargeting = sgTargeting.add(new BoolSetting.Builder()
        .name("enable-targeting")
        .description("Target a specific player's orders. Min-price is ignored for the targeted player.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> targetPlayerName = sgTargeting.add(new StringSetting.Builder()
        .name("target-player")
        .description("Player name to prioritise.")
        .defaultValue("")
        .visible(() -> enableTargeting.get())
        .build()
    );

    private final Setting<Boolean> targetOnlyMode = sgTargeting.add(new BoolSetting.Builder()
        .name("target-only-mode")
        .description("Skip ALL orders except from the targeted player.")
        .defaultValue(false)
        .visible(() -> enableTargeting.get())
        .build()
    );

    private final Setting<List<String>> blacklistedPlayers = sgTargeting.add(
        new StringListSetting.Builder()
            .name("blacklisted-players")
            .description("Players whose orders are always skipped.")
            .defaultValue(List.of())
            .build()
    );

    // ── Constructor ───────────────────────────────────────────────────────────

    public ShopOrderBot() {
        super(GlazedAddon.CATEGORY, "shop-order-bot",
            "Buys items from /shop and sells them in player orders for profit.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player == null) { toggle(); return; }

        double parsed = parsePrice(minPrice.get());
        if (parsed < 0 && !enableTargeting.get()) {
            ChatUtils.error("ShopOrderBot: Invalid min-price — use a number like 50, 1.5K, 2M");
            toggle();
            return;
        }

        resolvedTarget  = "";
        targetingActive = false;
        lastClickTick   = 0L;
        currentTick     = 0L;
        buyLoopCount    = 0;
        deliverIndex    = 0;
        lastDeliverMs   = 0L;
        if (enableTargeting.get() && !targetPlayerName.get().isBlank()) {
            resolvedTarget  = targetPlayerName.get().trim();
            targetingActive = true;
            log("Targeting player: §e%s", resolvedTarget);
        }

        // Ensure selected item belongs to selected category
        log("Started — §e%s§r | min: §e$%s", getSelectedItem().label, minPrice.get());
        goTo(Stage.SHOP_OPEN);
    }

    @Override
    public void onDeactivate() {
        stage = Stage.IDLE;
    }

    /** Returns the active item+category pair based on current settings. */
    private ShopItem getSelectedItem() {
        return switch (shopCategory.get()) {
            case END    -> endItem.get().shopItem;
            case NETHER -> netherItem.get().shopItem;
            case GEAR   -> gearItem.get().shopItem;
            case FOOD   -> foodItem.get().shopItem;
        };
    }

    // ── Main Tick ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        currentTick++;
        long now = System.currentTimeMillis();

        // Global timeout guard — prevents getting stuck in any stage
        if (stage != Stage.IDLE && stage != Stage.CYCLE_PAUSE
                && (now - stageMs) > STAGE_TIMEOUT_MS) {
            log("§cTimeout in stage %s — restarting cycle", stage);
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            goTo(Stage.CYCLE_PAUSE);
            return;
        }

        switch (stage) {

            // ── Shop: navigate to item ────────────────────────────────────────

            case SHOP_OPEN -> {
                ChatUtils.sendPlayerMsg("/shop");
                goTo(Stage.SHOP_CATEGORY);
            }

            case SHOP_CATEGORY -> {
                // Overview screen must be open — click the category slot directly
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                int catSlot = getSelectedItem().category.slot; // slot 11–14
                if (clickSlotById(screen, catSlot)) goTo(Stage.SHOP_ITEM);
            }

            case SHOP_ITEM -> {
                // Category screen open — click the item slot directly (slots 9–17)
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                int itemSlot = getSelectedItem().slot;
                if (clickSlotById(screen, itemSlot)) goTo(Stage.SHOP_SET_STACK);
            }

            case SHOP_SET_STACK -> {
                // Buy screen open — click "set to stack" button at slot 17 first
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                if (clickSlotById(screen, 17)) {
                    buyLoopCount = 0;
                    goTo(Stage.SHOP_BUY_LOOP);
                }
            }

            case SHOP_BUY_LOOP -> {
                // Buy screen open — click CONFIRM (slot 23) 36 times, one per tick-delay
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                if (buyLoopCount >= BUY_STACKS) {
                    mc.player.closeHandledScreen();
                    log("§aBought §e%d§a stacks.", BUY_STACKS);
                    goTo(Stage.ORDERS_OPEN);
                    return;
                }
                if (clickSlotByIdWithDelay(screen, 23, confirmDelay.get())) {
                    buyLoopCount++;
                }
            }

            // ── Orders: find, deliver, confirm ────────────────────────────────

            case ORDERS_OPEN -> {
                if (targetingActive) {
                    ChatUtils.sendPlayerMsg("/orders " + resolvedTarget);
                } else {
                    ChatUtils.sendPlayerMsg("/orders");
                }
                goTo(Stage.ORDERS_SCAN);
            }

            case ORDERS_SCAN -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                // Small stabilisation wait so the GUI is fully loaded
                if (now - stageMs < 200) return;
                ScreenHandler handler = screen.getScreenHandler();
                Item target           = getSelectedItem().item;
                double minVal         = parsePrice(minPrice.get());

                for (Slot slot : handler.slots) {
                    ItemStack s = slot.getStack();
                    if (s.isEmpty() || s.getItem() != target) continue;

                    String  orderPlayer = getOrderPlayerName(s);
                    double  price       = getOrderPrice(s);
                    boolean isTarget    = targetingActive
                        && resolvedTarget.equalsIgnoreCase(orderPlayer);

                    if (isBlacklisted(orderPlayer)) continue;
                    if (targetingActive && targetOnlyMode.get() && !isTarget) continue;
                    if (!isTarget && price < minVal) continue;

                    // Click the order slot to open the delivery screen
                    mc.interactionManager.clickSlot(
                        handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                    deliverIndex  = 0;
                    lastDeliverMs = 0L;
                    goTo(Stage.ORDERS_SELECT);
                    log("§aOrder: §e%s §r— §e%s",
                        orderPlayer != null ? orderPlayer : "?", formatPrice(price));
                    return;
                }

                // No matching order found — restart cycle
                mc.player.closeHandledScreen();
                goTo(Stage.CYCLE_PAUSE);
            }

            case ORDERS_SELECT -> {
                // Delivery screen is open — shift-click each stack of the target item
                // from our inventory (slots 0-35) into the order.
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    // Screen closed unexpectedly — skip to confirm
                    goTo(Stage.ORDERS_CONFIRM);
                    return;
                }

                if (deliverIndex >= 36) {
                    // All inventory slots processed — close and go confirm
                    mc.player.closeHandledScreen();
                    goTo(Stage.ORDERS_CONFIRM);
                    return;
                }

                if (now - lastDeliverMs < deliverDelay.get()) return;

                ScreenHandler handler  = screen.getScreenHandler();
                Item          target   = getSelectedItem().item;
                ItemStack     invStack = mc.player.getInventory().getStack(deliverIndex);

                if (!invStack.isEmpty() && invStack.getItem() == target) {
                    // Find the corresponding slot id inside the open screen handler
                    for (Slot slot : handler.slots) {
                        if (slot.inventory == mc.player.getInventory()
                                && slot.getIndex() == deliverIndex) {
                            mc.interactionManager.clickSlot(
                                handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                            break;
                        }
                    }
                }
                lastDeliverMs = now;
                deliverIndex++;
            }

            case ORDERS_CONFIRM -> {
                // Wait for the confirm screen (green glass pane) to appear and click it
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    // No screen — delivery was auto-completed
                    goTo(Stage.ORDERS_FINAL_EXIT);
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();
                for (Slot slot : handler.slots) {
                    if (isConfirmButton(slot.getStack())) {
                        // Spam a few clicks to be safe
                        for (int i = 0; i < 5; i++) {
                            mc.interactionManager.clickSlot(
                                handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        }
                        goTo(Stage.ORDERS_FINAL_EXIT);
                        return;
                    }
                }
                // Timeout if confirm screen never appears
                if (now - stageMs > 3000) {
                    mc.player.closeHandledScreen();
                    goTo(Stage.CYCLE_PAUSE);
                }
            }

            case ORDERS_FINAL_EXIT -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                if (now - stageMs > 200) goTo(Stage.CYCLE_PAUSE);
            }

            case CYCLE_PAUSE -> {
                if (now - stageMs >= CYCLE_PAUSE_MS) goTo(Stage.SHOP_OPEN);
            }
        }
    }

    // ── Slot interaction ──────────────────────────────────────────────────────

    /**
     * Attempts to click a slot. Returns true if the click was performed,
     * false if the tick-delay has not elapsed yet (caller should not advance stage).
     */
    private boolean click(GenericContainerScreen screen, Slot slot) {
        if (currentTick - lastClickTick < clickDelay.get()) {
            return false; // delay not elapsed yet — retry next tick
        }

        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
        lastClickTick = currentTick;
        return true;
    }

    /**
     * Clicks a slot by its index in the ScreenHandler slot list.
     * Returns true if the click was performed, false if delayed or out of bounds.
     */
    private boolean clickSlotById(GenericContainerScreen screen, int slotId) {
        ScreenHandler handler = screen.getScreenHandler();
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        return click(screen, handler.slots.get(slotId));
    }

    /**
     * Like clickSlotById but uses a custom tick delay instead of clickDelay.
     * Used for the 36 CONFIRM clicks so they can have their own faster delay.
     */
    private boolean clickSlotByIdWithDelay(GenericContainerScreen screen, int slotId, int delayTicks) {
        ScreenHandler handler = screen.getScreenHandler();
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        if (currentTick - lastClickTick < delayTicks) return false;
        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId, handler.slots.get(slotId).id,
            0, SlotActionType.PICKUP, mc.player);
        lastClickTick = currentTick;
        return true;
    }

    // ── Item / button detection ───────────────────────────────────────────────

    /**
     * CONFIRM button detection.
     * Buy screen:    lime/green stained glass pane whose name contains "confirm"
     * Orders screen: any lime or green stained glass pane (the confirm button on delivery)
     */
    private boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        boolean isGlass = item == Items.LIME_STAINED_GLASS_PANE
                       || item == Items.GREEN_STAINED_GLASS_PANE;
        if (!isGlass) return false;
        String name = stack.getName().getString().toLowerCase();
        // On the buy screen the button explicitly says confirm
        // On the orders confirm screen it is typically the only glass pane present
        return name.contains("confirm") || name.contains("ᴄᴏɴꜰɪʀᴍ") || true;
    }

    

    // ── Tooltip parsing (player name + price from orders) ────────────────────

    private static final Pattern[] NAME_PATTERNS = {
        Pattern.compile("(?i)player\\s*:\\s*([a-zA-Z0-9_]+)"),
        Pattern.compile("(?i)from\\s*:\\s*([a-zA-Z0-9_]+)"),
        Pattern.compile("(?i)by\\s*:\\s*([a-zA-Z0-9_]+)"),
        Pattern.compile("(?i)seller\\s*:\\s*([a-zA-Z0-9_]+)"),
        Pattern.compile("(?i)owner\\s*:\\s*([a-zA-Z0-9_]+)")
    };
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");

    private String getOrderPlayerName(ItemStack stack) {
        if (stack.isEmpty()) return null;
        List<Text> tooltip = stack.getTooltip(
            Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        for (Text line : tooltip) {
            String text = line.getString();
            for (Pattern p : NAME_PATTERNS) {
                Matcher m = p.matcher(text);
                if (m.find()) {
                    String name = m.group(1);
                    if (name.length() >= 3 && name.length() <= 16) return name;
                }
            }
        }
        return null;
    }

    private double getOrderPrice(ItemStack stack) {
        if (stack.isEmpty()) return -1.0;
        List<Text> tooltip = stack.getTooltip(
            Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        for (Text line : tooltip) {
            Matcher m = PRICE_PATTERN.matcher(line.getString());
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1.0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBlacklisted(String playerName) {
        if (playerName == null || blacklistedPlayers.get().isEmpty()) return false;
        return blacklistedPlayers.get().stream().anyMatch(p -> p.equalsIgnoreCase(playerName));
    }

    /** Parses prices like "50", "1.5K", "2M", "1B". Returns -1 on failure. */
    private double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return -1.0;
        String s = raw.trim().toUpperCase().replace(",", "");
        try {
            if (s.endsWith("B")) return Double.parseDouble(s.replace("B","")) * 1_000_000_000.0;
            if (s.endsWith("M")) return Double.parseDouble(s.replace("M","")) * 1_000_000.0;
            if (s.endsWith("K")) return Double.parseDouble(s.replace("K","")) * 1_000.0;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) { return -1.0; }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000_000) return String.format("$%.1fB", price / 1_000_000_000);
        if (price >= 1_000_000)     return String.format("$%.1fM", price / 1_000_000);
        if (price >= 1_000)         return String.format("$%.1fK", price / 1_000);
        return String.format("$%.0f", price);
    }

    private void goTo(Stage next) {
        stage   = next;
        stageMs = System.currentTimeMillis();
    }

    private void log(String msg, Object... args) {
        if (notifications.get()) ChatUtils.info(String.format("[ShopOrderBot] " + msg, args));
    }

    // ── Enums ─────────────────────────────────────────────────────────────────

    /**
     * Shop categories — each maps to the nav item shown on the Overview screen (Screen 1).
     *
     * Screen 1 layout (shop_screens.md):
     *   Slot 11 → END_STONE  → End shop
     *   Slot 12 → NETHERRACK → Nether shop
     *   Slot 13 → TOTEM      → Gear shop
     *   Slot 14 → COOKED_BEEF→ Food shop
     */
    public enum ShopCategory {
        END   (Items.END_STONE,       11),
        NETHER(Items.NETHERRACK,      12),
        GEAR  (Items.TOTEM_OF_UNDYING,13),
        FOOD  (Items.COOKED_BEEF,     14);

        /** Slot index on the Overview screen (/shop) to click for this category. */
        final int  slot;
        ShopCategory(Item navItem, int slot) { this.slot = slot; }
    }

    // ── Per-category item enums (each backed by a ShopItem) ─────────────────

    public enum EndItem {
        ENDER_CHEST      ("Ender Chest",        Items.ENDER_CHEST,         ShopCategory.END,  9),
        ENDER_PEARL      ("Ender Pearl",         Items.ENDER_PEARL,         ShopCategory.END, 10),
        END_STONE        ("End Stone",           Items.END_STONE,           ShopCategory.END, 11),
        DRAGON_BREATH    ("Dragon Breath",       Items.DRAGON_BREATH,       ShopCategory.END, 12),
        END_ROD          ("End Rod",             Items.END_ROD,             ShopCategory.END, 13),
        CHORUS_FRUIT     ("Chorus Fruit",        Items.CHORUS_FRUIT,        ShopCategory.END, 14),
        POPPED_CHORUS    ("Popped Chorus Fruit", Items.POPPED_CHORUS_FRUIT, ShopCategory.END, 15),
        SHULKER_SHELL    ("Shulker Shell",       Items.SHULKER_SHELL,       ShopCategory.END, 16),
        SHULKER_BOX      ("Shulker Box",         Items.SHULKER_BOX,         ShopCategory.END, 17);

        final ShopItem shopItem;
        EndItem(String label, Item item, ShopCategory cat, int slot) {
            this.shopItem = new ShopItem(label, cat, item, slot);
        }
        @Override public String toString() { return shopItem.label; }
    }

    public enum NetherItem {
        BLAZE_ROD       ("Blaze Rod",       Items.BLAZE_ROD,       ShopCategory.NETHER,  9),
        NETHER_WART     ("Nether Wart",     Items.NETHER_WART,     ShopCategory.NETHER, 10),
        GLOWSTONE_DUST  ("Glowstone Dust",  Items.GLOWSTONE_DUST,  ShopCategory.NETHER, 11),
        MAGMA_CREAM     ("Magma Cream",     Items.MAGMA_CREAM,     ShopCategory.NETHER, 12),
        GHAST_TEAR      ("Ghast Tear",      Items.GHAST_TEAR,      ShopCategory.NETHER, 13),
        NETHER_QUARTZ   ("Nether Quartz",   Items.QUARTZ,          ShopCategory.NETHER, 14),
        SOUL_SAND       ("Soul Sand",       Items.SOUL_SAND,       ShopCategory.NETHER, 15),
        MAGMA_BLOCK     ("Magma Block",     Items.MAGMA_BLOCK,     ShopCategory.NETHER, 16),
        CRYING_OBSIDIAN ("Crying Obsidian", Items.CRYING_OBSIDIAN, ShopCategory.NETHER, 17);

        final ShopItem shopItem;
        NetherItem(String label, Item item, ShopCategory cat, int slot) {
            this.shopItem = new ShopItem(label, cat, item, slot);
        }
        @Override public String toString() { return shopItem.label; }
    }

    public enum GearItem {
        OBSIDIAN          ("Obsidian",           Items.OBSIDIAN,           ShopCategory.GEAR,  9),
        END_CRYSTAL       ("End Crystal",         Items.END_CRYSTAL,        ShopCategory.GEAR, 10),
        RESPAWN_ANCHOR    ("Respawn Anchor",      Items.RESPAWN_ANCHOR,     ShopCategory.GEAR, 11),
        GLOWSTONE         ("Glowstone",           Items.GLOWSTONE,          ShopCategory.GEAR, 12),
        TOTEM_OF_UNDYING  ("Totem of Undying",    Items.TOTEM_OF_UNDYING,   ShopCategory.GEAR, 13),
        ENDER_PEARL       ("Ender Pearl",         Items.ENDER_PEARL,        ShopCategory.GEAR, 14),
        GOLDEN_APPLE      ("Golden Apple",        Items.GOLDEN_APPLE,       ShopCategory.GEAR, 15),
        EXPERIENCE_BOTTLE ("Experience Bottle",   Items.EXPERIENCE_BOTTLE,  ShopCategory.GEAR, 16),
        TIPPED_ARROW      ("Tipped Arrow (Slow)", Items.TIPPED_ARROW,       ShopCategory.GEAR, 17);

        final ShopItem shopItem;
        GearItem(String label, Item item, ShopCategory cat, int slot) {
            this.shopItem = new ShopItem(label, cat, item, slot);
        }
        @Override public String toString() { return shopItem.label; }
    }

    public enum FoodItem {
        POTATO         ("Potato",         Items.POTATO,         ShopCategory.FOOD,  9),
        SWEET_BERRIES  ("Sweet Berries",  Items.SWEET_BERRIES,  ShopCategory.FOOD, 10),
        MELON_SLICE    ("Melon Slice",    Items.MELON_SLICE,    ShopCategory.FOOD, 11),
        CARROT         ("Carrot",         Items.CARROT,         ShopCategory.FOOD, 12),
        APPLE          ("Apple",          Items.APPLE,          ShopCategory.FOOD, 13),
        COOKED_CHICKEN ("Cooked Chicken", Items.COOKED_CHICKEN, ShopCategory.FOOD, 14),
        COOKED_BEEF    ("Cooked Beef",    Items.COOKED_BEEF,    ShopCategory.FOOD, 15),
        GOLDEN_CARROT  ("Golden Carrot",  Items.GOLDEN_CARROT,  ShopCategory.FOOD, 16),
        GOLDEN_APPLE   ("Golden Apple",   Items.GOLDEN_APPLE,   ShopCategory.FOOD, 17);

        final ShopItem shopItem;
        FoodItem(String label, Item item, ShopCategory cat, int slot) {
            this.shopItem = new ShopItem(label, cat, item, slot);
        }
        @Override public String toString() { return shopItem.label; }
    }

    /** Lightweight item descriptor used internally — not a Setting enum. */
    public static class ShopItem {
        final String       label;
        final ShopCategory category;
        final Item         item;
        /** Slot index on the category screen to click for this item. */
        final int          slot;

        ShopItem(String label, ShopCategory category, Item item, int slot) {
            this.label    = label;
            this.category = category;
            this.item     = item;
            this.slot     = slot;
        }
    }
}
