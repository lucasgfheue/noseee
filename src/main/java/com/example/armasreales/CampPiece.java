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

/** Pieza única del campamento: coloca la plantilla camp.nbt. */
public class CampPiece extends TemplateStructurePiece {
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "campamento/camp");

    public CampPiece(StructureTemplateManager manager, BlockPos pos) {
        super(ModStructures.CAMP_PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), pos);
    }

    public CampPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.CAMP_PIECE.get(), tag, context.structureTemplateManager(), id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setIgnoreEntities(false);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
    }
}
