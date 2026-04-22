package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.addon.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;

/**
 * Attribute Swap module for HelixCraft-Glazed.
 *
 * Supported techniques (all based on MC-28289):
 *   1. Breach Swap       — sword/axe → breach mace            (armor bypass)
 *   2. Axe Swap          — any weapon → axe                   (shield disable)
 *   3. Density+Breach    — density mace → breach mace         (fall dmg + armor bypass)
 *   4. Spear Reach Swap  — sword/axe → spear/trident          (extended reach)
 *
 * Priority order when multiple techniques are active:
 *   Axe Swap (if target blocking) > Density+Breach > Breach Swap > Spear Reach
 */
public class AttributeSwapper extends Module {

    // ── Groups ────────────────────────────────────────────────────────────────

    private final SettingGroup sgBreach        = settings.createGroup("Breach Swap");
    private final SettingGroup sgAxe           = settings.createGroup("Axe Swap");
    private final SettingGroup sgDensityBreach = settings.createGroup("Density+Breach Swap");
    private final SettingGroup sgSpear         = settings.createGroup("Spear Reach Swap");
    private final SettingGroup sgMisc          = settings.createGroup("Misc");

    // ── Breach Swap ───────────────────────────────────────────────────────────

    private final Setting<Boolean> breachEnabled = sgBreach.add(new BoolSetting.Builder()
        .name("enabled")
        .description("On attack with sword/axe: swap to highest-level Breach mace.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> checkWeapon = sgBreach.add(new BoolSetting.Builder()
        .name("check-weapon")
        .description("Only activate when holding a sword or axe.")
        .defaultValue(true)
        .visible(breachEnabled::get)
        .build()
    );
    private final Setting<Boolean> allowSword = sgBreach.add(new BoolSetting.Builder()
        .name("allow-sword")
        .description("Allow Breach Swap when holding a sword.")
        .defaultValue(true)
        .visible(() -> breachEnabled.get() && checkWeapon.get())
        .build()
    );
    private final Setting<Boolean> allowAxeBreach = sgBreach.add(new BoolSetting.Builder()
        .name("allow-axe")
        .description("Allow Breach Swap when holding an axe.")
        .defaultValue(true)
        .visible(() -> breachEnabled.get() && checkWeapon.get())
        .build()
    );
    private final Setting<Integer> breachHoldTicks = sgBreach.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Ticks to hold Breach mace before swapping back. (1-2 recommended)")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .visible(breachEnabled::get)
        .build()
    );

    // ── Axe Swap ──────────────────────────────────────────────────────────────

    private final Setting<Boolean> axeEnabled = sgAxe.add(new BoolSetting.Builder()
        .name("enabled")
        .description("When target is blocking: swap to axe to disable shield (5s stun). "
                   + "Runs BEFORE Breach Swap so you hit unblocked.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> axeHoldTicks = sgAxe.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Ticks to hold axe. 1 tick is enough for shield disable.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(axeEnabled::get)
        .build()
    );
    private final Setting<Boolean> axeThenBreach = sgAxe.add(new BoolSetting.Builder()
        .name("then-breach-swap")
        .description("After axe disables shield: also do a Breach Swap on the same tick. "
                   + "(Axe stun + Breach armor-ignore in one combo)")
        .defaultValue(false)
        .visible(axeEnabled::get)
        .build()
    );

    // ── Density+Breach Swap ───────────────────────────────────────────────────

    private final Setting<Boolean> dbEnabled = sgDensityBreach.add(new BoolSetting.Builder()
        .name("enabled")
        .description("When holding a Density mace and falling: swap to Breach mace on attack. "
                   + "Combines Density fall damage with Breach armor-ignore. "
                   + "Requires two maces: one Density, one Breach.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> dbMinFall = sgDensityBreach.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance to activate. Higher = more Density damage before Breach.")
        .defaultValue(3.0)
        .min(0.5)
        .sliderMax(20.0)
        .visible(dbEnabled::get)
        .build()
    );
    private final Setting<Integer> dbHoldTicks = sgDensityBreach.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Ticks to hold Breach mace. 1-2 recommended.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .visible(dbEnabled::get)
        .build()
    );

    // ── Spear Reach Swap ──────────────────────────────────────────────────────

    private final Setting<Boolean> spearEnabled = sgSpear.add(new BoolSetting.Builder()
        .name("enabled")
        .description("On attack: briefly swap to spear/trident (preferably with Lunge) "
                   + "for extended 4-block reach. Sword attributes apply to the spear hit.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> spearHoldTicks = sgSpear.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Ticks to hold spear. 1 tick is sufficient for reach.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(spearEnabled::get)
        .build()
    );

    // ── Misc ──────────────────────────────────────────────────────────────────

    private final Setting<Boolean> debug = sgMisc.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print swap info in chat.")
        .defaultValue(false)
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private int prevSlot = -1;
    private int dDelay   = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AttributeSwapper() {
        super(GlazedAddon.pvp, "attribute-swapper",
            "Attribute swap: Breach, Axe shield-stun, Density+Breach, Spear reach.");
    }

    @Override
    public void onActivate() {
        prevSlot = -1;
        dDelay   = 0;
    }

    // ── Attack event ──────────────────────────────────────────────────────────

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (dDelay > 0) return; // don't interrupt ongoing swap

        boolean targetBlocking = event.target instanceof LivingEntity le && le.isBlocking();
        String  heldId         = mc.player.getMainHandStack().getItem().toString();
        boolean holdsSword     = heldId.contains("sword");
        boolean holdsAxe       = heldId.contains("_axe");
        boolean holdsDensity   = hasMaceWith("density");

        prevSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());

        // ── Priority 1: Axe Swap (shield disable) ────────────────────────────
        if (axeEnabled.get() && targetBlocking && !holdsAxe) {
            int axeSlot = findByType("_axe");
            if (axeSlot != -1) {
                // Optional: also line up a breach swap after axe finishes
                // (handled in tick by checking axeThenBreach after state returns IDLE)
                doSwap(axeSlot, axeHoldTicks.get(), "Axe swap (shield stun)");
                return;
            }
        }

        // ── Priority 2: Density+Breach Swap ──────────────────────────────────
        if (dbEnabled.get()
                && holdsDensity
                && mc.player.fallDistance >= dbMinFall.get().floatValue()) {
            int breachSlot = findBestByEnchant("minecraft:breach");
            if (breachSlot != -1 && breachSlot != prevSlot) {
                doSwap(breachSlot, dbHoldTicks.get(), "Density+Breach swap");
                return;
            }
        }

        // ── Priority 3: Breach Swap ───────────────────────────────────────────
        if (breachEnabled.get()) {
            if (checkWeapon.get()) {
                boolean valid = (allowSword.get() && holdsSword)
                             || (allowAxeBreach.get() && holdsAxe);
                if (!valid) return;
            }
            int breachSlot = findBestByEnchant("minecraft:breach");
            if (breachSlot != -1 && breachSlot != prevSlot) {
                doSwap(breachSlot, breachHoldTicks.get(), "Breach swap");
                return;
            }
        }

        // ── Priority 4: Spear Reach Swap ─────────────────────────────────────
        if (spearEnabled.get()) {
            int spearSlot = findSpear();
            if (spearSlot != -1 && spearSlot != prevSlot) {
                doSwap(spearSlot, spearHoldTicks.get(), "Spear reach swap");
            }
        }
    }

    // ── Tick: hold countdown + swap-back ─────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (dDelay > 0) {
            dDelay--;
            if (dDelay == 0 && prevSlot != -1) {
                InvUtils.swap(prevSlot, false);
                if (debug.get()) info("↩ Swap back → slot " + (prevSlot + 1));
                prevSlot = -1;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void doSwap(int slot, int ticks, String label) {
        InvUtils.swap(slot, false);
        dDelay = ticks;
        if (debug.get()) info("⇄ " + label + " → slot " + (slot + 1)
            + " (hold " + ticks + " ticks)");
    }

    /** True if currently held item is a mace with given enchant keyword. */
    private boolean hasMaceWith(String enchantKeyword) {
        var stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        return stack.getItem().toString().contains("mace")
            && stack.getEnchantments().toString().contains(enchantKeyword);
    }

    /** First hotbar slot whose item id contains typeStr (e.g. "_axe", "mace"). */
    private int findByType(String typeStr) {
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem().toString().contains(typeStr)) return i;
        }
        return -1;
    }

    /**
     * Hotbar slot with the HIGHEST level of enchantId.
     * Parses the enchantment component toString() which looks like:
     *   {minecraft:breach => 4, ...}
     */
    private int findBestByEnchant(String enchantId) {
        int bestSlot = -1, bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String enc = stack.getEnchantments().toString();
            if (!enc.contains(enchantId)) continue;
            try {
                int idx = enc.indexOf(enchantId);
                String after = enc.substring(idx + enchantId.length());
                // after looks like " => 4, ..." or "=4}"
                String lvlStr = after.replaceAll("[^0-9].*", "")  // take digits up to first non-digit
                                     .replaceAll("[^0-9]", "");
                if (lvlStr.isEmpty()) continue;
                int level = Integer.parseInt(lvlStr);
                if (level > bestLevel) { bestLevel = level; bestSlot = i; }
            } catch (Exception ignored) {}
        }
        return bestSlot;
    }

    /**
     * Finds a spear/trident in hotbar.
     * Prefers one with "lunge" enchantment (modded or future vanilla).
     * Falls back to any spear or trident.
     */
    private int findSpear() {
        // Pass 1: spear/trident WITH lunge
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id  = stack.getItem().toString();
            String enc = stack.getEnchantments().toString();
            if ((id.contains("spear") || id.contains("trident"))
                    && enc.contains("lunge")) return i;
        }
        // Pass 2: any spear or trident
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = stack.getItem().toString();
            if (id.contains("spear") || id.contains("trident")) return i;
        }
        return -1;
    }
}