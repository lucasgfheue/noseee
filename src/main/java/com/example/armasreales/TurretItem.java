package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Coloca una torreta sobre el bloque al que apuntas, mirando hacia donde miras. */
public class TurretItem extends Item {
    private final int type;

    public TurretItem(Properties properties, int type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TurretEntity turret = ModEntities.TURRET.get().create(serverLevel);
        if (turret == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        turret.setTurretType(this.type);
        turret.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYRot() : 0.0f, 0.0f);
        if (!serverLevel.noCollision(turret)) {
            return InteractionResult.FAIL;
        }
        serverLevel.addFreshEntity(turret);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }
}
