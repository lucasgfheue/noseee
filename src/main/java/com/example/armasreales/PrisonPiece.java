package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Pieza única de la prisión. Antes de colocar la plantilla despeja el terreno y rellena los huecos de debajo. */
public class PrisonPiece extends TemplateStructurePiece {
    /** Índice de la capa de suelo dentro de la plantilla (debajo hay 2 capas de tierra). */
    public static final int FLOOR_LAYER = 2;

    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "prision/prision");

    public PrisonPiece(StructureTemplateManager manager, BlockPos pos) {
        super(ModStructures.PRISON_PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), pos);
    }

    public PrisonPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.PRISON_PIECE.get(), tag, context.structureTemplateManager(), id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setIgnoreEntities(false);
    }

    /**
     * Despeja el volumen sobre el suelo (árboles, relieve) y rellena de tierra bajo la base, sólo dentro de
     * {@code clip}. Sirve tanto en la generación como en el comando.
     */
    public static void prepare(LevelAccessor level, BoundingBox area, int floorY, BoundingBox clip) {
        int x0 = Math.max(area.minX(), clip.minX()), x1 = Math.min(area.maxX(), clip.maxX());
        int z0 = Math.max(area.minZ(), clip.minZ()), z1 = Math.min(area.maxZ(), clip.maxZ());
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = floorY + 1; y <= floorY + 26; y++) {
                    q.set(x, y, z);
                    if (!level.getBlockState(q).isAir()) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                for (int y = floorY - 3; y >= floorY - 24; y--) {
                    q.set(x, y, z);
                    BlockState st = level.getBlockState(q);
                    boolean hollow = st.isAir() || !st.getFluidState().isEmpty() || st.canBeReplaced()
                            || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS);
                    if (!hollow) {
                        break;
                    }
                    level.setBlock(q, Blocks.DIRT.defaultBlockState(), 2);
                }
            }
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        prepare(level, this.boundingBox, this.templatePosition.getY() + FLOOR_LAYER, chunkBox);
        super.postProcess(level, structures, generator, random, chunkBox, chunkPos, pivot);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
    }
}
