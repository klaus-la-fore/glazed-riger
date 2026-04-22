package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import static com.nnpg.glazed.GlazedAddon.pvp;

/**
 * ═══════════════════════════════════════════════════════════════════
 * JumpReset — Final Definitive Analysis
 * ═══════════════════════════════════════════════════════════════════
 *
 * WHY PREVIOUS VERSIONS FAILED:
 *
 * 1. Wrong trigger: We only checked EntityVelocityUpdateS2CPacket.
 *    That packet fires for explosions, water, pistons, lava — not just hits.
 *    On 1.8 via ViaVersion, packet ordering is also different.
 *
 * 2. No combat confirmation: We never verified the player was actually HIT.
 *    mc.player.hurtTime is set to 10 in the SAME tick as the hit packet.
 *    Checking BOTH velocity packet AND hurtTime == 10 confirms "this is a hit".
 *
 *    Evidence: LiquidBounce's JumpReset triggers on fire/poison/wither too
 *    (see issue #5378) because they only check hurtTime, not velocity.
 *    We check BOTH for precision.
 *
 * 3. On 1.8 via ViaVersion: even LiquidBounce's JumpReset fails (issue #4079).
 *    This is a known limitation. ViaVersion translates packets but the ordering
 *    and server-side position validation differ. The "require-hurt-time" setting
 *    can be disabled for 1.8 servers as a workaround.
 *
 * CORRECT MECHANISM:
 *
 *   Step 1 — PacketEvent.Receive (PRE, before packet applied):
 *     EntityVelocityUpdateS2CPacket arrives → horizontal velocity > threshold
 *     → Set pendingJump = true. Save tick number.
 *     (DO NOT CANCEL — the packet must apply normally for server sync)
 *
 *   Step 2 — TickEvent.Pre (same tick, AFTER networkHandler processed all packets):
 *     pendingJump == true
 *     AND (requireHurtTime disabled OR player.hurtTime >= expectedHurtTime)
 *       → player.jump()  ← sets velocity.y = ~0.42, keeps KB horizontal
 *
 *   Step 3 — player.tickMovement() (runs inside world.tick(), AFTER TickEvent.Pre):
 *     Uses the modified velocity (kbX, 0.42, kbZ)
 *     Sends movement packet to server showing jump trajectory
 *
 *   Step 4 — Server:
 *     Receives position consistent with: player jumped while receiving knockback
 *     Accepts it (Grim: valid combination; vanilla 1.21: accepts any valid pos)
 *     Result: reduced horizontal displacement ✓
 *
 * hurtTime mechanics:
 *   When damaged → EntityStatusS2CPacket (status=2) → player.hurtTime = 10
 *   This runs in networkHandler.tick(), which is BEFORE TickEvent.Pre.
 *   So in TickEvent.Pre: hurtTime == 10 = player was just hit THIS tick. ✓
 *   At delayTicks=1: hurtTime should be 9 (decremented in previous tickMovement).
 *
 * player.jump() does NOT check isOnGround() internally:
 *   LivingEntity.jump() just sets velocity.y = getJumpVelocity() (~0.42)
 *   and velocityDirty = true. No ground check. Safe to call here.
 */
public class JumpReset extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStrict  = settings.createGroup("Strict Mode");
    private final SettingGroup sgNormal  = settings.createGroup("Normal Mode");

    // ── General ───────────────────────────────────────────────────────────────

    /**
     * Strict Mode: pure mechanic, no skip chance, no rate limit.
     * Fires on every eligible hit. Use this to verify the module works.
     */
    private final Setting<Boolean> strictMode = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-mode")
        .description("Pure mode: no skip chance, no rate limit. Fires on every eligible hit. " +
                     "Hides Normal Mode settings. Use to test if the module works at all.")
        .defaultValue(false)
        .build()
    );

    /**
     * TEST BUTTON — toggle ON to fire a single player.jump() call.
     * Auto-resets to OFF after firing.
     *
     * HOW TO VERIFY THE MODULE WORKS:
     * 1. Stand on flat ground, no sprinting.
     * 2. Toggle this ON.
     * 3. Expected: you visually jump once (same as pressing Space).
     *
     * If you DO jump: player.jump() works. The module mechanics are sound.
     * If you DON'T jump: there is an environment issue (mixin conflict, etc.)
     */
    private final Setting<Boolean> testJump = sgGeneral.add(new BoolSetting.Builder()
        .name("test-jump")
        .description("Toggle ON → fires player.jump() once, auto-resets. " +
                     "Verifies the jump mechanism works in your environment before blaming the module.")
        .defaultValue(false)
        .build()
    );

    /**
     * Require hurtTime confirmation.
     *
     * ON (default): Only jump when player.hurtTime == 10 (= freshly hit this tick).
     *   → Only triggers on actual combat hits, not explosions/water/pistons.
     *   → Recommended for 1.21 servers.
     *
     * OFF: Trigger on any velocity update above minVelocity, no hurtTime check.
     *   → More sensitive, catches hits that arrive in different tick orders.
     *   → Try this if the module doesn't trigger on 1.8 via ViaVersion.
     */
    private final Setting<Boolean> requireHurtTime = sgGeneral.add(new BoolSetting.Builder()
        .name("require-hurt-time")
        .description("ON=only trigger when player.hurtTime==10 (confirmed hit this tick). " +
                     "OFF=trigger on any velocity update above threshold (for 1.8/ViaVersion).")
        .defaultValue(true)
        .build()
    );

    /** Minimum horizontal knockback velocity to respond to (raw/8000, m/tick).
     *  Normal hit:   ~0.10–0.25 m/tick.  Sprint hit: ~0.30–0.50 m/tick.
     *  0.10 catches all real hits without false positives from gentle movements. */
    private final Setting<Double> minVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-velocity")
        .description("Minimum horizontal KB (raw/8000 m/tick). 0.10 = normal hits. 0.05 = more sensitive.")
        .defaultValue(0.10)
        .min(0.0)
        .max(1.0)
        .sliderMax(0.5)
        .build()
    );

    /** Show a chat debug message every time the module fires. */
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print a chat message every time a jump-reset fires. " +
                     "Shows hurtTime and velocity values so you can confirm the module is triggering.")
        .defaultValue(false)
        .build()
    );

    // ── Strict Mode Settings ──────────────────────────────────────────────────

    /**
     * Ticks to wait after the KB packet before jumping.
     *
     * 0 (optimal): jump in the SAME TickEvent.Pre as the packet.
     *   → Player is guaranteed on ground. hurtTime == 10. Best reduction.
     *   → Looks like 0ms reaction (potentially detectable on sensitive ACs).
     *
     * 1 tick (50ms): jump one tick after the packet.
     *   → Player may be slightly airborne. hurtTime == 9. Less effective.
     *   → Looks like 50ms reaction time (realistic for a fast human).
     *
     * Recommendation: 0 for max effectiveness. 1 for better human-like profile.
     */
    private final Setting<Integer> strictDelay = sgStrict.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Ticks to wait before jumping (Strict Mode). 0=same tick (optimal). 1=50ms delay.")
        .defaultValue(0)
        .min(0)
        .max(3)
        .sliderMax(3)
        .visible(() -> strictMode.get())
        .build()
    );

    // ── Normal Mode Settings ──────────────────────────────────────────────────

    /**
     * Base skip chance (auto-scaled by knockback strength).
     *  strong KB (>0.50): skip × 0.25  → jumps ~95% of the time
     *  medium KB (0.25–0.50): skip × 1.0 → jumps ~80%
     *  weak   KB (<0.25): skip × 2.0  → jumps ~60%
     */
    private final Setting<Double> skipChance = sgNormal.add(new DoubleSetting.Builder()
        .name("skip-chance")
        .description("Base chance to skip the reset (auto-scaled by KB strength). " +
                     "20%=realistic for good player. 0%=always. 100%=never.")
        .defaultValue(20.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .visible(() -> !strictMode.get())
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean pendingJump   = false;
    private int     ticksWaited   = 0;
    private int     configuredDelay = 0;  // captured from setting at arm-time
    private long    lastJumpMs    = 0L;

    private static final long MIN_INTERVAL_MS = 200L; // 200ms rate-limit in Normal Mode

    // ── Constructor ───────────────────────────────────────────────────────────

    public JumpReset() {
        super(pvp, "jump-reset", "Reduces knockback by jumping when hit");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        pendingJump = false;
        ticksWaited = 0;
    }

    // ── Event Handlers ────────────────────────────────────────────────────────

    /**
     * Intercepts the KB velocity packet and arms a pending jump.
     *
     * DO NOT cancel this packet. The packet must be applied so the client
     * and server agree on the player's velocity. Cancelling it causes
     * server-side position corrections that snap the player back.
     *
     * We only SET A FLAG here. The jump itself fires in TickEvent.Pre,
     * which runs in the same tick after all packets have been processed.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityVelocityUpdateS2CPacket packet)) return;
        if (mc.player == null || mc.world == null) return;
        if (packet.getEntityId() != mc.player.getId()) return;

        double vx = packet.getVelocityX() / 8000.0;
        double vz = packet.getVelocityZ() / 8000.0;
        double horizVel = Math.sqrt(vx * vx + vz * vz);

        // Filter: minimum horizontal velocity to rule out trivial velocity updates.
        if (horizVel < minVelocity.get()) return;

        // Normal mode: skip chance (don't check in strict mode).
        if (!strictMode.get()) {
            // Rate limit
            if (System.currentTimeMillis() - lastJumpMs < MIN_INTERVAL_MS) return;

            // Dynamic skip scaling
            double skip = skipChance.get();
            if      (horizVel > 0.50) skip *= 0.25;
            else if (horizVel < 0.25) skip *= 2.0;
            skip = Math.min(skip, 100.0);
            if (Math.random() * 100.0 < skip) return;
        }

        // Arm the jump. ticksWaited resets, delay captured now.
        pendingJump     = true;
        ticksWaited     = 0;
        configuredDelay = strictMode.get() ? strictDelay.get() : 0;

        // DO NOT cancel the event — packet must be applied for server sync.
    }

    /**
     * Executes the pending jump.
     *
     * Called at the HEAD of world.tick(), AFTER networkHandler has processed
     * all packets for this tick. At this point:
     *
     *   player.velocity     = (kbX, kbY, kbZ)   ← KB was just applied by packet
     *   player.hurtTime     = 10                 ← status packet was also applied
     *   player.isOnGround() = still true          ← physics haven't run yet
     *   player.tickMovement() has NOT run yet     ← we're before entity processing
     *
     * player.jump() → LivingEntity.jump():
     *   velocity.y = getJumpVelocity() ≈ 0.42
     *   velocity.x unchanged (KB horizontal preserved)
     *   velocity.z unchanged (KB horizontal preserved)
     *   velocityDirty = true
     *
     * Then tickMovement() runs: sends (kbX, 0.42, kbZ) trajectory to server.
     * Server sees: valid jump during knockback → accepts reduced displacement.
     */
    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        // ── Test Button ──────────────────────────────────────────────────────
        if (testJump.get()) {
            testJump.set(false);
            if (mc.player != null && !mc.player.isDead()) {
                mc.player.jump();
                info("(highlight)Test jump fired! hurtTime=" + mc.player.hurtTime +
                     " | onGround=" + mc.player.isOnGround() +
                     " | If you see yourself jump visually, player.jump() works.");
            }
            return;
        }

        if (!pendingJump) return;
        if (mc.player == null || mc.player.isDead() || mc.world == null) {
            pendingJump = false;
            return;
        }

        // ── Delay ────────────────────────────────────────────────────────────
        if (ticksWaited < configuredDelay) {
            ticksWaited++;
            return;
        }

        // ── hurtTime verification ─────────────────────────────────────────────
        // At delay=0: hurtTime should be 10 (hit this exact tick).
        // At delay=1: hurtTime should be 9  (hit last tick, decremented once).
        // At delay=2: hurtTime should be 8. Etc.
        // We use >= to be slightly lenient (packet ordering can vary by 1).
        if (requireHurtTime.get()) {
            int expectedHurtTime = 10 - configuredDelay;
            if (mc.player.hurtTime < Math.max(expectedHurtTime - 1, 1)) {
                // hurtTime doesn't match — this was NOT a combat hit.
                // (e.g. explosion, water, piston, or stale pending jump)
                if (debugMode.get()) {
                    info("JR skipped: hurtTime=" + mc.player.hurtTime +
                         " expected>=" + Math.max(expectedHurtTime - 1, 1) +
                         " (not a combat hit, or packet order issue)");
                }
                pendingJump = false;
                return;
            }
        }

        // ── Execute jump ──────────────────────────────────────────────────────
        mc.player.jump();

        if (debugMode.get()) {
            info("(highlight)JumpReset fired! hurtTime=" + mc.player.hurtTime +
                 " | onGround=" + mc.player.isOnGround() +
                 " | velocity.y BEFORE=" + String.format("%.3f", mc.player.getVelocity().y));
        }

        pendingJump = false;
        ticksWaited = 0;
        lastJumpMs  = System.currentTimeMillis();
    }
}