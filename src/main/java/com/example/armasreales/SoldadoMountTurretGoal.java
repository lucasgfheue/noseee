package com.example.armasreales;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Al ver a un enemigo, el soldado corre a la torreta libre más cercana de su base y se monta en ella. */
public class SoldadoMountTurretGoal extends Goal {
    private static final double SEARCH = 28.0;
    private final SoldadoEntity mob;
    private TurretEntity turret;
    private int timeout;

    public SoldadoMountTurretGoal(SoldadoEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private TurretEntity findTurret() {
        List<TurretEntity> list = mob.level().getEntitiesOfClass(TurretEntity.class,
                mob.getBoundingBox().inflate(SEARCH),
                t -> t.isAlive() && t.getTurretType() == TurretEntity.MG && !t.isVehicle()
                        && mob.isWithinRestriction(t.blockPosition()));
        return list.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || mob.isPassenger() || mob.getRole() == SoldadoEntity.ROLE_SNIPER
                || mob.getRole() < 0 || mob.distanceToSqr(t) < 25.0 || mob.tickCount % 10 != 0) {
            return false;
        }
        this.turret = findTurret();
        return this.turret != null;
    }

    @Override
    public boolean canContinueToUse() {
        return turret != null && turret.isAlive() && !turret.isVehicle() && !mob.isPassenger()
                && mob.getTarget() != null && timeout < 240;
    }

    @Override
    public void start() {
        timeout = 0;
        mob.getNavigation().moveTo(turret, 1.15);
    }

    @Override
    public void tick() {
        timeout++;
        if (mob.distanceToSqr(turret) < 2.6 * 2.6) {
            mob.startRiding(turret);
            return;
        }
        if (timeout % 10 == 0) {
            mob.getNavigation().moveTo(turret, 1.15);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        turret = null;
    }
}
