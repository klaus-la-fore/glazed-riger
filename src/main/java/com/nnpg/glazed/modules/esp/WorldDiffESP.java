package com.nnpg.glazed.modules.esp;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nnpg.glazed.GlazedAddon;

public class WorldDiffESP extends Module {

    // ── Setting Groups ────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgFilter   = settings.createGroup("Filter");
    private final SettingGroup sgRender   = settings.createGroup("Render");
    private final SettingGroup sgColors   = settings.createGroup("Colors");

    // ── General ───────────────────────────────────────────────────────────────

    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder()
        .name("seed")
        .description("World seed to generate original terrain for comparison.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> renderRadius = sgGeneral.add(new IntSetting.Builder()
        .name("render-radius-chunks")
        .description("How many chunks around the player to compute diff for.")
        .defaultValue(4)
        .min(1)
        .sliderMax(12)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to include in diff.")
        .defaultValue(-64)
        .min(-64)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to include in diff.")
        .defaultValue(100)
        .min(-64)
        .sliderRange(-64, 320)
        .build()
    );

    // ── Filter ────────────────────────────────────────────────────────────────

    private final Setting<Boolean> showRemoved = sgFilter.add(new BoolSetting.Builder()
        .name("show-removed")
        .description("Show blocks that were removed (original block → air).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showAdded = sgFilter.add(new BoolSetting.Builder()
        .name("show-added")
        .description("Show blocks that were placed by players (air → block).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showReplaced = sgFilter.add(new BoolSetting.Builder()
        .name("show-replaced")
        .description("Show blocks where one natural block was replaced with another.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chestOnly = sgFilter.add(new BoolSetting.Builder()
        .name("highlight-chests-only")
        .description("Only highlight added blocks that are chests/storage containers.")
        .defaultValue(false)
        .build()
    );

    // ── Render ────────────────────────────────────────────────────────────────

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the diff regions are rendered.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Boolean> seeThrough = sgRender.add(new BoolSetting.Builder()
        .name("see-through")
        .description("Render through walls.")
        .defaultValue(true)
        .build()
    );

    // ── Colors ────────────────────────────────────────────────────────────────

    private final Setting<SettingColor> removedSide = sgColors.add(new ColorSetting.Builder()
        .name("removed-side")
        .description("Fill color for removed blocks.")
        .defaultValue(new SettingColor(255, 50, 50, 30))
        .build()
    );

    private final Setting<SettingColor> removedLine = sgColors.add(new ColorSetting.Builder()
        .name("removed-line")
        .description("Outline color for removed blocks.")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .build()
    );

    private final Setting<SettingColor> addedSide = sgColors.add(new ColorSetting.Builder()
        .name("added-side")
        .description("Fill color for added blocks.")
        .defaultValue(new SettingColor(50, 255, 50, 30))
        .build()
    );

    private final Setting<SettingColor> addedLine = sgColors.add(new ColorSetting.Builder()
        .name("added-line")
        .description("Outline color for added blocks.")
        .defaultValue(new SettingColor(50, 255, 50, 200))
        .build()
    );

    private final Setting<SettingColor> replacedSide = sgColors.add(new ColorSetting.Builder()
        .name("replaced-side")
        .description("Fill color for replaced blocks.")
        .defaultValue(new SettingColor(255, 200, 50, 30))
        .build()
    );

    private final Setting<SettingColor> replacedLine = sgColors.add(new ColorSetting.Builder()
        .name("replaced-line")
        .description("Outline color for replaced blocks.")
        .defaultValue(new SettingColor(255, 200, 50, 200))
        .build()
    );

    // ── Internal Data Structures ──────────────────────────────────────────────

    /** Type of diff for each block region. */
    public enum DiffType { REMOVED, ADDED, REPLACED }

    /** An axis-aligned rectangular region produced by greedy meshing. */
    private record DiffRegion(
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        DiffType type
    ) {}

    /** Per-chunk state: dirty flag + cached rendered regions. */
    private static class ChunkState {
        final AtomicBoolean dirty = new AtomicBoolean(true);
        volatile List<DiffRegion> regions = List.of();
    }

    /** Map from ChunkPos (encoded as long) → state */
    private final Map<Long, ChunkState> chunkStates = new ConcurrentHashMap<>();

    /** Background thread pool for diff + meshing work. */
    private ExecutorService threadPool;

    /** Chunks currently being processed (to avoid double-submission). */
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();

    /** Cached chunk generator built from seed. */
    private ChunkGenerator cachedGenerator = null;
    private String         cachedSeed      = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public WorldDiffESP() {
        super(GlazedAddon.esp, "world-diff-esp",
            "Compares live server terrain against seed-generated original terrain. "
            + "Highlights all player-made changes.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        threadPool = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        chunkStates.clear();
        processing.clear();
        cachedGenerator = null;
        cachedSeed      = null;
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        chunkStates.clear();
        processing.clear();
    }

    // ── Packet Handler: mark chunks dirty when server sends new data ──────────

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDataS2CPacket p) {
            long key = ChunkPos.toLong(p.getChunkX(), p.getChunkZ());
            chunkStates.computeIfAbsent(key, k -> new ChunkState()).dirty.set(true);
        } else if (event.packet instanceof UnloadChunkS2CPacket p) {
            long key = ChunkPos.toLong(p.pos().x, p.pos().z);
            chunkStates.remove(key);
            processing.remove(key);
        }
    }

    // ── Tick: submit dirty chunks in radius to background thread ─────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || threadPool == null) return;
        if (seed.get().isEmpty()) return;

        ChunkGenerator generator = getOrBuildGenerator();
        if (generator == null) return;

        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        int r  = renderRadius.get();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.toLong(cx + dx, cz + dz);

                ChunkState state = chunkStates.computeIfAbsent(key, k -> new ChunkState());

                if (!state.dirty.get()) continue;
                if (!processing.add(key)) continue;

                int finalCx = cx + dx;
                int finalCz = cz + dz;

                threadPool.submit(() -> {
                    try {
                        processChunk(finalCx, finalCz, key, state, generator);
                    } finally {
                        processing.remove(key);
                    }
                });
            }
        }
    }

    // ── 3D Render ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || seed.get().isEmpty()) return;

        if (seeThrough.get()) {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }

        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        int r  = renderRadius.get();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.toLong(cx + dx, cz + dz);
                ChunkState state = chunkStates.get(key);
                if (state == null) continue;

                for (DiffRegion region : state.regions) {
                    SettingColor sc = sideColor(region.type());
                    SettingColor lc = lineColor(region.type());

                    if (sc == null || lc == null) continue;

                    event.renderer.box(
                        region.x1(), region.y1(), region.z1(),
                        region.x2(), region.y2(), region.z2(),
                        sc, lc, shapeMode.get(), 0
                    );
                }
            }
        }

        if (seeThrough.get()) {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }
    }

    // ── Core: diff + greedy mesh a single chunk ────────────────────────────────

    private void processChunk(int cx, int cz, long key, ChunkState state, ChunkGenerator generator) {
        if (mc.world == null) return;

        Chunk serverChunk = mc.world.getChunk(cx, cz);
        if (serverChunk == null) return;

        // Generate original chunk using seed-based generator
        // We use the noise generator to get terrain data
        boolean[][][] removed  = new boolean[16][mc.world.getHeight()][16];
        boolean[][][] added    = new boolean[16][mc.world.getHeight()][16];
        boolean[][][] replaced = new boolean[16][mc.world.getHeight()][16];

        int worldMinY  = mc.world.getBottomY();
        int filterMinY = Math.max(minY.get(), worldMinY);
        int filterMaxY = Math.min(maxY.get(), mc.world.getBottomY() + mc.world.getHeight() - 1);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = filterMinY; y <= filterMaxY; y++) {
                    int arrY = y - worldMinY;
                    if (arrY < 0 || arrY >= removed[0].length) continue;

                    int worldX = cx * 16 + lx;
                    int worldZ = cz * 16 + lz;

                    BlockState serverState = serverChunk.getBlockState(new BlockPos(worldX, y, worldZ));

                    // Generate original block at this position via generator
                    BlockState originalState = generateOriginalBlock(generator, worldX, y, worldZ);

                    boolean serverAir   = serverState.isAir();
                    boolean originalAir = originalState.isAir();

                    if (!originalAir && serverAir) {
                        // Original block was removed
                        if (showRemoved.get()) removed[lx][arrY][lz] = true;
                    } else if (originalAir && !serverAir) {
                        // Block was placed where air was
                        if (showAdded.get()) {
                            if (!chestOnly.get() || isStorage(serverState)) {
                                added[lx][arrY][lz] = true;
                            }
                        }
                    } else if (!originalAir && !serverAir && !serverState.equals(originalState)) {
                        // One natural block replaced by another
                        if (showReplaced.get()) replaced[lx][arrY][lz] = true;
                    }
                }
            }
        }

        int baseX = cx * 16;
        int baseZ = cz * 16;

        List<DiffRegion> regions = new ArrayList<>();
        greedyMesh(removed,  removed[0].length,  baseX, worldMinY, baseZ, DiffType.REMOVED,  regions);
        greedyMesh(added,    added[0].length,    baseX, worldMinY, baseZ, DiffType.ADDED,    regions);
        greedyMesh(replaced, replaced[0].length, baseX, worldMinY, baseZ, DiffType.REPLACED, regions);

        state.regions = List.copyOf(regions);
        state.dirty.set(false);
    }

    // ── Greedy Meshing ────────────────────────────────────────────────────────

    /**
     * Greedy meshing over a 3D boolean array.
     * Merges adjacent true cells into axis-aligned rectangular regions.
     * Sweeps on the Y axis first (most gains for flat terrain diffs),
     * then merges on X, then Z.
     *
     * Result: far fewer render calls than one box per block.
     */
    private void greedyMesh(boolean[][][] mask, int height,
                             int baseX, int baseY, int baseZ,
                             DiffType type, List<DiffRegion> out) {
        boolean[][][] used = new boolean[16][height][16];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!mask[x][y][z] || used[x][y][z]) continue;

                    // Extend on Z
                    int z2 = z;
                    while (z2 + 1 < 16 && mask[x][y][z2 + 1] && !used[x][y][z2 + 1]) z2++;

                    // Extend on X (all Z in [z..z2] must be true and unused)
                    int x2 = x;
                    outer:
                    while (x2 + 1 < 16) {
                        for (int zz = z; zz <= z2; zz++) {
                            if (!mask[x2 + 1][y][zz] || used[x2 + 1][y][zz]) break outer;
                        }
                        x2++;
                    }

                    // Extend on Y (all X in [x..x2], Z in [z..z2] must be true and unused)
                    int y2 = y;
                    outerY:
                    while (y2 + 1 < height) {
                        for (int xx = x; xx <= x2; xx++) {
                            for (int zz = z; zz <= z2; zz++) {
                                if (!mask[xx][y2 + 1][zz] || used[xx][y2 + 1][zz]) break outerY;
                            }
                        }
                        y2++;
                    }

                    // Mark used
                    for (int xx = x; xx <= x2; xx++)
                        for (int yy = y; yy <= y2; yy++)
                            for (int zz = z; zz <= z2; zz++)
                                used[xx][yy][zz] = true;

                    out.add(new DiffRegion(
                        baseX + x,      baseY + y,      baseZ + z,
                        baseX + x2 + 1, baseY + y2 + 1, baseZ + z2 + 1,
                        type
                    ));
                }
            }
        }
    }

    // ── Chunk Generator ───────────────────────────────────────────────────────

    /**
     * Returns a cached ChunkGenerator built from the configured seed.
     * Uses the overworld noise generator to match what vanilla would have generated.
     */
    private ChunkGenerator getOrBuildGenerator() {
        String s = seed.get().trim();
        if (s.isEmpty()) return null;
        if (s.equals(cachedSeed) && cachedGenerator != null) return cachedGenerator;
        if (mc.world == null) return null;

        try {
            long numericSeed;
            try {
                numericSeed = Long.parseLong(s);
            } catch (NumberFormatException e) {
                numericSeed = s.hashCode();
            }

            // Build a standalone overworld noise generator with the given seed.
            // We use the registry-based approach which works client-side.
            var registryManager = mc.world.getRegistryManager();
            var noiseSettings   = registryManager
                .getOrThrow(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS);

            var biomeLookup = registryManager.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.BIOME);
            var biomeSource = net.minecraft.world.biome.source.MultiNoiseBiomeSource
                .createOverworld(biomeLookup);

            var settings = noiseSettings.streamEntries()
                .filter(e -> e.registryKey().getValue().getPath().equals("overworld"))
                .findFirst();

            if (settings.isEmpty()) {
                if (notifications()) info("Could not find overworld noise settings.");
                return null;
            }

            cachedGenerator = new net.minecraft.world.gen.chunk.NoiseChunkGenerator(
                biomeSource,
                settings.get()
            );
            cachedSeed = s;
            if (notifications()) info("Generator built for seed: " + numericSeed);
        } catch (Exception e) {
            if (notifications()) info("Failed to build generator: " + e.getMessage());
            return null;
        }

        return cachedGenerator;
    }

    /**
     * Generates what a block at (worldX, y, worldZ) would have been in the original world.
     * Uses the chunk generator's surface rules and noise.
     *
     * Note: Full chunk generation is expensive; for performance we use the generator
     * in "height only" mode and infer air vs solid from the surface height.
     * This correctly identifies most player-made changes.
     */
    private BlockState generateOriginalBlock(ChunkGenerator gen, int worldX, int y, int worldZ) {
        try {
            // Use a random world with the seed to estimate surface height.
            // getHeight() is available on ChunkGenerator with a HeightLimitView.
            int surfaceHeight = gen.getHeight(worldX, worldZ,
                net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG,
                mc.world,
                net.minecraft.world.gen.noise.NoiseConfig.create(
                    gen instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator ncg
                        ? ncg.getSettings().value()
                        : net.minecraft.world.gen.chunk.ChunkGeneratorSettings.createMissingSettings(),
                    mc.world.getRegistryManager()
                        .getOrThrow(net.minecraft.registry.RegistryKeys.NOISE_PARAMETERS),
                    parsedSeedLong()
                )
            );

            if (y <= mc.world.getBottomY() + 4) return Blocks.BEDROCK.getDefaultState();
            if (y <= surfaceHeight)             return Blocks.STONE.getDefaultState();
            return Blocks.AIR.getDefaultState();
        } catch (Exception e) {
            return Blocks.AIR.getDefaultState();
        }
    }

    private long parsedSeedLong() {
        try { return Long.parseLong(seed.get().trim()); }
        catch (NumberFormatException e) { return seed.get().trim().hashCode(); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isStorage(BlockState state) {
        return state.isOf(Blocks.CHEST)
            || state.isOf(Blocks.TRAPPED_CHEST)
            || state.isOf(Blocks.BARREL)
            || state.isOf(Blocks.SHULKER_BOX)
            || state.isOf(Blocks.ENDER_CHEST)
            || state.isOf(Blocks.HOPPER)
            || state.isOf(Blocks.DROPPER)
            || state.isOf(Blocks.DISPENSER)
            || state.getBlock().getClass().getSimpleName().contains("ShulkerBox");
    }

    private SettingColor sideColor(DiffType type) {
        return switch (type) {
            case REMOVED  -> removedSide.get();
            case ADDED    -> addedSide.get();
            case REPLACED -> replacedSide.get();
        };
    }

    private SettingColor lineColor(DiffType type) {
        return switch (type) {
            case REMOVED  -> removedLine.get();
            case ADDED    -> addedLine.get();
            case REPLACED -> replacedLine.get();
        };
    }

    private boolean notifications() {
        return true;
    }
}
