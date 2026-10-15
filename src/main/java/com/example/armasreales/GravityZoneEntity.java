package com.example.armasreales;

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

import java.util.UUID;

/**
 * Zona «Horizonte» del X-29 Eclipse: durante 6 s atrae suavemente hacia su centro a los enemigos y los
 * ralentiza. El efecto es más fuerte en el centro, pero nunca inmoviliza del todo. No afecta a su dueño.
 */
public class GravityZoneEntity extends Entity {
    public static final int DURATION = 120;
    public static final double RADIUS = 7.0;

    private UUID owner;

    public GravityZoneEntity(EntityType<? extends GravityZoneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Entity owner, Vec3 at) {
        GravityZoneEntity zone = new GravityZoneEntity(ModEntities.GRAVITY_ZONE.get(), level);
        zone.owner = owner.getUUID();
        zone.setPos(at.x, at.y, at.z);
        level.addFreshEntity(zone);
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
        Vec3 center = this.position().add(0.0, 1.0, 0.0);

        AABB area = new AABB(center, center).inflate(RADIUS);
        for (Entity e : level.getEntities(this, area,
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator()
                        && (this.owner == null || !x.getUUID().equals(this.owner)))) {
            Vec3 to = center.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d > RADIUS) continue;
            double strength = 1.0 - d / RADIUS;                   // 0 en el borde, 1 en el centro
            // Tirón suave: más fuerte cerca del centro, pero acotado para no arrastrar de golpe
            if (d > 0.6) {
                Vec3 pull = to.normalize().scale(0.025 + 0.07 * strength);
                Vec3 v = e.getDeltaMovement().add(pull.x, pull.y * 0.4, pull.z);
                double maxH = 0.35;
                double h = Math.sqrt(v.x * v.x + v.z * v.z);
                if (h > maxH) {
                    v = new Vec3(v.x / h * maxH, v.y, v.z / h * maxH);
                }
                e.setDeltaMovement(v);
                e.hurtMarked = true;
            }
            // Ralentización graduada: Lentitud I en el borde, hasta IV en el centro (nunca inmoviliza)
            int amp = Math.min(3, (int) (strength * 4.0));
            ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, amp, false, false));
        }

        // Remolino visual: partículas que giran hacia el centro
        double spin = this.tickCount * 0.35;
        for (int arm = 0; arm < 3; arm++) {
            for (double r = 1.0; r <= RADIUS; r += 1.6) {
                double a = spin * (1.0 + 1.5 / r) + arm * 2 * Math.PI / 3 + r * 0.6;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        center.x + Math.cos(a) * r, center.y - 0.6 + (RADIUS - r) * 0.04, center.z + Math.sin(a) * r,
                        0, -Math.cos(a), 0.0, -Math.sin(a), 0.25);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 1, 0.1, 0.1, 0.1, 0.0);
        if (this.tickCount % 20 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.7f, 0.5f);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
