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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Torreta controlable. Clic derecho para subirte; la torreta apunta hacia donde miras y el clic izquierdo
 * dispara. Tipo 0: ametralladora (munición de fusil, ráfaga continua). Tipo 1: cañón pesado (dispara TNT).
 * Shift + clic con la mano vacía la recoge.
 */
public class TurretEntity extends Mob {
    public static final int MG = 0;
    public static final int HEAVY = 1;
    /** Altura del eje de giro del arma sobre los pies de la torreta. */
    public static final double PIVOT_Y = 1.1;

    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(TurretEntity.class, EntityDataSerializers.INT);

    private int cooldown = 0;

    public TurretEntity(EntityType<? extends TurretEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, MG);
    }

    public int getTurretType() {
        return this.entityData.get(DATA_TYPE);
    }

    public void setTurretType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TurretType", this.getTurretType());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setTurretType(tag.getInt("TurretType"));
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private Item pickupItem() {
        return this.getTurretType() == HEAVY ? ModItems.TORRETA_PESADA.get() : ModItems.TORRETA.get();
    }

    // ------------------------------------------------------------------ Interacción

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.isEmpty() && !this.isVehicle()) {
            if (!this.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    this.spawnAtLocation(new ItemStack(this.pickupItem()));
                }
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(this.pickupItem()));
    }

    // ------------------------------------------------------------------ Tick: sigue la mira del jugador

    @Override
    public void tick() {
        super.tick();
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.getFirstPassenger() instanceof Player rider) {
            this.setYRot(rider.getYRot());
            this.yRotO = this.getYRot();
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }
    }

    // ------------------------------------------------------------------ Disparo

    /** Lo llama el servidor mientras el jugador mantiene el clic izquierdo montado en la torreta. */
    public void fire(ServerPlayer player) {
        if (this.getFirstPassenger() != player || this.cooldown > 0 || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        Vec3 dir = player.getLookAngle();
        Vec3 pivot = this.position().add(0.0, PIVOT_Y, 0.0);
        boolean heavy = this.getTurretType() == HEAVY;
        ItemStack ammo = AmmoUtil.find(player, heavy ? Items.TNT : ModItems.MUNICION_FUSIL.get());
        if (ammo.isEmpty() && !creative) {
            level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0f, 1.6f);
            this.cooldown = 10;
            return;
        }
        Vec3 muzzle = pivot.add(dir.scale(heavy ? 1.6 : 1.3));
        if (heavy) {
            TntProjectile shell = new TntProjectile(level, player);
            shell.setPos(muzzle.x, muzzle.y - shell.getBbHeight() / 2.0, muzzle.z);
            shell.shoot(dir.x, dir.y, dir.z, 3.2f, 0.0f);
            level.addFreshEntity(shell);
            level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.05);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.15, 0.15, 0.03);
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                    SoundSource.PLAYERS, 3.0f, 0.5f);
            this.cooldown = 30;
        } else {
            Vec3 d = Bullets.spread(level, dir, 0.012);
            Bullets.Hit hit = Bullets.trace(level, this, muzzle, d, 120.0,
                    e -> e != player && e != this && !e.isPassengerOfSameVehicle(this));
            if (hit.entity() != null) {
                hit.entity().invulnerableTime = 0;
                hit.entity().hurt(level.damageSources().playerAttack(player), 6.0f);
            }
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.04, 0.04, 0.04, 0.01);
            level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.0);
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS, 1.4f, 0.6f);
            this.cooldown = 2;
        }
        if (!creative) {
            ammo.shrink(1);
        }
    }
}
