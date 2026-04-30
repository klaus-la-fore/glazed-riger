package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting;
import com.nnpg.glazed.GlazedAddon;

public class GamblingDropperRig extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Integer> forcedSeed = sgGeneral.add(new IntSetting.Builder()
        .name("forced-seed")
        .description("The seed to force for the dropper.")
        .defaultValue(0xDEADBEEF)
        .build());

    public GamblingDropperRig() {
        super(GlazedAddon.CATEGORY, "gambling-dropper-rig", "Forces RNG seed for DonutSMP droppers.");
    }

    public int getRiggedSeed() {
        return forcedSeed.get();
    }
}
