package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Disparo eléctrico.
 * BALL: bola azul; al golpear a una entidad la descarga salta a las cercanas.
 * SPARK: chispa que ATRAVIESA entidades (daña a todas las que encuentra) y se apaga contra un bloque.
 */
public class ElectricShot extends ThrowableItemProjectile {
    public static final int BALL = 0, SPARK = 1, TRACER = 2, ORB = 3;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> KIND =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ElectricShot.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private boolean ignite;

    private int kind = BALL;
    private float damage = 6.0f;
    private final Set<Integer> alreadyHit = new HashSet<>();

    public ElectricShot(EntityType<? extends ElectricShot> type, Level level) {
        super(type, level);
    }

    public ElectricShot(Level level, LivingEntity owner, int kind, float damage) {
        super(ModEntities.ELECTRIC_SHOT.get(), owner, level);
        this.kind = kind;
        this.damage = damage;
        this.setNoGravity(true);
        this.entityData.set(KIND, kind);
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public void setIgnite(boolean ignite) {
        this.ignite = ignite;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, BALL);
    }

    /** Evita volver a golpear a una entidad ya atravesada (o a la que originó la división). */
    public void ignore(Entity e) {
        this.alreadyHit.add(e.getId());
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BOLA_ELECTRICA.get();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !this.alreadyHit.contains(e.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", this.kind);
        tag.putFloat("Damage", this.damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.kind = tag.getInt("Kind");
        this.entityData.set(KIND, this.kind);
        this.damage = tag.getFloat("Damage");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > (this.kind == BALL ? 60 : this.kind == TRACER ? 40 : this.kind == ORB ? 60 : 25)) {
            this.discard();
            return;
        }
        if (this.kind == ORB) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.5), getZ(), 3, 0.25, 0.25, 0.25, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
        }
        if (this.kind == SPARK) {
            level.sendParticles(ParticleTypes.FLAME, getX(), getY(0.5), getZ(), 2, 0.04, 0.04, 0.04, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.5), getZ(), 2, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void zap(Entity target, float amount) {
        target.invulnerableTime = 0;
        Entity owner = this.getOwner();
        target.hurt(this.level().damageSources().thrown(this, owner != null ? owner : this), amount);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = result.getEntity();
        this.alreadyHit.add(target.getId());
        zap(target, this.damage);

        if (this.kind == ORB) {
            this.implode(level, result.getLocation());
            this.discard();
            return;
        }
        if (this.kind == SPARK) {
            level.sendParticles(ParticleTypes.FLAME, result.getLocation().x, result.getLocation().y,
                    result.getLocation().z, 5, 0.12, 0.12, 0.12, 0.04);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, result.getLocation().x, result.getLocation().y,
                    result.getLocation().z, 3, 0.12, 0.12, 0.12, 0.02);
            return;                                           // la chispa sigue de largo
        }
        if (this.kind == TRACER) {
            if (this.ignite) {
                target.setRemainingFireTicks(80);
            }
            this.discard();
            return;
        }

        // BALL: la descarga salta a las entidades cercanas
        Vec3 center = target.getBoundingBox().getCenter();
        AABB area = target.getBoundingBox().inflate(4.5);
        int count = 0;
        for (Entity other : level.getEntities(this, area,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != this.getOwner())) {
            if (other == target || other.distanceToSqr(target) > 4.5 * 4.5) continue;
            if (count >= 6) break;
            count++;
            zap(other, this.damage * 0.6f);
            drawArc(level, center, other.getBoundingBox().getCenter());
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 20, 0.4, 0.4, 0.4, 0.15);
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.3f, 2.0f);
        this.discard();
    }

    /** Esfera gravitatoria: al estallar atrae a las entidades cercanas hacia el punto de impacto. */
    private void implode(ServerLevel level, Vec3 at) {
        double radius = 5.0;
        for (Entity e : level.getEntities(this, new AABB(at, at).inflate(radius),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x != this.getOwner())) {
            Vec3 to = at.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d > radius || d < 1.0E-3) continue;
            double pull = 0.9 * (1.0 - d / radius);
            e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(pull)));
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 40, 0.8, 0.8, 0.8, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.kind == ORB && this.level() instanceof ServerLevel orbLevel) {
            this.implode(orbLevel, result.getLocation());
        }
        if (this.level() instanceof ServerLevel level) {
            Vec3 p = result.getLocation();
            if (this.kind == SPARK) {
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.03);
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            } else {
                level.sendParticles(this.kind == TRACER ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z, 5, 0.15, 0.15, 0.15, 0.08);
            }
            this.discard();
        }
    }

    private void drawArc(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(2, (int) (delta.length() / 0.4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale((double) i / steps));
            double j = (i == 0 || i == steps) ? 0.0 : 0.25;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x + (level.random.nextDouble() - 0.5) * 2 * j,
                    p.y + (level.random.nextDouble() - 0.5) * 2 * j,
                    p.z + (level.random.nextDouble() - 0.5) * 2 * j, 1, 0, 0, 0, 0);
        }
    }
}
