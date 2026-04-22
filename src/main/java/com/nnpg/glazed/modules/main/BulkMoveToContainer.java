// com/nnpg/glazed/modules/main/BulkMoveToContainer.java
package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.mixins.ScreenHandlerAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.List;

public class BulkMoveToContainer extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage { NONE, WAIT_BEFORE_OPEN, OPEN_SCREEN, WAIT_AFTER_OPEN, MOVING, DONE }
    private Stage stage = Stage.NONE;
    private long stageStart = 0;

    // Timing:
    // 0s        → aktiviert
    // 0→1s      → WAIT_BEFORE_OPEN (1s warten)
    // 1s        → OPEN_SCREEN (fake container öffnen)
    // 1→5s      → WAIT_AFTER_OPEN (4s warten)
    // 5s        → MOVING (bulk move ausführen)
    private static final long WAIT_BEFORE_OPEN_MS = 1000;
    private static final long WAIT_AFTER_OPEN_MS  = 4000;

    // Die fake Inventory Instanz — wird gehalten damit sie nicht GC'd wird
    private SimpleInventory fakeInventory = null;

    public BulkMoveToContainer() {
        super(GlazedAddon.CATEGORY, "bulk-move-to-container",
                "Öffnet einen fake 9x4 Container, wartet 5s, verschiebt dann alle Inventory-Items per QUICK_MOVE rein.");
    }

    @Override
    public void onActivate() {
        // Kein Check ob Container offen — wir öffnen selbst einen
        stage = Stage.WAIT_BEFORE_OPEN;
        stageStart = System.currentTimeMillis();
        fakeInventory = null;
        ChatUtils.info("BulkMove: Starte in 1s einen fake 9x4 Container...");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
        fakeInventory = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        switch (stage) {

            case WAIT_BEFORE_OPEN -> {
                if (now - stageStart >= WAIT_BEFORE_OPEN_MS) {
                    openFakeContainer();
                    stage = Stage.OPEN_SCREEN;
                    stageStart = now;
                }
            }

            case OPEN_SCREEN -> {
                // Einen Tick warten bis der Screen gesetzt ist
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    ChatUtils.info("BulkMove: Fake Container offen! Bulk-Move in 4s...");
                    stage = Stage.WAIT_AFTER_OPEN;
                    stageStart = now;
                } else if (now - stageStart > 2000) {
                    // Timeout — Screen hat sich nicht geöffnet
                    ChatUtils.error("BulkMove: Container konnte nicht geöffnet werden!");
                    toggle();
                }
            }

            case WAIT_AFTER_OPEN -> {
                if (now - stageStart >= WAIT_AFTER_OPEN_MS) {
                    stage = Stage.MOVING;
                }
            }

            case MOVING -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen)) {
                    ChatUtils.error("BulkMove: Container wurde geschlossen! Abbruch.");
                    toggle();
                    return;
                }
                performBulkMove();
                stage = Stage.DONE;
            }

            case DONE -> {
                toggle();
            }
        }
    }

    /**
     * Öffnet einen vollständig funktionalen fake 9x4 GenericContainerScreen client-seitig.
     *
     * Wir nutzen openHandledScreen() über den Player — das erzeugt einen echten
     * ScreenHandler mit syncId, revision, und voll funktionalen Slots (Item-Bewegungen,
     * Shift-Click, etc. funktionieren alle korrekt).
     *
     * Die SimpleInventory (36 Slots) ist die Container-Seite — sie ist leer beim Start.
     * Der ScreenHandler verbindet sie mit dem PlayerInventory genau wie eine echte Truhe.
     */
    private void openFakeContainer() {
        fakeInventory = new SimpleInventory(36); // 9x4 = 36 slots

        mc.player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) -> new GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X4,
                        syncId,
                        playerInv,
                        fakeInventory,
                        4  // rows
                ),
                Text.literal("Fake Container (9x4)")
        ));
    }

    private void performBulkMove() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) return;

        int syncId   = handler.syncId;
        int revision = ((ScreenHandlerAccessor) handler).getRevision();
        List<Slot> slots = handler.slots;

        int totalSlots    = slots.size();
        int containerSize = totalSlots - 36; // 36 = 27 player inv + 9 hotbar

        int movedCount          = 0;
        int nextFreeContainerSlot = 0;

        for (int sourceSlot = containerSize; sourceSlot < totalSlots; sourceSlot++) {
            Slot slot = slots.get(sourceSlot);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Nächsten freien Slot im Container finden
            while (nextFreeContainerSlot < containerSize
                    && !slots.get(nextFreeContainerSlot).getStack().isEmpty()) {
                nextFreeContainerSlot++;
            }
            if (nextFreeContainerSlot >= containerSize) {
                ChatUtils.warning("BulkMove: Container voll! %d Items verschoben.", movedCount);
                break;
            }

            int targetSlot = nextFreeContainerSlot;

            Int2ObjectMap<ItemStack> modifiedSlots = buildModifiedSlots(sourceSlot, targetSlot, stack);

            ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
                    syncId,
                    revision,
                    sourceSlot,
                    0,
                    SlotActionType.QUICK_MOVE,
                    ItemStack.EMPTY,
                    modifiedSlots
            );

            mc.player.networkHandler.sendPacket(packet);

            // Optimistic local update — damit die nächste Iteration
            // den korrekten lokalen State sieht (revision kommt vom Server zurück)
            slot.setStack(ItemStack.EMPTY);
            slots.get(targetSlot).setStack(stack.copy());
            nextFreeContainerSlot++;
            movedCount++;
        }

        ChatUtils.info("BulkMove: %d Items per QUICK_MOVE in Container verschoben!", movedCount);
    }

    private Int2ObjectMap<ItemStack> buildModifiedSlots(int sourceSlot, int targetSlot, ItemStack movedItem) {
        Int2ObjectMap<ItemStack> map = new Int2ObjectArrayMap<>();
        map.put(targetSlot, movedItem.copy());
        map.put(sourceSlot, ItemStack.EMPTY);
        return map;
    }
}
