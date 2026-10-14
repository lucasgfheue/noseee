package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Coloca campamentos en chunks recién generados del Overworld, pero sólo en terreno seco y bastante llano
 * (nunca sobre agua). Hay como máximo un campamento por región de 20x20 chunks.
 */
public final class CampSpawner {
    private static final int REGION = 20;
    private static final int SIZE = 25;
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "campamento/camp");
    private static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "has_campamento"));

    private static final class Pending {
        final ServerLevel level;
        final BlockPos center;
        int tries = 40;

        Pending(ServerLevel level, BlockPos center) {
            this.level = level;
            this.center = center;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private CampSpawner() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        int rx = Math.floorDiv(pos.x, REGION);
        int rz = Math.floorDiv(pos.z, REGION);
        RandomSource r = RandomSource.create(level.getSeed() * 341873128712L + rx * 132897987541L
                + rz * 42317861L + 1987341L);
        int cx = rx * REGION + 3 + r.nextInt(REGION - 6);
        int cz = rz * REGION + 3 + r.nextInt(REGION - 6);
        if (pos.x != cx || pos.z != cz || r.nextInt(100) >= 75) {
            return;
        }
        synchronized (PENDING) {
            PENDING.add(new Pending(level, new BlockPos(pos.getMinBlockX() + 8, 0, pos.getMinBlockZ() + 8)));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        synchronized (PENDING) {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (--p.tries < 0 || tryPlace(p)) {
                    it.remove();
                }
            }
        }
    }

    /** @return true si ya hay que descartar este pendiente (colocado o terreno no válido). */
    private static boolean tryPlace(Pending p) {
        ServerLevel level = p.level;
        int x0 = p.center.getX() - SIZE / 2;
        int z0 = p.center.getZ() - SIZE / 2;
        for (int cx = x0 >> 4; cx <= (x0 + SIZE) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + SIZE) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;                         // esperar a que carguen los vecinos
                }
            }
        }
        // Estudio del terreno
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, n = 0;
        for (int dx = 0; dx < SIZE; dx += 3) {
            for (int dz = 0; dz < SIZE; dz += 3) {
                int x = x0 + dx, z = z0 + dz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, h - 1, z));
                if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LOGS) || ground.isAir()) {
                    return true;                          // agua o árbol: no se coloca aquí
                }
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        }
        if (max - min > 4) {
            return true;                                  // demasiado desnivel
        }
        BlockPos middle = new BlockPos(p.center.getX(), sum / n, p.center.getZ());
        if (!level.getBiome(middle).is(BIOMES)) {
            return true;
        }
        int groundTop = Math.round(sum / (float) n);
        BlockPos origin = new BlockPos(x0, groundTop - 1, z0);

        // Despejar árboles y plantas dentro del área y rellenar huecos debajo
        for (int dx = 0; dx < SIZE; dx++) {
            for (int dz = 0; dz < SIZE; dz++) {
                for (int y = 0; y < 9; y++) {
                    BlockPos q = origin.offset(dx, y, dz);
                    BlockState st = level.getBlockState(q);
                    if (y >= 1 && !st.isAir() && (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
                            || st.canBeReplaced())) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                for (int y = 1; y <= 5; y++) {
                    BlockPos q = origin.offset(dx, -y, dz);
                    BlockState st = level.getBlockState(q);
                    if (st.isAir() || st.canBeReplaced()) {
                        level.setBlock(q, Blocks.DIRT.defaultBlockState(), 2);
                    } else {
                        break;
                    }
                }
            }
        }
        StructureTemplate template = level.getStructureManager().getOrCreate(TEMPLATE);
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        return true;
    }
}
