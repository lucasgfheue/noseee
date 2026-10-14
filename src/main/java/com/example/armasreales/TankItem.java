package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Coloca un tanque sobre el bloque al que apuntas. */
public class TankItem extends Item {
    public TankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TankEntity tank = ModEntities.TANK.get().create(serverLevel);
        if (tank == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : 0.0f;
        tank.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);

        if (!serverLevel.noCollision(tank)) {
            return InteractionResult.FAIL;   // no cabe: hay bloques en medio
        }
        serverLevel.addFreshEntity(tank);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }
}
