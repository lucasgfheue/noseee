package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Escudo celeste transparente del Fusil Anillo. Sigue al jugador, dura 5 segundos y desvía los
 * proyectiles ENERGÉTICOS (disparos eléctricos y del taser) y detiene en seco cualquier otro proyectil
 * (flechas, cohetes, granadas...). No detiene ataques cuerpo a cuerpo ni explosiones ya detonadas. El dibujo lo hace ShieldRenderer.
 */
public class ShieldEntity extends Entity {
    public static final int DURATION = 100;           // 5 s
    public static final double DISTANCE = 2.2;        // centro de la cúpula respecto al ojo
    public static final double RADIUS = 2.0;          // radio de la cúpula

    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(ShieldEntity.class, EntityDataSerializers.INT);

    public ShieldEntity(EntityType<? extends ShieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer player) {
        ShieldEntity shield = new ShieldEntity(ModEntities.SHIELD.get(), level);
        shield.setOwnerId(player.getId());
        shield.setPos(player.getX(), player.getEyeY(), player.getZ());
        level.addFreshEntity(shield);
    }

    public int getOwnerId() { return this.entityData.get(OWNER_ID); }
    public void setOwnerId(int id) { this.entityData.set(OWNER_ID, id); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        Entity owner = this.level().getEntity(this.getOwnerId());
        if (!(owner instanceof ServerPlayer player) || !player.isAlive() || this.tickCount > DURATION) {
            this.discard();
            return;
        }
        this.setPos(player.getX(), player.getEyeY(), player.getZ());
        ServerLevel level = (ServerLevel) this.level();

        Vec3 look = player.getLookAngle();
        Vec3 center = player.getEyePosition().add(look.scale(DISTANCE));
        AABB zone = new AABB(center, center).inflate(RADIUS + 0.8);
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, zone,
                p -> true)) {
            if (proj.getOwner() == player) {
                continue;
            }
            Vec3 rel = proj.position().subtract(player.getEyePosition());
            double dist = rel.length();
            // Sólo lo que está dentro de la cúpula delantera y cerca de su superficie
            if (dist > DISTANCE + RADIUS + 0.6 || rel.normalize().dot(look) < 0.2) {
                continue;
            }
            if (!(proj instanceof ElectricShot || proj instanceof TaserProjectile)) {
                // Cualquier otro proyectil (flechas, cohetes, granadas, bolas de fuego...) se detiene contra el escudo
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(),
                        16, 0.25, 0.25, 0.25, 0.12);
                level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(),
                        5, 0.15, 0.15, 0.15, 0.05);
                level.playSound(null, proj.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 1.2f, 1.4f);
                proj.discard();
                continue;
            }
            Vec3 v = proj.getDeltaMovement();
            double speed = Math.max(v.length(), 1.2);
            Vec3 out = rel.normalize().add(
                    (level.random.nextDouble() - 0.5) * 0.3,
                    (level.random.nextDouble() - 0.5) * 0.2,
                    (level.random.nextDouble() - 0.5) * 0.3).normalize();
            proj.setOwner(player);
            proj.setDeltaMovement(out.scale(speed));
            proj.hasImpulse = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(),
                    14, 0.2, 0.2, 0.2, 0.15);
            level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(),
                    4, 0.1, 0.1, 0.1, 0.05);
            level.playSound(null, proj.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.2f, 1.9f);
        }
        if (this.tickCount % 20 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.4f, 2.0f);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
