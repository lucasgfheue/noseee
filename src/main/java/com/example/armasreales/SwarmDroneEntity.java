package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Dispositivo autónomo del ENJAMBRE: un pequeño dron virtual que persigue a su objetivo marcado (o al enemigo
 * más cercano si no tiene) girando con rapidez (control 94). Al alcanzarlo hace un daño leve y lo ralentiza;
 * impactos repetidos aumentan la ralentización hasta Lentitud III, sin inmovilizar. No rompe bloques.
 */
public class SwarmDroneEntity extends Entity {
    public static final int LIFETIME = 140;
    private static final double SPEED = 0.85;
    private static final double TURN = 0.38;               // control 94: giro rápido
    private static final float DAMAGE = 3.0f;
    private static final int SLOW_TICKS = 90;
    private static final double SEARCH = 20.0;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.25f, 1.0f, 0.4f), 0.6f);

    private UUID owner;
    private UUID target;
    private Vec3 vel = Vec3.ZERO;

    public SwarmDroneEntity(EntityType<? extends SwarmDroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static void launch(ServerLevel level, Entity owner, Vec3 from, Vec3 dir, LivingEntity target, double startSpeed) {
        SwarmDroneEntity d = new SwarmDroneEntity(ModEntities.SWARM_DRONE.get(), level);
        d.owner = owner.getUUID();
        d.target = target == null ? null : target.getUUID();
        d.vel = dir.normalize().scale(startSpeed);
        d.setPos(from.x, from.y, from.z);
        d.setDeltaMovement(d.vel);
        level.addFreshEntity(d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    private LivingEntity resolveTarget(ServerLevel level) {
        if (this.target != null) {
            Entity e = level.getEntity(this.target);
            if (e instanceof LivingEntity le && le.isAlive()) {
                return le;
            }
            this.target = null;
        }
        // Sin objetivo marcado: busca al enemigo hostil más cercano
        LivingEntity best = null;
        double bestD = SEARCH * SEARCH;
        for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(SEARCH),
                x -> x instanceof Enemy && x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = (LivingEntity) e;
            }
        }
        if (best != null) {
            this.target = best.getUUID();
        }
        return best;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.tickCount > LIFETIME) {
            fizzle(level);
            return;
        }
        LivingEntity t = this.tickCount > 4 ? resolveTarget(level) : null;
        Vec3 pos = this.position();
        if (t != null) {
            Vec3 aim = t.getBoundingBox().getCenter();
            Vec3 want = aim.subtract(pos);
            double dist = want.length();
            if (t.getBoundingBox().inflate(0.35).contains(pos) || dist < 0.9) {
                hit(level, t);
                return;
            }
            Vec3 desired = want.normalize().scale(SPEED);
            this.vel = this.vel.add(desired.subtract(this.vel).scale(TURN));
        } else {
            this.vel = this.vel.scale(0.98);                       // sin objetivo, planea y se apaga
            if (this.vel.length() < 0.15) {
                this.vel = this.vel.normalize().scale(0.15);
            }
        }
        Vec3 next = pos.add(this.vel);
        if (level.getBlockState(BlockPos.containing(next)).blocksMotion()) {
            fizzle(level);
            return;
        }
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(this.vel);
        this.hasImpulse = true;
        level.sendParticles(TRAIL, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.0);
        if (this.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        if (this.tickCount % 8 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.BEE_LOOP,
                    SoundSource.PLAYERS, 0.25f, 1.8f);
        }
    }

    private void hit(ServerLevel level, LivingEntity t) {
        Player p = this.owner == null ? null : level.getPlayerByUUID(this.owner);
        DamageSource src = p != null ? level.damageSources().playerAttack(p) : level.damageSources().magic();
        t.invulnerableTime = 0;
        t.hurt(src, DAMAGE);
        int amp = 0;
        MobEffectInstance cur = t.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (cur != null) {
            amp = Math.min(2, cur.getAmplifier() + 1);
        }
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, amp));
        Vec3 c = t.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 5, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, t.blockPosition(), SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 0.8f, 1.8f);
        this.discard();
    }

    private void fizzle(ServerLevel level) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
