package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Cuando la bestia persigue a un jugador (aunque esté detrás de paredes) avanza en línea recta hacia él
 * y rompe lo que le estorba: madera, tierra, hojas y puertas de cualquier tipo.
 */
public class WerewolfBreakGoal extends Goal {
    private final WerewolfEntity wolf;
    private int cooldown;

    public WerewolfBreakGoal(WerewolfEntity wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = wolf.getTarget();
        return wolf.isWolf() && target != null && target.isAlive()
                && wolf.distanceToSqr(target) > 2.5 * 2.5
                && (wolf.getNavigation().isDone() || wolf.horizontalCollision);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || (wolf.getTarget() != null && wolf.isWolf() && wolf.horizontalCollision);
    }

    @Override
    public void tick() {
        LivingEntity target = wolf.getTarget();
        if (target == null) {
            return;
        }
        wolf.getLookControl().setLookAt(target, 30.0f, 30.0f);
        wolf.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.3);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Level level = wolf.level();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        double dx = target.getX() - wolf.getX();
        double dz = target.getZ() - wolf.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            return;
        }
        // Un bloque por delante en la dirección hacia el jugador
        int ax = (int) Math.round(dx / len * 1.2);
        int az = (int) Math.round(dz / len * 1.2);
        int height = Math.max(2, (int) Math.ceil(wolf.getBbHeight()));
        BlockPos base = wolf.blockPosition().offset(ax, 0, az);
        boolean broke = false;
        for (int y = 0; y < height && !broke; y++) {
            broke = tryBreak(level, base.above(y));
        }
        if (!broke && target.getY() > wolf.getY() + 1.5) {
            broke = tryBreak(level, wolf.blockPosition().above(height));   // sube cavando
        }
        if (!broke && target.getY() < wolf.getY() - 1.5) {
            broke = tryBreak(level, wolf.blockPosition().below());          // baja cavando
        }
        if (broke) {
            cooldown = 8;
            wolf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    private boolean tryBreak(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        boolean door = state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES);
        float hardness = state.getDestroySpeed(level, pos);
        boolean soft = hardness >= 0.0f && hardness <= 3.0f && level.getBlockEntity(pos) == null
                && (state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.LEAVES));
        if (!door && !soft) {
            return false;
        }
        return level.destroyBlock(pos, true, wolf);
    }
}
