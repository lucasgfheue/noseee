package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Sobreviviente prisionero: pelea contra los mobs hostiles. Con el jugador es neutral mientras lo ve;
 * si lo golpean, o si el jugador sale de su vista, lo cazan hasta matarlo.
 */
public class SobrevivienteEntity extends PathfinderMob {
    public static final int SKINS = 5;
    private static final int HOME_RADIUS = 24;
    private static final int LOSE_SIGHT_TICKS = 60;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(SobrevivienteEntity.class, EntityDataSerializers.INT);

    private boolean angry;
    private UUID angerTarget;
    private UUID seenPlayer;
    private int unseenTicks;
    private boolean homeSet;
    private BlockPos home;

    public SobrevivienteEntity(EntityType<? extends SobrevivienteEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SobrevivienteShootGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new AngerGoal());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Mob>(this, Mob.class, 5, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper) && !(e instanceof SobrevivienteEntity)));
    }

    // ------------------------------------------------------------------ Datos

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, -1);
    }

    public int getSkin() {
        return Math.floorMod(this.entityData.get(DATA_SKIN), SKINS);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, skin);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", this.entityData.get(DATA_SKIN));
        tag.putBoolean("Angry", this.angry);
        if (this.angerTarget != null) {
            tag.putUUID("AngerTarget", this.angerTarget);
        }
        if (this.home != null) {
            tag.putInt("HomeX", this.home.getX());
            tag.putInt("HomeY", this.home.getY());
            tag.putInt("HomeZ", this.home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Skin")) {
            this.setSkin(tag.getInt("Skin"));
        }
        this.angry = tag.getBoolean("Angry");
        if (tag.hasUUID("AngerTarget")) {
            this.angerTarget = tag.getUUID("AngerTarget");
        }
        if (tag.contains("HomeX")) {
            this.home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            this.homeSet = true;
            this.restrictTo(this.home, HOME_RADIUS);
        }
    }

    // ------------------------------------------------------------------ Comportamiento

    /** Enfurece a este sobreviviente contra un jugador. */
    public void enrage(Player player) {
        this.angry = true;
        this.angerTarget = player.getUUID();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && source.getEntity() instanceof Player player
                && !player.isCreative() && !player.isSpectator()) {
            this.enrage(player);
            // los demás sobrevivientes cercanos también se ponen en su contra
            AABB box = this.getBoundingBox().inflate(24.0);
            for (SobrevivienteEntity other : this.level().getEntitiesOfClass(SobrevivienteEntity.class, box)) {
                other.enrage(player);
            }
        }
        return result;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.entityData.get(DATA_SKIN) < 0) {
            this.setSkin(this.random.nextInt(SKINS));
        }
        if (!this.homeSet) {
            this.homeSet = true;
            this.home = this.blockPosition();
            this.restrictTo(this.home, HOME_RADIUS);
        }
        // Vigilancia del jugador: mientras lo ven se quedan tranquilos; si lo pierden de vista, lo cazan
        if (!this.angry && this.tickCount % 5 == 0) {
            Player near = this.level().getNearestPlayer(this, 24.0);
            if (near != null && !near.isCreative() && !near.isSpectator() && this.hasLineOfSight(near)) {
                this.seenPlayer = near.getUUID();
                this.unseenTicks = 0;
            } else if (this.seenPlayer != null) {
                Player seen = this.level().getPlayerByUUID(this.seenPlayer);
                if (seen == null || !seen.isAlive() || this.distanceToSqr(seen) > 48.0 * 48.0) {
                    this.seenPlayer = null;
                    this.unseenTicks = 0;
                } else if (!this.hasLineOfSight(seen)) {
                    this.unseenTicks += 5;
                    if (this.unseenTicks >= LOSE_SIGHT_TICKS) {
                        this.enrage(seen);
                    }
                } else {
                    this.unseenTicks = 0;
                }
            }
        }
        this.setAggressive(this.getTarget() != null && this.getTarget().isAlive());
    }

    /** Objetivo prioritario: el jugador contra el que están furiosos. */
    private class AngerGoal extends Goal {
        private Player target;

        AngerGoal() {
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        private Player find() {
            if (!SobrevivienteEntity.this.angry || SobrevivienteEntity.this.angerTarget == null) {
                return null;
            }
            Player p = SobrevivienteEntity.this.level().getPlayerByUUID(SobrevivienteEntity.this.angerTarget);
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator()
                    || SobrevivienteEntity.this.distanceToSqr(p) > 48.0 * 48.0) {
                return null;
            }
            return p;
        }

        @Override
        public boolean canUse() {
            this.target = this.find();
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            this.target = this.find();
            return this.target != null;
        }

        @Override
        public void start() {
            SobrevivienteEntity.this.setTarget(this.target);
        }

        @Override
        public void stop() {
            if (SobrevivienteEntity.this.getTarget() == this.target) {
                SobrevivienteEntity.this.setTarget(null);
            }
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
