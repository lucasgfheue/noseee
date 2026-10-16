package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dimensión «Ciudad»: terreno normal e infinito de Minecraft, con una ciudad de 2000 x 2000 bloques
 * (de -1000 a 999 en X y Z) colocada en el centro.
 */
public class CityChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<CityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(inst, CityChunkGenerator::new));

    private static final int B = CityPlan.HALF;
    private static final int MIN_Y = -64;

    public CityChunkGenerator(BiomeSource source, Holder<NoiseGeneratorSettings> settings) {
        super(source, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private static boolean overlaps(ChunkPos cp) {
        int x = cp.getMinBlockX();
        int z = cp.getMinBlockZ();
        return x < B && x + 15 >= -B && z < B && z + 15 >= -B;
    }

    private static boolean inside(ChunkPos cp) {
        int x = cp.getMinBlockX();
        int z = cp.getMinBlockZ();
        return x >= -B && x + 15 < B && z >= -B && z + 15 < B;
    }

    private static boolean inBox(int x, int z) {
        return x >= -B && x < B && z >= -B && z < B;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                         StructureManager structures, ChunkAccess chunk) {
        if (inside(chunk.getPos())) {
            cityFill(chunk, false);
            return CompletableFuture.completedFuture(chunk);
        }
        return super.fillFromNoise(blender, random, structures, chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        if (inside(cp)) {
            return;
        }
        super.buildSurface(region, structures, random, chunk);
        if (overlaps(cp)) {
            cityFill(chunk, true);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        if (overlaps(chunk.getPos())) {
            return;
        }
        super.applyCarvers(region, seed, random, biomes, structures, chunk, step);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (overlaps(region.getCenter())) {
            return;
        }
        super.spawnOriginalMobs(region);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (overlaps(chunk.getPos())) {
            return;
        }
        super.applyBiomeDecoration(level, chunk, structures);
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
                                 StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
        if (overlaps(chunk.getPos())) {
            return;
        }
        super.createStructures(registryAccess, state, structures, chunk, templates);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        if (inBox(x, z)) {
            return CityPlan.GROUND + 1;
        }
        return super.getBaseHeight(x, z, type, level, random);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        if (!inBox(x, z)) {
            return super.getBaseColumn(x, z, level, random);
        }
        BlockState[] column = new BlockState[level.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = level.getMinBuildHeight() + i;
            column[i] = y <= CityPlan.GROUND ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    private static void put(ChunkAccess chunk, int lx, int y, int lz, BlockState st) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
            return;
        }
        LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
        sec.setBlockState(lx, y & 15, lz, st, false);
    }

    /** Escribe la ciudad en las columnas del chunk que caen dentro del cuadrado de la ciudad. */
    private static void cityFill(ChunkAccess chunk, boolean clear) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        Map<Long, CityPlan.Lot> cache = new HashMap<>();
        CityPlan.Out o = new CityPlan.Out();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                if (!inBox(x, z)) {
                    continue;
                }
                CityPlan.column(x, z, o, cache);
                if (clear) {
                    for (int y = MIN_Y; y < chunk.getMaxBuildHeight(); y++) {
                        put(chunk, lx, y, lz, air);
                    }
                }
                int fy = o.floorY;
                put(chunk, lx, MIN_Y, lz, bedrock);
                for (int y = MIN_Y + 1; y <= fy - 5; y++) {
                    put(chunk, lx, y, lz, stone);
                }
                BlockState under = fy < CityPlan.SEA ? sand : dirt;
                for (int y = fy - 4; y < fy; y++) {
                    put(chunk, lx, y, lz, under);
                }
                put(chunk, lx, fy, lz, o.ground);
                for (int y = fy + 1; y <= o.waterTop; y++) {
                    put(chunk, lx, y, lz, water);
                }
                if (o.pillar) {
                    for (int y = fy + 1; y < CityPlan.GROUND; y++) {
                        put(chunk, lx, y, lz, o.pillarBlk);
                    }
                }
                for (int rel = 0; rel <= o.max; rel++) {
                    BlockState st = o.up[rel];
                    if (st != null) {
                        put(chunk, lx, CityPlan.GROUND + rel, lz, st);
                    }
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }
}
