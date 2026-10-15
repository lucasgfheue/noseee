package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Base militar gigante. Funciona con /locate y puede aparecer en cualquier bioma del Overworld, pero rechaza el sitio
 * si hay agua o demasiado desnivel bajo su área de 121x121.
 */
public class MilitaryBaseStructure extends Structure {
    public static final MapCodec<MilitaryBaseStructure> CODEC = simpleCodec(MilitaryBaseStructure::new);
    private static final int SIZE = 121;

    public MilitaryBaseStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos cp = context.chunkPos();
        int x0 = cp.getMinBlockX() + 8 - SIZE / 2;
        int z0 = cp.getMinBlockZ() + 8 - SIZE / 2;
        ChunkGenerator gen = context.chunkGenerator();
        LevelHeightAccessor accessor = context.heightAccessor();
        RandomState random = context.randomState();

        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, n = 0;
        for (int dx = 0; dx < SIZE; dx += 10) {
            for (int dz = 0; dz < SIZE; dz += 10) {
                int x = x0 + dx, z = z0 + dz;
                int h = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, accessor, random);
                NoiseColumn column = gen.getBaseColumn(x, z, accessor, random);
                BlockState top = column.getBlock(h);          // h ya es el bloque superior
                if (!top.getFluidState().isEmpty()) {
                    return Optional.empty();                 // agua: no se genera aquí
                }
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        }
        if (max - min > 10) {
            return Optional.empty();                         // demasiado desnivel
        }
        // h es la altura del bloque superior; la capa del suelo de la plantilla es la 3ª (índice 2)
        BlockPos origin = new BlockPos(x0, Math.round(sum / (float) n) - MilitaryBasePiece.FLOOR_LAYER, z0);
        return Optional.of(new GenerationStub(origin,
                builder -> builder.addPiece(new MilitaryBasePiece(context.structureTemplateManager(), origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BASE.get();
    }
}
