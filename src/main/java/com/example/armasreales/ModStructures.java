package com.example.armasreales;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro de la estructura del campamento y de su pieza. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ArmasRealesMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ArmasRealesMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<CampStructure>> CAMP =
            TYPES.register("campamento", () -> () -> CampStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> CAMP_PIECE =
            PIECES.register("campamento", () -> (StructurePieceType) (ctx, tag) -> new CampPiece(ctx, tag));

    public static final DeferredHolder<StructureType<?>, StructureType<RadioTowerStructure>> TOWER =
            TYPES.register("torre_radio", () -> () -> RadioTowerStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> TOWER_PIECE =
            PIECES.register("torre_radio", () -> (StructurePieceType) (ctx, tag) -> new RadioTowerPiece(ctx, tag));

    private ModStructures() {
    }
}
