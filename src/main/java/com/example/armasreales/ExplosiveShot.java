package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Proyectil explosivo genérico. Todas sus explosiones usan ExplosionInteraction.NONE: hieren a las
 * entidades pero NO destruyen bloques. Tipos: granada rebotante, cohete, racimo, adhesivo y minibomba.
 */
public class ExplosiveShot extends ThrowableItemProjectile {
    public static final int GRENADE = 0, ROCKET = 1, CLUSTER = 2, STICKY = 3, BOMBLET = 4;

    private int kind = GRENADE;
    private float power = 2.5f;
    private int fuse = 50;
    private boolean stuck;
    private Entity stuckTo;
    private Vec3 stuckOffset = Vec3.ZERO;

    public ExplosiveShot(EntityType<? extends ExplosiveShot> type, Level level) {
        super(type, level);
    }

    public ExplosiveShot(Level level, LivingEntity owner, int kind, float power, int fuse) {
        super(ModEntities.EXPLOSIVE_SHOT.get(), owner, level);
        this.kind = kind;
        this.power = power;
        this.fuse = fuse;
        if (kind == ROCKET) {
            this.setNoGravity(true);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CARGA_EXPLOSIVA.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", this.kind);
        tag.putFloat("Power", this.power);
        tag.putInt("Fuse", this.fuse);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.kind = tag.getInt("Kind");
        this.power = tag.getFloat("Power");
        this.fuse = tag.getInt("Fuse");
    }

    @Override
    public void tick() {
        if (this.stuck) {
            // pegado: se queda quieto (o sigue a la entidad a la que se adhirió)
            if (this.stuckTo != null) {
                if (this.stuckTo.isRemoved()) {
                    this.stuckTo = null;
                    this.stuck = false;
                    this.setNoGravity(false);
                } else {
                    this.setPos(this.stuckTo.position().add(this.stuckOffset));
                }
            }
            this.setDeltaMovement(Vec3.ZERO);
            this.baseTick();
        } else {
            super.tick();
        }
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > 400) {
            this.discard();
            return;
        }
        switch (this.kind) {
            case ROCKET -> {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY(0.5), getZ(), 2, 0.03, 0.03, 0.03, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.5), getZ(), 3, 0.05, 0.05, 0.05, 0.0);
            }
            case STICKY -> level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY(0.5), getZ(), 1, 0.08, 0.08, 0.08, 0.0);
            default -> level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(0.5) + 0.2, getZ(), 1, 0.03, 0.03, 0.03, 0.0);
        }
        boolean fused = this.kind == GRENADE || this.kind == BOMBLET || (this.kind == STICKY && this.stuck);
        if (fused && --this.fuse <= 0) {
            this.detonate();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        if (this.kind == STICKY) {
            this.stick(result.getEntity(), result.getLocation());
        } else if (this.kind == CLUSTER) {
            this.splitIntoSparks(result.getEntity());
        } else {
            this.detonate();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) {
            return;
        }
        switch (this.kind) {
            case ROCKET, CLUSTER -> this.detonate();
            case STICKY -> this.stick(null, result.getLocation());
            default -> {                                       // granada / minibomba: rebota
                Vec3 v = this.getDeltaMovement();
                switch (result.getDirection().getAxis()) {
                    case X -> v = new Vec3(-v.x * 0.2, v.y * 0.4, v.z * 0.4);
                    case Y -> v = new Vec3(v.x * 0.4, -v.y * 0.12, v.z * 0.4);
                    case Z -> v = new Vec3(v.x * 0.4, v.y * 0.4, -v.z * 0.2);
                }
                Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
                this.setPos(result.getLocation().add(n.scale(0.12)));
                this.setDeltaMovement(v);
            }
        }
    }

    private void stick(Entity target, Vec3 at) {
        this.stuck = true;
        this.stuckTo = target;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(at);
        if (target != null) {
            this.stuckOffset = at.subtract(target.position());
        }
        this.fuse = 40;
        this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.SLIME_BLOCK_PLACE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Explota sin romper bloques. */
    private void detonate() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        this.level().explode(this.getOwner() != null ? this.getOwner() : this, getX(), getY(0.5), getZ(),
                this.power, Level.ExplosionInteraction.NONE);
        this.discard();
    }

    /** El racimo golpea a una entidad: se parte en 5 chispas (no explosivas) que siguen de largo y hieren lo que hay detrás. */
    private void splitIntoSparks(Entity hit) {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        Entity owner = this.getOwner();
        hit.invulnerableTime = 0;
        hit.hurt(this.level().damageSources().thrown(this, owner != null ? owner : this), 5.0f);
        Vec3 dir = this.getDeltaMovement();
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        if (owner instanceof LivingEntity living) {
            for (int i = 0; i < 5; i++) {
                ElectricShot spark = new ElectricShot(this.level(), living, ElectricShot.SPARK, 4.0f);
                spark.ignore(hit);
                spark.setPos(getX(), getY(0.5), getZ());
                Vec3 d = dir.add((this.random.nextDouble() - 0.5) * 0.45,
                        (this.random.nextDouble() - 0.5) * 0.3,
                        (this.random.nextDouble() - 0.5) * 0.45).normalize();
                spark.setDeltaMovement(d.scale(2.0));
                this.level().addFreshEntity(spark);
            }
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.5), getZ(), 25, 0.3, 0.3, 0.3, 0.2);
        }
        this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_TWINKLE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.2f);
        this.discard();
    }
}
