package com.example.armasreales;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /ciudad lleva al jugador a la ciudad; /ciudad salir lo devuelve al mundo normal (sólo operadores). */
public final class CityCommand {
    private CityCommand() {}

    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "ciudad"));

    private static final String[] PLACES = {"restaurante", "policia", "bomberos", "hospital", "ciber", "universidad",
            "centro_comercial", "hotel", "banco", "cine", "biblioteca", "escuela"};

    /** /ciudad ir <lugar>: teletransporta a los edificios de servicios más cercanos al centro. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> places() {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> go = Commands.literal("ir");
        for (int i = 0; i < PLACES.length; i++) {
            final int idx = i;
            go.then(Commands.literal(PLACES[i]).executes(ctx -> {
                int[] a = CityCivic.landmark(idx);
                return teleport(ctx.getSource(), a[0], a[1], CityPlan.GROUND + 1, PLACES[idx]);
            }));
        }
        go.then(Commands.literal("estacion").executes(ctx -> {
            int[] a = CityCivic.stationSpot();
            return teleport(ctx.getSource(), a[0], a[1], CityPlan.GROUND + 2, "estacion");
        }));
        return go;
    }

    private static int teleport(net.minecraft.commands.CommandSourceStack src, int x, int z, int y, String name) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel target = src.getServer().getLevel(CITY);
            if (target == null) {
                src.sendFailure(Component.literal("La dimensión de la ciudad no está disponible."));
                return 0;
            }
            player.teleportTo(target, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
            src.sendSuccess(() -> Component.literal("Teletransportado: " + name), true);
            return Command.SINGLE_SUCCESS;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return 0;
        }
    }

    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ciudad").requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel target = ctx.getSource().getServer().getLevel(CITY);
                    if (target == null) {
                        ctx.getSource().sendFailure(Component.literal("La dimensión de la ciudad no está disponible."));
                        return 0;
                    }
                    player.teleportTo(target, 0.5, CityPlan.GROUND + 1, 0.5, player.getYRot(), player.getXRot());
                    ctx.getSource().sendSuccess(() -> Component.literal("Bienvenido a la ciudad (cruce central, 0 0)."), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(places())
                .then(Commands.literal("salir").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel over = ctx.getSource().getServer().overworld();
                    BlockPos sp = over.getSharedSpawnPos();
                    player.teleportTo(over, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, player.getYRot(), player.getXRot());
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
