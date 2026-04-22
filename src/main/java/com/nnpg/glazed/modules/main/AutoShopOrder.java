package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.addon.GlazedAddon;
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
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoShopOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public enum ShopCategory { END, NETHER, GEAR, FOOD }
    public enum EndItem { SHULKER_SHELL, SHULKER_BOX, ENDER_CHEST, ENDER_PEARL, END_STONE, DRAGON_BREATH }
    public enum NetherItem { BLAZE_ROD, NETHER_WART, GLOWSTONE_DUST, MAGMA_CREAM, GHAST_TEAR, NETHER_QUARTZ }
    public enum GearItem { TOTEM_OF_UNDYING, OBSIDIAN, END_CRYSTAL, RESPAWN_ANCHOR, GLOWSTONE, GOLDEN_APPLE }
    public enum FoodItem { GOLDEN_CARROT, COOKED_BEEF, GOLDEN_APPLE, COOKED_CHICKEN }

    private enum Stage { 
        SHOP_OPEN, SHOP_CATEGORY, SHOP_ITEM, SHOP_SET_STACK, SHOP_CONFIRM_SPAM, 
        ORDERS_OPEN, ORDERS_LIST, ORDERS_FILL_ITEMS, ORDERS_CONFIRM_SCREEN, ORDERS_EXIT_1, ORDERS_EXIT_2, CYCLE_PAUSE 
    }

    private Stage stage = Stage.SHOP_OPEN;
    private long lastActionTime = 0;
    private static final long DELAY = 120; // Delay in MS für Server-Stabilität

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<ShopCategory> category = sgGeneral.add(new EnumSetting.Builder<ShopCategory>().name("category").defaultValue(ShopCategory.FOOD).build());
    private final Setting<EndItem> endItem = sgGeneral.add(new EnumSetting.Builder<EndItem>().name("end-item").defaultValue(EndItem.SHULKER_SHELL).visible(() -> category.get() == ShopCategory.END).build());
    private final Setting<NetherItem> netherItem = sgGeneral.add(new EnumSetting.Builder<NetherItem>().name("nether-item").defaultValue(NetherItem.BLAZE_ROD).visible(() -> category.get() == ShopCategory.NETHER).build());
    private final Setting<GearItem> gearItem = sgGeneral.add(new EnumSetting.Builder<GearItem>().name("gear-item").defaultValue(GearItem.TOTEM_OF_UNDYING).visible(() -> category.get() == ShopCategory.GEAR).build());
    private final Setting<FoodItem> foodItem = sgGeneral.add(new EnumSetting.Builder<FoodItem>().name("food-item").defaultValue(FoodItem.COOKED_CHICKEN).visible(() -> category.get() == ShopCategory.FOOD).build());
    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder().name("min-price").defaultValue("30").build());

    public AutoShopOrder() {
        super(GlazedAddon.CATEGORY, "auto-shop-order", "Kauft Items im Shop und liefert sie automatisch bei Orders ab.");
    }

    @Override
    public void onActivate() {
        stage = Stage.SHOP_OPEN;
        lastActionTime = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DELAY) return;

        switch (stage) {
            case SHOP_OPEN -> {
                ChatUtils.sendPlayerMsg("/shop");
                stage = Stage.SHOP_CATEGORY;
                lastActionTime = now;
            }

            case SHOP_CATEGORY -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                for (Slot slot : screen.getScreenHandler().slots) {
                    if (isCategoryIcon(slot.getStack())) {
                        click(screen, slot);
                        stage = Stage.SHOP_ITEM;
                        lastActionTime = now;
                        return;
                    }
                }
            }

            case SHOP_ITEM -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                for (Slot slot : screen.getScreenHandler().slots) {
                    if (slot.inventory != mc.player.getInventory() && isTargetItem(slot.getStack())) {
                        click(screen, slot);
                        stage = Stage.SHOP_SET_STACK;
                        lastActionTime = now;
                        return;
                    }
                }
            }

            case SHOP_SET_STACK -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                for (Slot slot : screen.getScreenHandler().slots) {
                    String name = slot.getStack().getName().getString();
                    if (isGreen(slot.getStack()) && name.contains("64")) {
                        click(screen, slot);
                        stage = Stage.SHOP_CONFIRM_SPAM;
                        lastActionTime = now;
                        return;
                    }
                }
                if (now - lastActionTime > 500) stage = Stage.SHOP_CONFIRM_SPAM;
            }

            case SHOP_CONFIRM_SPAM -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                for (Slot slot : screen.getScreenHandler().slots) {
                    if (isConfirmButton(slot.getStack())) {
                        for (int i = 0; i < 10; i++) click(screen, slot);
                        if (isInventoryFull()) {
                            mc.player.closeHandledScreen();
                            stage = Stage.ORDERS_OPEN;
                        }
                        lastActionTime = now;
                        return;
                    }
                }
            }

            case ORDERS_OPEN -> {
                if (mc.currentScreen != null) break;
                ChatUtils.sendPlayerMsg("/orders " + getSearchKeyword());
                stage = Stage.ORDERS_LIST;
                lastActionTime = now;
            }

            case ORDERS_LIST -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                Slot bestSlot = null;
                double maxPrice = -1;

                for (Slot slot : screen.getScreenHandler().slots) {
                    if (slot.inventory == mc.player.getInventory() || !isTargetItem(slot.getStack())) continue;
                    double price = getPriceFromLore(slot.getStack());
                    if (price > maxPrice && price >= parsePriceSetting()) {
                        maxPrice = price;
                        bestSlot = slot;
                    }
                }

                if (bestSlot != null) {
                    click(screen, bestSlot);
                    stage = Stage.ORDERS_FILL_ITEMS;
                    lastActionTime = now;
                }
            }

            case ORDERS_FILL_ITEMS -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                
                // Wir identifizieren das Item in Slot 13 (Mitte des Order-Screens)
                ItemStack infoStack = screen.getScreenHandler().getSlot(13).getStack();
                if (infoStack.isEmpty()) return;
                Item targetItem = infoStack.getItem();

                boolean moved = false;
                for (Slot slot : screen.getScreenHandler().slots) {
                    // WICHTIG: Nur Slots aus dem Spieler-Inventar prüfen
                    if (slot.inventory == mc.player.getInventory()) {
                        if (slot.getStack().getItem() == targetItem) {
                            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                            moved = true;
                        }
                    }
                }

                if (moved || now - lastActionTime > 800) {
                    mc.player.closeHandledScreen(); // Schließen löst den Config-Screen aus
                    stage = Stage.ORDERS_CONFIRM_SCREEN;
                    lastActionTime = now;
                }
            }

            case ORDERS_CONFIRM_SCREEN -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) break;
                // Der Config-Screen hat laut deinem JSON das grüne Glas in Slot 15
                Slot confirmSlot = screen.getScreenHandler().getSlot(15);
                if (confirmSlot != null && isConfirmButton(confirmSlot.getStack())) {
                    click(screen, confirmSlot);
                    stage = Stage.ORDERS_EXIT_1;
                    lastActionTime = now;
                }
            }

            case ORDERS_EXIT_1 -> {
                mc.player.closeHandledScreen();
                stage = Stage.ORDERS_EXIT_2;
                lastActionTime = now;
            }

            case ORDERS_EXIT_2 -> {
                mc.player.closeHandledScreen();
                stage = Stage.CYCLE_PAUSE;
                lastActionTime = now;
            }

            case CYCLE_PAUSE -> {
                if (now - lastActionTime > 1500) stage = Stage.SHOP_OPEN;
            }
        }
    }

    private void click(GenericContainerScreen screen, Slot slot) {
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
    }

    private boolean isConfirmButton(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();
        return isGreen(stack) && (name.contains("confirm") || name.contains("ᴄᴏɴꜰɪʀᴍ") || name.contains("deliver"));
    }

    private boolean isGreen(ItemStack stack) {
        Item i = stack.getItem();
        return i == Items.LIME_STAINED_GLASS_PANE || i == Items.GREEN_STAINED_GLASS_PANE || i == Items.LIME_CONCRETE;
    }

    private boolean isCategoryIcon(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();
        return switch (category.get()) {
            case END -> name.contains("ᴇɴᴅ") || stack.getItem() == Items.END_STONE;
            case NETHER -> name.contains("ɴᴇᴛʜᴇʀ") || stack.getItem() == Items.NETHERRACK;
            case GEAR -> name.contains("ɢᴇᴀʀ") || stack.getItem() == Items.TOTEM_OF_UNDYING;
            case FOOD -> name.contains("ꜰᴏᴏᴅ") || stack.getItem() == Items.COOKED_BEEF;
        };
    }

    private boolean isTargetItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString().toLowerCase();
        String search = getSearchKeyword().replace(" ", "_");
        return name.contains(search) || stack.getName().getString().toLowerCase().contains(getSearchKeyword());
    }

    private String getSearchKeyword() {
        return switch (category.get()) {
            case END -> endItem.get().name().replace("_", " ").toLowerCase();
            case NETHER -> netherItem.get().name().replace("_", " ").toLowerCase();
            case GEAR -> gearItem.get().name().replace("_", " ").toLowerCase();
            case FOOD -> foodItem.get().name().replace("_", " ").toLowerCase();
        };
    }

    private double getPriceFromLore(ItemStack stack) {
        List<Text> lore = stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        for (Text line : lore) {
            String text = line.getString().replace(",", "").replace(" ", "");
            Matcher m = Pattern.compile("\\$([\\d.]+)([kKmM]?)").matcher(text);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                String suffix = m.group(2).toLowerCase();
                if (suffix.equals("k")) val *= 1000;
                else if (suffix.equals("m")) val *= 1000000;
                return val;
            }
        }
        return -1;
    }

    private double parsePriceSetting() {
        try {
            return Double.parseDouble(minPrice.get().toLowerCase().replace("k", "000").replace("m", "000000"));
        } catch (Exception e) { return 0; }
    }

    private boolean isInventoryFull() {
        for (int i = 9; i <= 35; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        return true;
    }
}