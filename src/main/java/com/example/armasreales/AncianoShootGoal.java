package com.example.armasreales;

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

/** El anciano apunta y dispara su escopeta (10 perdigones, muy dañina de cerca) desde su silla. */
public class AncianoShootGoal extends Goal {
    private static final double RANGE = 28.0;
    private final AncianoEntity mob;
    private int cooldown = 30;

    public AncianoShootGoal(AncianoEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    private boolean hasShotgun() {
        return mob.getMainHandItem().is(ModItems.ESCOPETA.get());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && hasShotgun() && mob.distanceToSqr(t) < RANGE * RANGE
                && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
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
        mob.getLookControl().setLookAt(target, 60.0f, 60.0f);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        fire(level, target);
        cooldown = 45;
    }

    private void fire(ServerLevel level, LivingEntity target) {
        Vec3 eye = mob.getEyePosition();
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 dir = aim.subtract(eye).normalize();
        DamageSource source = level.damageSources().mobAttack(mob);
        Map<Entity, Float> damage = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            Vec3 d = Bullets.spread(level, dir, 0.06);
            Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 32.0);
            if (hit.entity() != null && !(hit.entity() instanceof AncianoEntity)) {
                float falloff = (float) Math.max(0.0, 1.0 - hit.distance() / 24.0);
                damage.merge(hit.entity(), 0.6f + 3.2f * falloff, Float::sum);
            }
        }
        for (Map.Entry<Entity, Float> e : damage.entrySet()) {
            e.getKey().invulnerableTime = 0;
            e.getKey().hurt(source, e.getValue());
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 1.0f, 1.5f);
        Vec3 muzzle = eye.add(dir.scale(0.9));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 6, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
    }
}
