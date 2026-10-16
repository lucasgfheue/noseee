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
                .then(Commands.literal("salir").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel over = ctx.getSource().getServer().overworld();
                    BlockPos sp = over.getSharedSpawnPos();
                    player.teleportTo(over, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, player.getYRot(), player.getXRot());
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
