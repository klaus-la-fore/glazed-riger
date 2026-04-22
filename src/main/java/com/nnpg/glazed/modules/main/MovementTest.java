package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.utils.glazed.MovementKeys;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

import static com.nnpg.glazed.GlazedAddon.CATEGORY;

/**
 * MovementTest - Test-Modul für die MovementKeys Utility
 * 
 * Demonstriert die Verwendung der MovementKeys Utility in einem Modul.
 */
public class MovementTest extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Direction> direction = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("direction")
        .description("Bewegungsrichtung")
        .defaultValue(Direction.Forward)
        .build()
    );
    
    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Automatisch springen")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> autoSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sneak")
        .description("Automatisch schleichen")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> autoSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sprint")
        .description("Automatisch sprinten")
        .defaultValue(false)
        .build()
    );
    
    public MovementTest() {
        super(CATEGORY, "movement-test", "Test-Modul für MovementKeys Utility");
    }
    
    @Override
    public void onActivate() {
        info("MovementTest aktiviert - Richtung: " + direction.get());
    }
    
    @Override
    public void onDeactivate() {
        // Alle Keys loslassen beim Deaktivieren
        MovementKeys.releaseAll();
        info("MovementTest deaktiviert - Alle Keys losgelassen");
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        // Bewegungsrichtung setzen
        switch (direction.get()) {
            case Forward -> {
                MovementKeys.forward(true);
                MovementKeys.back(false);
                MovementKeys.left(false);
                MovementKeys.right(false);
            }
            case Back -> {
                MovementKeys.forward(false);
                MovementKeys.back(true);
                MovementKeys.left(false);
                MovementKeys.right(false);
            }
            case Left -> {
                MovementKeys.forward(false);
                MovementKeys.back(false);
                MovementKeys.left(true);
                MovementKeys.right(false);
            }
            case Right -> {
                MovementKeys.forward(false);
                MovementKeys.back(false);
                MovementKeys.left(false);
                MovementKeys.right(true);
            }
            case ForwardLeft -> {
                MovementKeys.forward(true);
                MovementKeys.back(false);
                MovementKeys.left(true);
                MovementKeys.right(false);
            }
            case ForwardRight -> {
                MovementKeys.forward(true);
                MovementKeys.back(false);
                MovementKeys.left(false);
                MovementKeys.right(true);
            }
            case BackLeft -> {
                MovementKeys.forward(false);
                MovementKeys.back(true);
                MovementKeys.left(true);
                MovementKeys.right(false);
            }
            case BackRight -> {
                MovementKeys.forward(false);
                MovementKeys.back(true);
                MovementKeys.left(false);
                MovementKeys.right(true);
            }
        }
        
        // Auto-Jump
        MovementKeys.jump(autoJump.get());
        
        // Auto-Sneak
        MovementKeys.sneak(autoSneak.get());
        
        // Auto-Sprint
        MovementKeys.sprint(autoSprint.get());
    }
    
    public enum Direction {
        Forward,
        Back,
        Left,
        Right,
        ForwardLeft,
        ForwardRight,
        BackLeft,
        BackRight
    }
}
