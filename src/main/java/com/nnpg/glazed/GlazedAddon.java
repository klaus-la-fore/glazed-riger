package com.nnpg.glazed; // Corrected package

import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.main.*; // This includes GamblingDropperRig automatically
import com.nnpg.glazed.modules.pvp.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

public class GlazedAddon extends MeteorAddon {

    public static final Category CATEGORY = new Category("Glazed", new ItemStack(Items.CAKE));
    public static final Category esp = new Category("Glazed ESP ", new ItemStack(Items.VINE));
    public static final Category pvp = new Category("Glazed PVP", new ItemStack(Items.DIAMOND_SWORD));

    public static int MyScreenVERSION = 16;

    @Override
    public void onInitialize() {
        // ... (keep all your existing module registrations)
        Modules.get().add(new AutoTreeFarmer());
        Modules.get().add(new GamblingDropperRig()); // Now it will find it
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(esp);
        Modules.registerCategory(pvp);
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }
}
