package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Generador de la dimensión «Ciudad»: una ciudad de 2000 x 2000 bloques calculada por coordenadas. */
public class CityChunkGenerator extends ChunkGenerator {
    public static final MapCodec<CityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(inst, CityChunkGenerator::new));

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    public CityChunkGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return CityPlan.SEA;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return CityPlan.GROUND + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] column = new BlockState[level.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = level.getMinBuildHeight() + i;
            column[i] = y <= CityPlan.GROUND ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }

    private static void put(ChunkAccess chunk, int lx, int y, int lz, BlockState st) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
            return;
        }
        LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
        sec.setBlockState(lx, y & 15, lz, st, false);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                         StructureManager structures, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        Map<Long, CityPlan.Lot> cache = new HashMap<>();
        CityPlan.Out o = new CityPlan.Out();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                CityPlan.column(x0 + lx, z0 + lz, o, cache);
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
        return CompletableFuture.completedFuture(chunk);
    }
}
