package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Soldado de la base militar: hostil, 40 de vida, armado con AK-47, escopeta o francotirador.
 * No se aleja de su puesto: vuelve a él si se desvía y suelta a los objetivos lejanos.
 */
public class SoldadoEntity extends Monster {
    public static final int SKINS = 4;
    public static final int ROLE_AK = 0;
    public static final int ROLE_SHOTGUN = 1;
    public static final int ROLE_SNIPER = 2;
    private static final int HOME_RADIUS = 40;

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(SoldadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(SoldadoEntity.class, EntityDataSerializers.INT);

    private int turretIdle;
    private boolean homeSet;
    private BlockPos home;

    public SoldadoEntity(EntityType<? extends SoldadoEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, -1);
        builder.define(DATA_SKIN, -1);
    }

    public int getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    public void setRole(int role) {
        this.entityData.set(DATA_ROLE, role);
    }

    public int getSkin() {
        return Math.floorMod(this.entityData.get(DATA_SKIN), SKINS);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, skin);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SoldadoMountTurretGoal(this));
        this.goalSelector.addGoal(2, new SoldadoShootGoal(this));
        this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return SoldadoEntity.this.getRole() != ROLE_SNIPER && super.canUse();   // los francotiradores no patrullan
            }
        });
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static Item weaponFor(int role) {
        return switch (role) {
            case ROLE_SHOTGUN -> ModItems.ESCOPETA.get();
            case ROLE_SNIPER -> ModItems.FRANCOTIRADOR.get();
            default -> ModItems.AK47.get();
        };
    }

    private static Item ammoFor(int role) {
        return switch (role) {
            case ROLE_SHOTGUN -> ModItems.CARTUCHO_ESCOPETA.get();
            case ROLE_SNIPER -> ModItems.MUNICION_FRANCOTIRADOR.get();
            default -> ModItems.MUNICION_FUSIL.get();
        };
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.getRole() < 0) {                       // sin rol (huevo de generación): se sortea
            int r = this.random.nextInt(10);
            this.setRole(r < 5 ? ROLE_AK : r < 8 ? ROLE_SHOTGUN : ROLE_SNIPER);
        }
        if (this.entityData.get(DATA_SKIN) < 0) {
            this.setSkin(this.random.nextInt(SKINS));
        }
        Item weapon = weaponFor(this.getRole());
        if (!this.getMainHandItem().is(weapon)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.1f);
        }
        if (!this.homeSet) {                            // el puesto donde apareció es su hogar
            this.homeSet = true;
            this.home = this.blockPosition();
            this.restrictTo(this.home, HOME_RADIUS);
        }
        LivingEntity target = this.getTarget();
        if (target != null && this.home != null
                && target.distanceToSqr(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5)
                > (double) (HOME_RADIUS + 40) * (HOME_RADIUS + 40)) {
            this.setTarget(null);                        // no persigue más allá de su base
            target = null;
        }
        this.setAggressive(target != null && target.isAlive());
        // Se baja de la torreta cuando pierde de vista al enemigo
        if (this.getVehicle() instanceof TurretEntity) {
            if (target == null) {
                if (++this.turretIdle > 100) {
                    this.stopRiding();
                }
            } else {
                this.turretIdle = 0;
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ammoFor(this.getRole()), 4 + this.random.nextInt(9)));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", this.getRole());
        tag.putInt("Skin", this.entityData.get(DATA_SKIN));
        if (this.home != null) {
            tag.putInt("HomeX", this.home.getX());
            tag.putInt("HomeY", this.home.getY());
            tag.putInt("HomeZ", this.home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Role")) {
            this.setRole(tag.getInt("Role"));
        }
        if (tag.contains("Skin")) {
            this.setSkin(tag.getInt("Skin"));
        }
        if (tag.contains("HomeX")) {
            this.home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            this.homeSet = true;
            this.restrictTo(this.home, HOME_RADIUS);
        }
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
