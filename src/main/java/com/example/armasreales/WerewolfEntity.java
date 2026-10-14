package com.example.armasreales;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Hombre lobo: de día parece un jugador cualquiera y no molesta; al caer la noche se transforma en una
 * bestia enorme que ataca a los jugadores. Al amanecer vuelve a su forma humana.
 */
public class WerewolfEntity extends Monster {
    public static final int HUMAN_SKINS = 4;
    /** Pelaje de la bestia: 0 marrón, 1 negro, 2 blanco. */
    public static final int FUR_VARIANTS = 3;

    private static final EntityDataAccessor<Boolean> DATA_WOLF =
            SynchedEntityData.defineId(WerewolfEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(WerewolfEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_FUR =
            SynchedEntityData.defineId(WerewolfEntity.class, EntityDataSerializers.INT);

    public WerewolfEntity(EntityType<? extends WerewolfEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WerewolfBreakGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Sólo caza jugadores cuando es una bestia (de noche)
        // mustSee = false: te detecta a través de las paredes
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 10, false, false, null) {
            @Override
            public boolean canUse() {
                return WerewolfEntity.this.isWolf() && super.canUse();
            }
        });
    }

    // ------------------------------------------------------------------ Datos sincronizados

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WOLF, false);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_FUR, 0);
    }

    public boolean isWolf() {
        return this.entityData.get(DATA_WOLF);
    }

    public int getFur() {
        return Math.floorMod(this.entityData.get(DATA_FUR), FUR_VARIANTS);
    }

    public int getHumanSkin() {
        return Math.floorMod(this.entityData.get(DATA_SKIN), HUMAN_SKINS);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Wolf", this.isWolf());
        tag.putInt("HumanSkin", this.getHumanSkin());
        tag.putInt("Fur", this.getFur());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_WOLF, tag.getBoolean("Wolf"));
        this.entityData.set(DATA_SKIN, tag.getInt("HumanSkin"));
        this.entityData.set(DATA_FUR, tag.getInt("Fur"));
    }

    // ------------------------------------------------------------------ Transformación

    /** Noche del Overworld: del minuto 13000 al 23000 del día. */
    private boolean isNightNow() {
        if (this.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        long t = this.level().getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setAggressive(this.getTarget() != null && this.getTarget().isAlive());
        }
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            boolean wantWolf = this.isNightNow();
            if (wantWolf != this.isWolf()) {
                this.transform(wantWolf);
            }
        }
    }

    private void transform(boolean wolf) {
        this.entityData.set(DATA_WOLF, wolf);
        this.applyForm(wolf, true);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(0.5), getZ(), 40, 0.5, 0.9, 0.5, 0.05);
            level.sendParticles(ParticleTypes.POOF, getX(), getY(0.5), getZ(), 20, 0.5, 0.9, 0.5, 0.1);
            if (wolf) {
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 3.0f, 0.5f);
            } else {
                this.setTarget(null);
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_WHINE, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
        }
    }

    /** Ajusta vida, daño, velocidad y tamaño según la forma. */
    private void applyForm(boolean wolf, boolean keepHealthRatio) {
        float ratio = this.getHealth() / this.getMaxHealth();
        setBase(Attributes.MAX_HEALTH, wolf ? 80.0 : 30.0);
        setBase(Attributes.ATTACK_DAMAGE, wolf ? 10.0 : 3.0);
        setBase(Attributes.MOVEMENT_SPEED, wolf ? 0.36 : 0.25);
        setBase(Attributes.KNOCKBACK_RESISTANCE, wolf ? 0.5 : 0.0);
        setBase(Attributes.SCALE, wolf ? 1.45 : 1.0);
        if (keepHealthRatio) {
            this.setHealth(ratio * this.getMaxHealth());
        }
        this.refreshDimensions();
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.entityData.set(DATA_SKIN, level.getRandom().nextInt(HUMAN_SKINS));
        this.entityData.set(DATA_FUR, level.getRandom().nextInt(FUR_VARIANTS));
        boolean wolf = this.isNightNow();
        this.entityData.set(DATA_WOLF, wolf);
        this.applyForm(wolf, false);
        this.setHealth(this.getMaxHealth());
        return result;
    }

    // ------------------------------------------------------------------ Sonidos

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isWolf() ? SoundEvents.WOLF_GROWL : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isWolf() ? SoundEvents.WOLF_HURT : SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isWolf() ? SoundEvents.WOLF_DEATH : SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }
}
