package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Anciano de barba blanca que vive en la torre de radio: sentado junto a su radio, siempre con una escopeta.
 * Está sentado en su silla; cuando ve a alguien se levanta, lo persigue y le dispara.
 */
public class AncianoEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_SITTING =
            SynchedEntityData.defineId(AncianoEntity.class, EntityDataSerializers.BOOLEAN);

    public AncianoEntity(EntityType<? extends AncianoEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SITTING, false);
    }

    public boolean isSitting() {
        return this.entityData.get(DATA_SITTING);
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(DATA_SITTING, sitting);
        this.setNoGravity(sitting);
    }

    /** Se levanta de la silla: sube lo que estaba hundido en el asiento y vuelve a tener gravedad. */
    private void standUp() {
        this.setSitting(false);
        this.setPos(this.getX(), this.getY() + 0.65, this.getZ());
        this.playSound(SoundEvents.WOODEN_TRAPDOOR_OPEN, 1.0f, 0.7f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sitting", this.isSitting());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean sitting = tag.getBoolean("Sitting");
        this.setSitting(sitting);
        if (sitting) {
            this.yBodyRot = this.getYRot();
            this.yBodyRotO = this.getYRot();
            this.yHeadRot = this.getYRot();
            this.yHeadRotO = this.getYRot();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AncianoShootGoal(this));
        this.goalSelector.addGoal(2, new AncianoChaseGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Siempre lleva su escopeta
        if (this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ESCOPETA.get()));
            this.setDropChance(EquipmentSlot.MAINHAND, 1.0f);
        }
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        this.setAggressive(hasTarget);
        if (this.isSitting()) {
            if (hasTarget) {
                this.standUp();      // nos vio: se levanta de la silla
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}
