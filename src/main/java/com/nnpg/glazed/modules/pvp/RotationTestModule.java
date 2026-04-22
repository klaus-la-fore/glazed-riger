package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.glazed.RotationUtil;
import com.nnpg.glazed.utils.glazed.RotationUtil.CurveType;
import com.nnpg.glazed.utils.glazed.RotationUtil.RotationConfig;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RotationTestModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<CurveType> curve = sgGeneral.add(new EnumSetting.Builder<CurveType>()
        .name("curve")
        .description("Smoothing curve for the rotation animation.")
        .defaultValue(CurveType.ACCELERATION)
        .build()
    );

    private final Setting<Double> maxDegreesPerTick = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-degrees-per-tick")
        .description("Hard cap on rotation change per tick in degrees.")
        .defaultValue(30.0)
        .min(1.0)
        .sliderMax(45.0)
        .build()
    );

    private final Setting<Double> yawAccelMin = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-accel-min")
        .description("Minimum yaw acceleration range per tick.")
        .defaultValue(20.0)
        .min(1.0)
        .sliderMax(60.0)
        .build()
    );

    private final Setting<Double> yawAccelMax = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-accel-max")
        .description("Maximum yaw acceleration range per tick.")
        .defaultValue(25.0)
        .min(1.0)
        .sliderMax(60.0)
        .build()
    );

    private final Setting<Double> pitchAccelMin = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-accel-min")
        .description("Minimum pitch acceleration range per tick.")
        .defaultValue(20.0)
        .min(1.0)
        .sliderMax(60.0)
        .build()
    );

    private final Setting<Double> pitchAccelMax = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-accel-max")
        .description("Maximum pitch acceleration range per tick.")
        .defaultValue(25.0)
        .min(1.0)
        .sliderMax(60.0)
        .build()
    );

    private final Setting<Double> yawAccelError = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-accel-error")
        .description("Proportional random error on yaw per tick.")
        .defaultValue(0.1)
        .min(0.0)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Double> yawConstError = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-const-error")
        .description("Constant random micro-jitter on yaw per tick.")
        .defaultValue(0.1)
        .min(0.0)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Boolean> inputBlend = sgGeneral.add(new BoolSetting.Builder()
        .name("input-blend")
        .description("Mix player mouse input with the utility rotation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> inputBlendWeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("input-blend-weight")
        .description("0 = utility only, 1 = player only.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderMax(1.0)
        .visible(() -> inputBlend.get())
        .build()
    );

    private final RotationUtil rotationUtil = new RotationUtil();

    private BlockPos pendingTarget  = null;
    private int      delayTicksLeft = 0;

    /**
     * When true: the movement packet for this tick has been sent, so we are
     * safe to send a block interaction packet without rotation desync.
     */
    private volatile boolean movementPacketSent = false;
    private BlockPos interactPendingPost = null;

    public RotationTestModule() {
        super(GlazedAddon.pvp, "rotation-test",
            "Rotates toward a right-clicked block after a 2-second delay. Tests RotationUtil.");
    }

    @Override
    public void onActivate() {
        pendingTarget       = null;
        delayTicksLeft      = 0;
        interactPendingPost = null;
        movementPacketSent  = false;
        rotationUtil.cancel();
    }

    @Override
    public void onDeactivate() {
        rotationUtil.cancel();
        pendingTarget       = null;
        delayTicksLeft      = 0;
        interactPendingPost = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        movementPacketSent = false;

        if (rotationUtil.isActive()) {
            boolean done = rotationUtil.tick();
            if (done && interactPendingPost != null) {
                pendingTarget = null;
            }
            return;
        }

        if (pendingTarget == null) {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult bhr && mc.options.useKey.isPressed()) {
                pendingTarget  = bhr.getBlockPos();
                delayTicksLeft = 40;
            }
            return;
        }

        if (delayTicksLeft > 0) {
            delayTicksLeft--;
            return;
        }

        Vec3d blockCenter = Vec3d.ofCenter(pendingTarget);
        Vec3d eyePos      = mc.player.getEyePos();
        Vec3d delta       = blockCenter.subtract(eyePos);
        double horizDist  = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw   = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizDist)));

        RotationConfig cfg = new RotationConfig.Builder()
            .curve(curve.get())
            .maxDegreesPerTick(maxDegreesPerTick.get().floatValue())
            .yawAccel(yawAccelMin.get().floatValue(),   yawAccelMax.get().floatValue())
            .pitchAccel(pitchAccelMin.get().floatValue(), pitchAccelMax.get().floatValue())
            .yawAccelError(yawAccelError.get().floatValue())
            .yawConstError(yawConstError.get().floatValue())
            .pitchAccelError(yawAccelError.get().floatValue())
            .pitchConstError(yawConstError.get().floatValue())
            .inputBlendWeight(inputBlend.get() ? inputBlendWeight.get().floatValue() : 0f)
            .build();

        rotationUtil.start(targetYaw, targetPitch, cfg);
        interactPendingPost = pendingTarget;
    }

    /**
     * Watch for outgoing movement packets. Once vanilla sends the movement packet
     * with the rotated yaw, it is safe to send the block interaction.
     * This mirrors the PostRotationExecutor post-move pattern from LiquidBounce.
     */
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket)) return;
        if (interactPendingPost == null) return;
        if (movementPacketSent) return;

        movementPacketSent = true;

        BlockPos pos = interactPendingPost;
        interactPendingPost = null;

        mc.execute(() -> {
            if (mc.player == null || mc.interactionManager == null) return;
            ClientPlayerInteractionManager im = mc.interactionManager;
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) {
                im.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND, bhr);
            }
            pendingTarget = null;
        });
    }
}
