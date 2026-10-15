package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** El sobreviviente con pistola le dispara a su objetivo; los desarmados pelean cuerpo a cuerpo. */
public class SobrevivienteShootGoal extends Goal {
    private static final double RANGE = 24.0;
    private final SobrevivienteEntity mob;
    private int cooldown = 20;

    public SobrevivienteShootGoal(SobrevivienteEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean hasGun() {
        return mob.getMainHandItem().is(ModItems.PISTOLA.get());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && hasGun() && mob.distanceToSqr(t) < RANGE * RANGE
                && mob.getSensing().hasLineOfSight(t);
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
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        mob.getLookControl().setLookAt(target, 40.0f, 40.0f);
        double dist = mob.distanceTo(target);
        if (dist > 12.0) {
            mob.getNavigation().moveTo(target, 1.0);
        } else {
            mob.getNavigation().stop();
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        Vec3 eye = mob.getEyePosition();
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 dir = aim.subtract(eye).normalize();
        Vec3 d = Bullets.spread(level, dir, 0.035 + dist * 0.0015);
        Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 40.0);
        if (hit.entity() != null && !(hit.entity() instanceof SobrevivienteEntity)) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().mobAttack(mob), 4.0f);
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.2f, 0.7f);
        Vec3 muzzle = eye.add(d.scale(0.9));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
        this.cooldown = 25;
    }
}
