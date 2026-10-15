package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Combate del soldado según su arma: AK-47 en ráfagas, escopeta de cerca y francotirador de largo alcance. */
public class SoldadoShootGoal extends Goal {
    private final SoldadoEntity mob;
    private int cooldown = 20;
    private int burstLeft = 0;
    private int burstDelay = 0;

    public SoldadoShootGoal(SoldadoEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private double range() {
        return switch (mob.getRole()) {
            case SoldadoEntity.ROLE_SHOTGUN -> 32.0;
            case SoldadoEntity.ROLE_SNIPER -> 110.0;
            default -> 64.0;
        };
    }

    private double holdDistance() {
        return switch (mob.getRole()) {
            case SoldadoEntity.ROLE_SHOTGUN -> 8.0;
            case SoldadoEntity.ROLE_SNIPER -> 999.0;     // no se mueve
            default -> 22.0;
        };
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && mob.getRole() >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || burstLeft > 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        burstLeft = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        mob.getLookControl().setLookAt(target, 40.0f, 40.0f);
        double dist = mob.distanceTo(target);
        boolean los = mob.getSensing().hasLineOfSight(target);
        boolean sniper = mob.getRole() == SoldadoEntity.ROLE_SNIPER;

        // Montado en una torreta: la maneja con el arma de ella
        if (mob.getVehicle() instanceof TurretEntity turret) {
            mob.getNavigation().stop();
            if (los && dist < 90.0) {
                turret.fireByMob(mob, target);
            }
            return;
        }

        // Movimiento: sólo avanza si el objetivo está dentro de su zona; si él se desvió, vuelve a su puesto
        if (!mob.isWithinRestriction()) {
            BlockPos c = mob.getRestrictCenter();
            mob.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.0);
        } else if (!sniper && (!los || dist > holdDistance()) && mob.isWithinRestriction(target.blockPosition())) {
            mob.getNavigation().moveTo(target, 1.0);
        } else {
            mob.getNavigation().stop();
        }

        if (cooldown > 0) {
            cooldown--;
        }
        if (burstLeft > 0) {                               // ráfaga de AK-47 en curso
            if (--burstDelay <= 0) {
                fire(level, target, dist);
                burstLeft--;
                burstDelay = 2;
                if (burstLeft == 0) {
                    cooldown = 30;
                }
            }
            return;
        }
        if (cooldown > 0 || !los || dist > range()) {
            return;
        }
        switch (mob.getRole()) {
            case SoldadoEntity.ROLE_SHOTGUN -> {
                fire(level, target, dist);
                cooldown = 42;
            }
            case SoldadoEntity.ROLE_SNIPER -> {
                fire(level, target, dist);
                cooldown = 70;
            }
            default -> {
                burstLeft = 4;
                burstDelay = 0;
            }
        }
    }

    private static boolean isAlly(Entity e) {
        return e instanceof SoldadoEntity || e instanceof TankEntity || e instanceof TurretEntity || e instanceof AncianoEntity;
    }

    private void fire(ServerLevel level, LivingEntity target, double dist) {
        Vec3 eye = mob.getEyePosition();
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 dir = aim.subtract(eye).normalize();
        DamageSource source = level.damageSources().mobAttack(mob);
        int role = mob.getRole();

        if (role == SoldadoEntity.ROLE_SHOTGUN) {
            Map<Entity, Float> damage = new LinkedHashMap<>();
            for (int i = 0; i < 9; i++) {
                Vec3 d = Bullets.spread(level, dir, 0.07);
                Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 40.0);
                if (hit.entity() != null && !isAlly(hit.entity())) {
                    float falloff = (float) Math.max(0.0, 1.0 - hit.distance() / 30.0);
                    damage.merge(hit.entity(), 0.5f + 3.2f * falloff, Float::sum);
                }
            }
            for (Map.Entry<Entity, Float> e : damage.entrySet()) {
                e.getKey().invulnerableTime = 0;
                e.getKey().hurt(source, e.getValue());
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE, 1.0f, 1.5f);
            effects(level, eye, dir, 6);
        } else if (role == SoldadoEntity.ROLE_SNIPER) {
            Vec3 d = Bullets.spread(level, dir, 0.004);
            Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 130.0);
            if (hit.entity() != null && !isAlly(hit.entity())) {
                hit.entity().invulnerableTime = 0;
                hit.entity().hurt(source, 14.0f);
            }
            for (double s = 2.0; s < hit.distance(); s += 2.0) {      // estela de la bala
                Vec3 p = eye.add(d.scale(s));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE, 1.6f, 1.9f);
            effects(level, eye, d, 5);
        } else {
            Vec3 d = Bullets.spread(level, dir, 0.045 + dist * 0.0012);
            Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 90.0);
            if (hit.entity() != null && !isAlly(hit.entity())) {
                hit.entity().invulnerableTime = 0;
                hit.entity().hurt(source, 3.5f);
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.HOSTILE, 1.4f, 0.55f);
            effects(level, eye, d, 2);
        }
    }

    private void effects(ServerLevel level, Vec3 eye, Vec3 dir, int smoke) {
        Vec3 muzzle = eye.add(dir.scale(0.9));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, smoke, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
    }
}
