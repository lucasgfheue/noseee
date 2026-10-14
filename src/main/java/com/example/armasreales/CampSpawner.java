package com.example.armasreales;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


/** Comando /campamento: coloca un campamento a mano (para pruebas). La generación natural está en CampStructure. */
public final class CampSpawner {
    private static final int SIZE = 25;
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "campamento/camp");
    private CampSpawner() {
    }

    /** Coloca la plantilla de la torre de radio (9x9) con su esquina mínima en (x0, z0). */
    public static void buildTower(ServerLevel level, int x0, int z0, int groundTop) {
        BlockPos origin = new BlockPos(x0, groundTop - 1 - CampPiece.FLOOR_LAYER, z0);
        for (int dx = 0; dx < 9; dx++) {
            for (int dz = 0; dz < 9; dz++) {
                for (int y = CampPiece.FLOOR_LAYER + 1; y < 62; y++) {
                    BlockPos q = origin.offset(dx, y, dz);
                    BlockState st = level.getBlockState(q);
                    if (!st.isAir() && (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.canBeReplaced())) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        StructureTemplate template = level.getStructureManager().getOrCreate(
                ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "torre_radio/torre"));
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(false),
                level.getRandom(), 2);
    }

    /** Despeja, rellena y coloca el campamento con su esquina mínima en (x0, z0). */
    public static void build(ServerLevel level, int x0, int z0, int groundTop) {
        // groundTop = primer aire sobre el suelo; la capa de suelo de la plantilla es el índice FLOOR_LAYER
        BlockPos origin = new BlockPos(x0, groundTop - 1 - CampPiece.FLOOR_LAYER, z0);

        // Despejar árboles y plantas dentro del área y rellenar huecos debajo
        for (int dx = 0; dx < SIZE; dx++) {
            for (int dz = 0; dz < SIZE; dz++) {
                for (int y = 0; y < 12; y++) {
                    BlockPos q = origin.offset(dx, y, dz);
                    BlockState st = level.getBlockState(q);
                    if (y > CampPiece.FLOOR_LAYER && !st.isAir() && (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
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
    }

    /** /campamento: coloca un campamento 16 bloques delante del jugador (sólo operadores). */
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("campamento").requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    BlockPos c = player.blockPosition().relative(player.getDirection(), 16);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                    build(level, c.getX() - SIZE / 2, c.getZ() - SIZE / 2, top);
                    ctx.getSource().sendSuccess(() -> Component.literal("Campamento colocado"), true);
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("torreradio").requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    BlockPos c = player.blockPosition().relative(player.getDirection(), 8);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                    buildTower(level, c.getX() - 4, c.getZ() - 4, top);
                    ctx.getSource().sendSuccess(() -> Component.literal("Torre de radio colocada"), true);
                    return 1;
                }));
    }
}
