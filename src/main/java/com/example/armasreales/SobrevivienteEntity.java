package com.example.armasreales;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Sobreviviente prisionero: pelea contra los mobs hostiles y es neutral con el jugador. De noche se le acerca,
 * le roba cosas del inventario (con un aviso) y huye; las suelta al morir. Si el jugador duerme, va a su cama
 * y lo mata. Si el jugador lo golpea, él y los demás sobrevivientes cercanos se enfurecen.
 */
public class SobrevivienteEntity extends PathfinderMob {
    public static final int SKINS = 5;
    private static final int HOME_RADIUS = 24;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(SobrevivienteEntity.class, EntityDataSerializers.INT);

    private boolean angry;
    private UUID angerTarget;
    private boolean homeSet;
    private BlockPos home;
    private final List<ItemStack> stash = new ArrayList<>();

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
        this.goalSelector.addGoal(1, new SleeperKillGoal());
        // con el botín robado huye del jugador
        this.goalSelector.addGoal(1, new AvoidEntityGoal<Player>(this, Player.class, 24.0f, 1.2, 1.5,
                p -> !this.stash.isEmpty() && !this.angry));
        this.goalSelector.addGoal(2, new ThiefGoal());
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

    public boolean isAngry() {
        return this.angry;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", this.entityData.get(DATA_SKIN));
        tag.putBoolean("Angry", this.angry);
        if (this.angerTarget != null) {
            tag.putUUID("AngerTarget", this.angerTarget);
        }
        ListTag list = new ListTag();
        for (ItemStack s : this.stash) {
            list.add(s.save(this.registryAccess()));
        }
        tag.put("Stash", list);
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
        ListTag list = tag.getList("Stash", 10);
        this.stash.clear();
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(this.registryAccess(), list.get(i)).ifPresent(this.stash::add);
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
            AABB box = this.getBoundingBox().inflate(24.0);
            for (SobrevivienteEntity other : this.level().getEntitiesOfClass(SobrevivienteEntity.class, box)) {
                other.enrage(player);
            }
        }
        return result;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack s : this.stash) {
            this.spawnAtLocation(s);
        }
        this.stash.clear();
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
        this.setAggressive(this.getTarget() != null && this.getTarget().isAlive());
    }

    // ------------------------------------------------------------------ Robo nocturno

    private boolean isNightNow() {
        long t = this.level().getDayTime() % 24000L;
        return t >= 13000L && t <= 23000L;
    }

    private Player nearestVictim(double range) {
        Player best = null;
        double bd = range * range;
        for (Player p : this.level().players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
                double d = this.distanceToSqr(p);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /** De noche, sin objetivo y sin botín: se acerca al jugador y le roba. */
    private class ThiefGoal extends Goal {
        private Player victim;

        ThiefGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            SobrevivienteEntity m = SobrevivienteEntity.this;
            if (!m.stash.isEmpty() || m.angry || m.getTarget() != null || !m.isNightNow() || m.tickCount % 10 != 0) {
                return false;
            }
            Player p = m.nearestVictim(28.0);
            if (p == null || p.isSleeping()) {
                return false;
            }
            this.victim = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            SobrevivienteEntity m = SobrevivienteEntity.this;
            return m.stash.isEmpty() && !m.angry && m.getTarget() == null && m.isNightNow() && this.victim != null
                    && this.victim.isAlive() && !this.victim.isCreative() && !this.victim.isSleeping()
                    && m.distanceToSqr(this.victim) < 40.0 * 40.0;
        }

        @Override
        public void start() {
            SobrevivienteEntity.this.clearRestriction();
        }

        @Override
        public void tick() {
            SobrevivienteEntity m = SobrevivienteEntity.this;
            m.getLookControl().setLookAt(this.victim, 30.0f, 30.0f);
            if (m.distanceToSqr(this.victim) > 2.2 * 2.2) {
                m.getNavigation().moveTo(this.victim, 1.05);
            } else {
                m.steal(this.victim);
            }
        }

        @Override
        public void stop() {
            this.victim = null;
        }
    }

    /** Roba hasta 12 pilas del inventario y avisa al jugador. */
    private void steal(Player player) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                slots.add(i);
            }
        }
        java.util.Collections.shuffle(slots, new java.util.Random(this.random.nextLong()));
        int take = Math.min(slots.size(), 12);
        for (int k = 0; k < take; k++) {
            ItemStack taken = player.getInventory().removeItemNoUpdate(slots.get(k));
            if (!taken.isEmpty()) {
                this.stash.add(taken);
            }
        }
        if (this.stash.isEmpty()) {
            return;                                   // no llevaba nada: no hay robo
        }
        this.clearRestriction();
        player.displayClientMessage(Component.literal("\u00a1ALERTA! Un sobreviviente te ha robado cosas de tu inventario y est\u00e1 huyendo. M\u00e1talo para recuperarlas.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.literal("\u00a1Te han robado!")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        this.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.6f);
    }

    /** Si hay un jugador durmiendo, va hasta su cama y lo mata. */
    private class SleeperKillGoal extends Goal {
        private Player sleeper;

        SleeperKillGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            SobrevivienteEntity m = SobrevivienteEntity.this;
            if (m.tickCount % 10 != 0) {
                return false;
            }
            for (Player p : m.level().players()) {
                if (p.isAlive() && p.isSleeping() && !p.isCreative() && m.distanceToSqr(p) < 48.0 * 48.0) {
                    this.sleeper = p;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.sleeper != null && this.sleeper.isAlive() && this.sleeper.isSleeping();
        }

        @Override
        public void start() {
            SobrevivienteEntity.this.clearRestriction();
        }

        @Override
        public void tick() {
            SobrevivienteEntity m = SobrevivienteEntity.this;
            m.getLookControl().setLookAt(this.sleeper, 30.0f, 30.0f);
            if (m.distanceToSqr(this.sleeper) > 2.2 * 2.2) {
                m.getNavigation().moveTo(this.sleeper, 1.2);
            } else {
                this.sleeper.hurt(m.damageSources().mobAttack(m), 1000.0f);
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }

        @Override
        public void stop() {
            this.sleeper = null;
        }
    }

    /** Objetivo prioritario: sólo el jugador que los golpeó. */
    private class AngerGoal extends Goal {
        private Player target;

        AngerGoal() {
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        private Player find() {
            SobrevivienteEntity self = SobrevivienteEntity.this;
            if (!self.angry || self.angerTarget == null) {
                return null;
            }
            Player p = self.level().getPlayerByUUID(self.angerTarget);
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator()
                    || self.distanceToSqr(p) > 48.0 * 48.0) {
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
