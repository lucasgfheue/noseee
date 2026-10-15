package com.example.armasreales;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Esfera solar del SOLARIS: durante 8 s flota sobre el punto elegido, ilumina (los seres vivos de la zona
 * brillan) y calienta un área muy amplia: quema suavemente a los enemigos, más cerca del centro. A su dueño
 * le quita el frío (nieve en polvo) y no lo daña. No destruye bloques.
 */
public class SolarSphereEntity extends Entity {
    public static final int DURATION = 160;
    public static final double RADIUS = 11.0;          // área de efecto 90/100
    public static final double HEIGHT = 2.6;           // altura del centro de la esfera sobre el suelo

    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.12f), 1.4f);

    private UUID owner;

    public SolarSphereEntity(EntityType<? extends SolarSphereEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Entity owner, Vec3 at) {
        SolarSphereEntity sphere = new SolarSphereEntity(ModEntities.SOLAR_SPHERE.get(), level);
        sphere.owner = owner.getUUID();
        sphere.setPos(at.x, at.y, at.z);
        level.addFreshEntity(sphere);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.tickCount > DURATION) {
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Vec3 center = this.position().add(0.0, HEIGHT, 0.0);

        if (this.tickCount % 10 == 0) {
            AABB area = new AABB(center, center).inflate(RADIUS);
            for (Entity e : level.getEntities(this, area, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
                LivingEntity target = (LivingEntity) e;
                double d = e.getBoundingBox().getCenter().distanceTo(center);
                if (d > RADIUS) continue;
                boolean isOwner = this.owner != null && e.getUUID().equals(this.owner);
                if (isOwner) {
                    target.setTicksFrozen(0);                      // el calor le quita el frío
                    continue;
                }
                double strength = 1.0 - d / RADIUS;                // 0 en el borde, 1 en el centro
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));   // iluminado
                if (this.tickCount % 20 == 0) {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().inFire(), (float) (0.5 + 2.0 * strength));
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 40 + (int) (60 * strength)));
                }
            }
        }

        // Brillo y calor visibles
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, this.tickCount % 40 == 0 ? 1 : 0, 0, 0, 0, 0);
        level.sendParticles(GLOW, center.x, center.y, center.z, 6, 0.8, 0.8, 0.8, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, center.x, center.y, center.z, 2, 0.9, 0.9, 0.9, 0.02);
        for (int i = 0; i < 4; i++) {                              // destellos repartidos por toda la zona
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(level.random.nextDouble()) * RADIUS;
            level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * r,
                    this.getY() + 0.3 + level.random.nextDouble() * 2.5, center.z + Math.sin(a) * r, 0, 0, 0.04, 0, 0.5);
        }
        if (this.tickCount % 20 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.FIRE_AMBIENT,
                    SoundSource.PLAYERS, 1.2f, 0.7f);
            level.playSound(null, this.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.7f, 1.6f);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
