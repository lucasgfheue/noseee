package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** El zombi dispara el arma que lleva en la mano y arroja cuchillos o bombas a su objetivo. */
public class ZombieShootGoal extends Goal {
    private static final double GUN_RANGE = 24.0;
    private static final double THROW_MIN = 5.0;
    private static final double THROW_MAX = 16.0;

    private final ArmedZombie zombie;
    private int shotCooldown = 20;
    private int throwCooldown = 40;
    private int burstLeft = 0;
    private int burstDelay = 0;

    public ZombieShootGoal(ArmedZombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean hasGun() {
        Item i = zombie.getMainHandItem().getItem();
        return i == ModItems.PISTOLA.get() || i == ModItems.ESCOPETA.get() || i == ModItems.AK47.get();
    }

    private boolean hasThrowable() {
        ItemStack s = zombie.getOffhandItem();
        return !s.isEmpty() && (s.is(ModItems.CUCHILLO.get()) || s.is(ModItems.BOMBA.get()));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = zombie.getTarget();
        if (t == null || !t.isAlive() || !(hasGun() || hasThrowable())) {
            return false;
        }
        double max = hasGun() ? GUN_RANGE : THROW_MAX;
        return zombie.distanceToSqr(t) < max * max && zombie.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || this.burstLeft > 0;
    }

    @Override
    public void start() {
        zombie.setAggressive(true);
    }

    @Override
    public void stop() {
        zombie.setAggressive(false);
        zombie.getNavigation().stop();
        this.burstLeft = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (target == null || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        zombie.getLookControl().setLookAt(target, 40.0f, 40.0f);
        double dist = zombie.distanceTo(target);
        // Se acerca hasta quedar a tiro y se planta
        if (dist > 12.0 || (!hasGun() && dist > THROW_MAX - 2)) {
            zombie.getNavigation().moveTo(target, 1.0);
        } else {
            zombie.getNavigation().stop();
        }

        if (this.throwCooldown > 0) this.throwCooldown--;
        if (this.shotCooldown > 0) this.shotCooldown--;

        // Ráfaga en curso (AK-47)
        if (this.burstLeft > 0) {
            if (--this.burstDelay <= 0) {
                fireGun(level, target, zombie.getMainHandItem().getItem(), dist);
                this.burstLeft--;
                this.burstDelay = 2;
                if (this.burstLeft == 0) this.shotCooldown = 35;
            }
            return;
        }

        if (hasGun() && this.shotCooldown <= 0 && zombie.getSensing().hasLineOfSight(target)) {
            Item gun = zombie.getMainHandItem().getItem();
            if (gun == ModItems.AK47.get()) {
                this.burstLeft = 4;
                this.burstDelay = 0;
            } else {
                fireGun(level, target, gun, dist);
                this.shotCooldown = gun == ModItems.ESCOPETA.get() ? 45 : 25;
            }
        } else if (hasThrowable() && this.throwCooldown <= 0 && dist >= THROW_MIN && dist <= THROW_MAX
                && zombie.getSensing().hasLineOfSight(target)) {
            throwItem(level, target, dist);
            this.throwCooldown = 70 + zombie.getRandom().nextInt(50);
        }
    }

    // ------------------------------------------------------------------ Disparo

    private void fireGun(ServerLevel level, LivingEntity target, Item gun, double dist) {
        Vec3 eye = zombie.getEyePosition();
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 dir = aim.subtract(eye).normalize();
        DamageSource source = level.damageSources().mobAttack(zombie);

        if (gun == ModItems.ESCOPETA.get()) {
            Map<Entity, Float> damage = new LinkedHashMap<>();
            for (int i = 0; i < 8; i++) {
                Vec3 d = Bullets.spread(level, dir, 0.07);
                Bullets.Hit hit = Bullets.trace(level, zombie, eye, d, 30.0);
                if (hit.entity() != null && !(hit.entity() instanceof Zombie)) {
                    float falloff = (float) Math.max(0.0, 1.0 - hit.distance() / 20.0);
                    damage.merge(hit.entity(), 0.4f + 3.0f * falloff, Float::sum);
                }
            }
            for (Map.Entry<Entity, Float> e : damage.entrySet()) {
                e.getKey().invulnerableTime = 0;
                e.getKey().hurt(source, e.getValue());
            }
            level.playSound(null, zombie.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE, 0.7f, 1.6f);
            effects(level, eye, dir, 6);
        } else {
            boolean ak = gun == ModItems.AK47.get();
            float dmg = ak ? 3.0f : 4.0f;
            double spread = (ak ? 0.05 : 0.035) + dist * 0.0015;
            Vec3 d = Bullets.spread(level, dir, spread);
            Bullets.Hit hit = Bullets.trace(level, zombie, eye, d, 50.0);
            if (hit.entity() != null && !(hit.entity() instanceof Zombie)) {
                hit.entity().invulnerableTime = 0;
                hit.entity().hurt(source, dmg);
            }
            level.playSound(null, zombie.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.HOSTILE, 1.4f, ak ? 0.55f : 0.7f);
            effects(level, eye, d, 2);
        }
    }

    private void effects(ServerLevel level, Vec3 eye, Vec3 dir, int smoke) {
        Vec3 muzzle = eye.add(dir.scale(0.9));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, smoke, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
    }

    // ------------------------------------------------------------------ Lanzamiento

    private void throwItem(ServerLevel level, LivingEntity target, double dist) {
        ItemStack stack = zombie.getOffhandItem();
        double dx = target.getX() - zombie.getX();
        double dy = target.getEyeY() - zombie.getEyeY() + dist * 0.12;   // compensa la caída
        double dz = target.getZ() - zombie.getZ();
        if (stack.is(ModItems.BOMBA.get())) {
            BombProjectile bomb = new BombProjectile(level, zombie);
            bomb.setPos(zombie.getX(), zombie.getEyeY() - 0.2, zombie.getZ());
            bomb.shoot(dx, dy, dz, 1.3f, 2.0f);
            level.addFreshEntity(bomb);
            level.playSound(null, zombie.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 0.8f, 1.2f);
        } else {
            ThrownKnife knife = new ThrownKnife(level, zombie);
            knife.setPos(zombie.getX(), zombie.getEyeY() - 0.2, zombie.getZ());
            knife.shoot(dx, dy - dist * 0.05, dz, 2.2f, 2.5f);
            level.addFreshEntity(knife);
            level.playSound(null, zombie.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                    SoundSource.HOSTILE, 0.8f, 1.6f);
        }
        stack.shrink(1);
    }
}
