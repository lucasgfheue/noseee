package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Pieza única de la torre de radio: coloca la plantilla torre.nbt. */
public class RadioTowerPiece extends TemplateStructurePiece {
    /** Índice de la capa de suelo dentro de la plantilla (debajo hay 2 capas de tierra). */
    public static final int FLOOR_LAYER = 2;

    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "torre_radio/torre");

    public RadioTowerPiece(StructureTemplateManager manager, BlockPos pos) {
        super(ModStructures.TOWER_PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), pos);
    }

    public RadioTowerPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.TOWER_PIECE.get(), tag, context.structureTemplateManager(), id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setIgnoreEntities(false);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
    }
}
