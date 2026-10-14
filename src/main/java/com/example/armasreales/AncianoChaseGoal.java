package com.example.armasreales;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Una vez de pie, el anciano persigue a su objetivo hasta tenerlo a tiro. */
public class AncianoChaseGoal extends Goal {
    private final AncianoEntity mob;

    public AncianoChaseGoal(AncianoEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && !mob.isSitting();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) {
            return;
        }
        boolean los = mob.getSensing().hasLineOfSight(t);
        if (mob.distanceToSqr(t) > 100.0 || !los) {
            mob.getNavigation().moveTo(t, 1.1);
        } else {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
