package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nnpg.glazed.GlazedAddon;

public class AutoBoneOrder extends Module {

    // ── Setting Groups ────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgOrder    = settings.createGroup("Order");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // ── General ───────────────────────────────────────────────────────────────

    private final Setting<Boolean> hideBones = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-bone-entities")
        .description("Make dropped bone entities invisible client-side to reduce lag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> clickDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("click-delay-ms")
        .description("Delay between GUI clicks in milliseconds.")
        .defaultValue(200)
        .min(50)
        .max(2000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> pagesPerLoad = sgGeneral.add(new IntSetting.Builder()
        .name("pages-per-load")
        .description("How many pages of loot to drop per spawner opening (DROP → NEXT repeated N times).")
        .defaultValue(3)
        .min(1)
        .max(50)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> pickupWaitTicks = sgAdvanced.add(new IntSetting.Builder()
        .name("pickup-wait-ticks")
        .description("Ticks to wait after closing spawner for floor bones to be picked up.")
        .defaultValue(40)
        .min(10)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    // ── Order Settings ────────────────────────────────────────────────────────

    public enum OrderMode { FIRST, BEST_RATIO }

    private final Setting<OrderMode> orderMode = sgOrder.add(new EnumSetting.Builder<OrderMode>()
        .name("order-mode")
        .description("FIRST: always clicks the first order. BEST_RATIO: picks highest price × remaining capacity.")
        .defaultValue(OrderMode.BEST_RATIO)
        .build()
    );

    private final Setting<Boolean> orderToSpecific = sgOrder.add(new BoolSetting.Builder()
        .name("order-to-specific-player")
        .description("Order bones to a specific player using /order <name>.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> specificPlayer = sgOrder.add(new StringSetting.Builder()
        .name("specific-player")
        .description("Player name to use with /order <name>.")
        .defaultValue("")
        .visible(() -> orderToSpecific.get())
        .build()
    );

    // ── State Machine ─────────────────────────────────────────────────────────

    private enum State {
        IDLE,
        ROTATING,
        OPENING_SPAWNER,
        WAIT_SPAWNER_GUI,
        IN_SPAWNER_GUI,
        WAIT_BONE_PICKUP,
        CHECK_INVENTORY,
        SEND_ORDER_CMD,
        WAIT_ORDER_GUI,
        IN_ORDER_GUI,
        WAIT_DELIVERY_GUI,
        IN_DELIVERY_CONTAINER,
        WAIT_CONFIRM_GUI,
        CONFIRMING,
        WAIT_AFTER_CONFIRM
    }

    private State state = State.IDLE;

    // Timers (all in ticks unless named *Ms)
    private int tickTimer         = 0;
    private int waitTimer         = 0;
    private int pagesDropped      = 0;
    private int rotationTicks     = 0;

    private BlockPos spawnerPos   = null;

    // Regex patterns for lore parsing
    private static final Pattern PRICE_PATTERN    = Pattern.compile("\\$([0-9,]+\\.?[0-9]*)");
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("([0-9]+\\.?[0-9]*[KkMm]?)\\s*/\\s*([0-9]+\\.?[0-9]*[KkMm]?)");

    // ── Constructor ───────────────────────────────────────────────────────────

    public AutoBoneOrder() {
        super(GlazedAddon.CATEGORY, "auto-bone-order", "Automatically drops spawner loot and delivers bones to orders.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        state         = State.IDLE;
        tickTimer     = 0;
        waitTimer     = 0;
        pagesDropped  = 0;
        rotationTicks = 0;
        spawnerPos    = null;
    }

    @Override
    public void onDeactivate() {
        if (mc.currentScreen instanceof HandledScreen) {
            mc.setScreen(null);
        }
        state = State.IDLE;
    }

    // ── Main Tick ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        suppressBoneEntities();

        switch (state) {
            case IDLE             -> handleIdle();
            case ROTATING         -> handleRotating();
            case OPENING_SPAWNER  -> handleOpeningSpawner();
            case WAIT_SPAWNER_GUI -> handleWaitSpawnerGui();
            case IN_SPAWNER_GUI   -> handleInSpawnerGui();
            case WAIT_BONE_PICKUP -> handleWaitBonePickup();
            case CHECK_INVENTORY  -> handleCheckInventory();
            case SEND_ORDER_CMD   -> handleSendOrderCmd();
            case WAIT_ORDER_GUI   -> handleWaitOrderGui();
            case IN_ORDER_GUI     -> handleInOrderGui();
            case WAIT_DELIVERY_GUI   -> handleWaitDeliveryGui();
            case IN_DELIVERY_CONTAINER -> handleInDeliveryContainer();
            case WAIT_CONFIRM_GUI -> handleWaitConfirmGui();
            case CONFIRMING       -> handleConfirming();
            case WAIT_AFTER_CONFIRM -> handleWaitAfterConfirm();
        }
    }

    // ── State Handlers ────────────────────────────────────────────────────────

    private void handleIdle() {
        if (mc.currentScreen instanceof HandledScreen) {
            mc.setScreen(null);
        }
        spawnerPos = findNearbySpawner();
        if (spawnerPos == null) {
            if (notifications.get()) info("No spawner found within 6 blocks.");
            toggle();
            return;
        }
        pagesDropped = 0;
        setState(State.ROTATING, 0);
    }

    private void handleRotating() {
        rotateToward(Vec3d.ofCenter(spawnerPos));
        rotationTicks++;
        if (rotationTicks >= 5) {
            rotationTicks = 0;
            setState(State.OPENING_SPAWNER, 0);
        }
    }

    private void handleOpeningSpawner() {
        if (!tickDelay()) return;
        if (mc.player.getPos().distanceTo(Vec3d.ofCenter(spawnerPos)) > 5.0) {
            if (notifications.get()) info("Spawner out of reach.");
            toggle();
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(spawnerPos), Direction.UP, spawnerPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        setState(State.WAIT_SPAWNER_GUI, 20);
    }

    private void handleWaitSpawnerGui() {
        if (!timerExpired()) return;
        if (mc.currentScreen instanceof HandledScreen screen && isSpawnerGui(screen)) {
            setState(State.IN_SPAWNER_GUI, 0);
        } else {
            setState(State.WAIT_SPAWNER_GUI, 5);
        }
    }

    private void handleInSpawnerGui() {
        if (!(mc.currentScreen instanceof HandledScreen screen)) {
            setState(State.IDLE, 0);
            return;
        }

        if (!isSpawnerGui(screen)) {
            setState(State.IDLE, 0);
            return;
        }

        // Check for arrows (abort condition)
        if (hasArrowsInGui(screen)) {
            if (notifications.get()) info("Arrows detected in spawner - aborting.");
            mc.setScreen(null);
            toggle();
            return;
        }

        if (!tickDelay()) return;

        if (pagesDropped < pagesPerLoad.get()) {
            // Drop loot on this page
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
            setWaitTimer(msToTicks(clickDelayMs.get()));
            // After delay, go to next page
            pagesDropped++;
            // Schedule: click NEXT after another delay
            new Thread(() -> {
                try { Thread.sleep(clickDelayMs.get()); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    if (mc.currentScreen instanceof HandledScreen s && isSpawnerGui(s)) {
                        mc.interactionManager.clickSlot(s.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                    }
                });
            }).start();
        } else {
            // Done with all pages – close spawner
            if (notifications.get()) info("Dropped " + pagesPerLoad.get() + " pages. Closing spawner.");
            mc.setScreen(null);
            setState(State.WAIT_BONE_PICKUP, pickupWaitTicks.get());
        }
    }

    private void handleWaitBonePickup() {
        if (!timerExpired()) return;
        setState(State.CHECK_INVENTORY, 0);
    }

    private void handleCheckInventory() {
        if (countBonesInInventory() > 0) {
            setState(State.SEND_ORDER_CMD, 0);
        } else {
            if (notifications.get()) info("No bones in inventory – reloading spawner.");
            setState(State.IDLE, 0);
        }
    }

    private void handleSendOrderCmd() {
        String cmd = orderToSpecific.get() && !specificPlayer.get().isEmpty()
            ? "order " + specificPlayer.get()
            : "order";
        mc.player.networkHandler.sendChatCommand(cmd);
        if (notifications.get()) info("Sent /" + cmd);
        setState(State.WAIT_ORDER_GUI, 40);
    }

    private void handleWaitOrderGui() {
        if (!timerExpired()) return;
        if (mc.currentScreen instanceof HandledScreen screen && isOrderGui(screen)) {
            setState(State.IN_ORDER_GUI, 0);
        } else {
            setState(State.WAIT_ORDER_GUI, 5);
        }
    }

    private void handleInOrderGui() {
        if (!(mc.currentScreen instanceof HandledScreen screen) || !isOrderGui(screen)) {
            setState(State.IDLE, 0);
            return;
        }
        if (!tickDelay()) return;

        int targetSlot = -1;

        if (orderToSpecific.get() && !specificPlayer.get().isEmpty()) {
            // With /order NAME the first bone (slot 0) should be their order
            targetSlot = findFirstBoneSlot(screen);
        } else if (orderMode.get() == OrderMode.FIRST) {
            targetSlot = findFirstBoneSlot(screen);
        } else {
            targetSlot = findBestRatioSlot(screen);
        }

        if (targetSlot == -1) {
            if (notifications.get()) info("No valid order found – going back to IDLE.");
            mc.setScreen(null);
            setState(State.IDLE, 0);
            return;
        }

        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
        setState(State.WAIT_DELIVERY_GUI, 40);
    }

    private void handleWaitDeliveryGui() {
        if (!timerExpired()) return;
        if (!(mc.currentScreen instanceof HandledScreen screen)) {
            setState(State.WAIT_DELIVERY_GUI, 5);
            return;
        }
        // If the confirm GUI opened immediately (some servers skip delivery container)
        if (isConfirmGui(screen)) {
            setState(State.CONFIRMING, 0);
            return;
        }
        // Otherwise it's the delivery container
        if (!isOrderGui(screen) && !isSpawnerGui(screen)) {
            setState(State.IN_DELIVERY_CONTAINER, 0);
        } else {
            setState(State.WAIT_DELIVERY_GUI, 5);
        }
    }

    private void handleInDeliveryContainer() {
        if (!(mc.currentScreen instanceof HandledScreen screen)) {
            setState(State.IDLE, 0);
            return;
        }
        if (isConfirmGui(screen)) {
            setState(State.CONFIRMING, 0);
            return;
        }
        if (!tickDelay()) return;

        // Quickmove all bones from player inventory into delivery container
        int containerSize = screen.getScreenHandler().slots.size() - 36;
        boolean movedAny = false;
        for (int i = containerSize; i < screen.getScreenHandler().slots.size(); i++) {
            ItemStack s = screen.getScreenHandler().getSlot(i).getStack();
            if (s.getItem() == Items.BONE) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                movedAny = true;
            }
        }

        if (!movedAny) {
            if (notifications.get()) info("No bones to deposit – closing delivery container.");
        }

        // Close container → server should send confirm GUI
        mc.player.closeHandledScreen();
        setState(State.WAIT_CONFIRM_GUI, 30);
    }

    private void handleWaitConfirmGui() {
        if (!timerExpired()) return;
        if (mc.currentScreen instanceof HandledScreen screen && isConfirmGui(screen)) {
            setState(State.CONFIRMING, 0);
        } else {
            setState(State.WAIT_CONFIRM_GUI, 5);
        }
    }

    private void handleConfirming() {
        if (!(mc.currentScreen instanceof HandledScreen screen) || !isConfirmGui(screen)) {
            setState(State.WAIT_AFTER_CONFIRM, 20);
            return;
        }
        if (!tickDelay()) return;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 15, 0, SlotActionType.PICKUP, mc.player);
        if (notifications.get()) info("Confirmed delivery.");
        setState(State.WAIT_AFTER_CONFIRM, 20);
    }

    private void handleWaitAfterConfirm() {
        if (!timerExpired()) return;
        if (countBonesInInventory() > 0) {
            // Still have bones – order again
            setState(State.SEND_ORDER_CMD, 0);
        } else {
            if (notifications.get()) info("All bones delivered. Going back to load spawner.");
            setState(State.IDLE, 0);
        }
    }

    // ── GUI Detection ─────────────────────────────────────────────────────────

    /** Spawner loot GUI: dropper at slot 50 */
    private boolean isSpawnerGui(HandledScreen<?> screen) {
        int slots = screen.getScreenHandler().slots.size();
        if (slots <= 50) return false;
        return screen.getScreenHandler().getSlot(50).getStack().getItem() == Items.DROPPER;
    }

    /** Order list GUI: map at slot 49, arrow at slot 53 */
    private boolean isOrderGui(HandledScreen<?> screen) {
        int slots = screen.getScreenHandler().slots.size();
        if (slots <= 53) return false;
        boolean hasMap   = screen.getScreenHandler().getSlot(49).getStack().getItem() == Items.FILLED_MAP
                        || screen.getScreenHandler().getSlot(49).getStack().getItem() == Items.MAP;
        boolean hasArrow = screen.getScreenHandler().getSlot(53).getStack().getItem() == Items.ARROW;
        return hasMap || hasArrow;
    }

    /** Confirm GUI: lime stained glass at slot 15 */
    private boolean isConfirmGui(HandledScreen<?> screen) {
        int slots = screen.getScreenHandler().slots.size();
        if (slots <= 15) return false;
        return screen.getScreenHandler().getSlot(15).getStack().getItem() == Items.LIME_STAINED_GLASS_PANE;
    }

    // ── Order Slot Selection ──────────────────────────────────────────────────

    private int findFirstBoneSlot(HandledScreen<?> screen) {
        for (int i = 0; i <= 44; i++) {
            if (i >= screen.getScreenHandler().slots.size()) break;
            if (screen.getScreenHandler().getSlot(i).getStack().getItem() == Items.BONE) return i;
        }
        return -1;
    }

    /**
     * Finds the slot with the best score = price * remaining_capacity.
     * This favours high-paying orders with large remaining capacity,
     * so a 500K-bone order at $190 beats a 10K-bone order at $200.
     */
    private int findBestRatioSlot(HandledScreen<?> screen) {
        double bestScore = -1;
        int bestSlot = -1;

        for (int i = 0; i <= 44; i++) {
            if (i >= screen.getScreenHandler().slots.size()) break;
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (stack.getItem() != Items.BONE) continue;

            double price     = parsePriceFromLore(stack);
            double remaining = parseRemainingFromLore(stack);
            if (price <= 0) continue;

            double score = price * remaining;
            if (score > bestScore) {
                bestScore = score;
                bestSlot  = i;
            }
        }
        return bestSlot;
    }

    // ── Lore Parsing ──────────────────────────────────────────────────────────

    /**
     * Extracts price-per-item from lore lines like "$190 each" or "$165.02 each".
     */
    private double parsePriceFromLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return 0;
        for (var line : lore.lines()) {
            String text = line.getString();
            Matcher m = PRICE_PATTERN.matcher(text);
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    /**
     * Extracts remaining capacity from lore lines like "81.99K/100K Delivered".
     * Returns (total - delivered), i.e. how many more items the order can absorb.
     */
    private double parseRemainingFromLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return 0;
        for (var line : lore.lines()) {
            String text = line.getString();
            Matcher m = PROGRESS_PATTERN.matcher(text);
            if (m.find()) {
                double delivered = parseShortNumber(m.group(1));
                double total     = parseShortNumber(m.group(2));
                return Math.max(0, total - delivered);
            }
        }
        return 1; // fallback: treat as non-zero so first-only mode still works
    }

    /** Parses numbers like "1K", "1.5M", "100", "81.99K" to their double value. */
    private double parseShortNumber(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        char last = s.charAt(s.length() - 1);
        double multiplier = 1;
        if (last == 'K' || last == 'k') { multiplier = 1_000;     s = s.substring(0, s.length() - 1); }
        else if (last == 'M' || last == 'm') { multiplier = 1_000_000; s = s.substring(0, s.length() - 1); }
        try { return Double.parseDouble(s.replace(",", "")) * multiplier; }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Inventory Helpers ─────────────────────────────────────────────────────

    private int countBonesInInventory() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.BONE) count += s.getCount();
        }
        return count;
    }

    private boolean hasArrowsInGui(HandledScreen<?> screen) {
        for (int i = 0; i <= 44; i++) {
            if (i >= screen.getScreenHandler().slots.size()) break;
            if (screen.getScreenHandler().getSlot(i).getStack().getItem() == Items.ARROW) return true;
        }
        return false;
    }

    // ── Spawner Search ────────────────────────────────────────────────────────

    private BlockPos findNearbySpawner() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos origin = mc.player.getBlockPos();
        for (int x = -6; x <= 6; x++)
            for (int y = -6; y <= 6; y++)
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) return pos;
                }
        return null;
    }

    // ── Rotation (inline, no utility) ─────────────────────────────────────────

    private void rotateToward(Vec3d target) {
        if (mc.player == null) return;
        Vec3d eyes  = mc.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        double h    = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw   = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, h)));

        // Smooth toward target (3 degrees per tick max)
        float dy = MathHelper.wrapDegrees(yaw   - mc.player.getYaw());
        float dp = MathHelper.clamp(pitch - mc.player.getPitch(), -20f, 20f);
        mc.player.setYaw(mc.player.getYaw()   + MathHelper.clamp(dy, -20f, 20f));
        mc.player.setPitch(mc.player.getPitch() + dp);
    }

    // ── Bone Entity Suppression ────────────────────────────────────────────────

    private void suppressBoneEntities() {
        if (!hideBones.get() || mc.world == null) return;
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity ie && ie.getStack().getItem() == Items.BONE) {
                ie.setInvisible(true);
            }
        }
    }

    // ── Timing Helpers ────────────────────────────────────────────────────────

    /**
     * Returns true once per call after tickTimer reaches the click delay.
     * Resets tickTimer on success.
     */
    private boolean tickDelay() {
        tickTimer++;
        if (tickTimer >= msToTicks(clickDelayMs.get())) {
            tickTimer = 0;
            return true;
        }
        return false;
    }

    private void setWaitTimer(int ticks) { waitTimer = ticks; }

    private boolean timerExpired() {
        if (waitTimer > 0) { waitTimer--; return false; }
        return true;
    }

    private void setState(State newState, int waitTicks) {
        state     = newState;
        waitTimer = waitTicks;
        tickTimer = 0;
    }

    private int msToTicks(int ms) {
        return Math.max(1, ms / 50);
    }
}
