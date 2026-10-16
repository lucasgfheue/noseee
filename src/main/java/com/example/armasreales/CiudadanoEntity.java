package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Habitante de la ciudad. Un solo tipo de entidad; el rol es oculto (no se ve en la skin ni en el nombre):
 * <ul>
 *   <li>Civil: inofensivo. Huye si le apuntas con un arma o le disparas. Al caer la noche desaparece y vuelve de día.</li>
 *   <li>Armado: lleva un arma. De día sólo ataca si le apuntas o le disparas; de noche intenta matarte.</li>
 *   <li>Ladrón: sin arma. De noche intenta robarte; si te roba un arma, la usa para matarte.</li>
 * </ul>
 * Todos pueden abrir puertas.
 */
public class CiudadanoEntity extends PathfinderMob {
    public static final int TOTAL_SKINS = 20;
    public static final int THIEF_SKINS = 4;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(CiudadanoEntity.class, EntityDataSerializers.INT);

    /** Ticks que le quedan de estar "provocado" (si le apuntaste o le dispararon, de día también ataca). */
    int provoked;
    /** Ticks que le quedan huyendo tras robar. */
    int fleeTicks;
    Vec3 fleeFrom;
    int stealCooldown;
    private final net.minecraft.world.SimpleContainer loot = new net.minecraft.world.SimpleContainer(4);

    public CiudadanoEntity(EntityType<? extends CiudadanoEntity> type, Level level) {
        super(type, level);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 80.0);
    }

    // ------------------------------------------------------------------ Rol

    /** Rol oculto: 0 civil, 1 armado, 2 ladrón. Nada en el aspecto ni el nombre lo delata. */
    int role = -1;

    public boolean isCivil() {
        return this.role <= 0;
    }

    public boolean isArmedType() {
        return this.role == 1;
    }

    public boolean isThief() {
        return this.role == 2;
    }

    /** Tipo de entidad "Ladrón" (con huevo, nombre y skins propios). */
    public boolean isThiefType() {
        return this.getType() == ModEntities.LADRON.get();
    }

    public void setRole(int role) {
        this.role = role;
    }

    /** Armado apostado dentro de un edificio: sólo te ataca si te acercas (emboscada sorpresa). */
    boolean ambush;

    /** El civil ya entregó su "tributo" de lingotes de oro. */
    boolean paid;

    /** Al ser golpeado o apuntado, el civil suelta de 1 a 3 lingotes de oro (una sola vez). */
    void payTribute(Player player) {
        if (!this.isCivil() || this.paid || this.level().isClientSide) {
            return;
        }
        this.paid = true;
        int n = 1 + this.random.nextInt(3);
        net.minecraft.world.entity.item.ItemEntity drop = this.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, n));
        if (drop != null) {
            Vec3 d = player.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.2);
            drop.setDeltaMovement(d.x, 0.25, d.z);
        }
        this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    /** Arma guardada (en la cintura) mientras no hay motivo para sacarla. */
    ItemStack holster = ItemStack.EMPTY;

    /** ¿Lleva un arma, en la mano o guardada? */
    public boolean carriesGun() {
        return this.hasGun() || this.holster.getItem() instanceof Gun;
    }

    /** ¿Lleva un arma del mod en la mano? */
    public boolean hasGun() {
        return this.getMainHandItem().getItem() instanceof Gun;
    }

    /** Hostil de noche: armados y ladrones. */
    public boolean isHostileKind() {
        return !this.isCivil();
    }

    public static boolean isNight(Level level) {
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    /** ¿Debe atacar a los jugadores ahora? (de noche, o provocado, o ladrón ya armado) */
    public boolean wantsToFight() {
        if (this.isCivil()) {
            return false;
        }
        if (this.provoked > 0 && this.carriesGun()) {
            return true;
        }
        return isNight(this.level()) && (this.carriesGun() || this.isThief());
    }

    // ------------------------------------------------------------------ Objetivos

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.6) {
            @Override
            protected boolean shouldPanic() {
                return CiudadanoEntity.this.isCivil()
                        && (CiudadanoEntity.this.getLastHurtByMob() != null || CiudadanoEntity.this.isOnFire());
            }
        });
        this.goalSelector.addGoal(2, new CiudadanoGoals.FleeAimGoal(this));
        this.goalSelector.addGoal(2, new CiudadanoGoals.FleeAfterTheftGoal(this));
        this.goalSelector.addGoal(2, new CiudadanoGoals.ClimbLadderGoal(this));
        this.goalSelector.addGoal(2, new CiudadanoGoals.BreakGlassGoal(this));
        this.goalSelector.addGoal(3, new CiudadanoGoals.ShootGoal(this));
        this.goalSelector.addGoal(3, new CiudadanoGoals.StealGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.1, false) {
            @Override
            public boolean canUse() {
                return CiudadanoEntity.this.wantsToFight() && !CiudadanoEntity.this.carriesGun() && super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new CiudadanoGoals.NightSeekGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.3));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Alguien (un jugador) le apuntó o le disparó: los armados contraatacan. */
    void provoke(LivingEntity attacker) {
        if (this.isCivil() || attacker.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        if (attacker instanceof Player p && p.isSpectator()) {
            return;
        }
        if (this.carriesGun()) {
            this.ambush = false;
            this.provoked = 600;
            this.setTarget(attacker);
            this.drawGun();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && source.getEntity() instanceof Player player) {
            this.payTribute(player);
            if (this.carriesGun()) {
                this.provoke(player);
            }
            // Los demás ciudadanos cercanos reaccionan: los armados contraatacan, los civiles huyen
            List<CiudadanoEntity> near = this.level().getEntitiesOfClass(CiudadanoEntity.class,
                    this.getBoundingBox().inflate(18.0), e -> e != this && e.isAlive());
            for (CiudadanoEntity c : near) {
                if (c.isCivil()) {
                    c.setLastHurtByMob(player);
                } else if (c.carriesGun()) {
                    c.provoke(player);
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.provoked > 0) {
            this.provoked--;
        }
        if (this.stealCooldown > 0) {
            this.stealCooldown--;
        }
        // Los civiles desaparecen al anochecer
        if (this.isCivil() && isNight(level) && this.random.nextInt(240) == 0) {
            this.vanish(level);
            return;
        }
        // Sacar el arma al ser provocado o de noche; guardarla cuando se calma
        if (this.isArmedType()) {
            if (this.wantsToFight()) {
                this.drawGun();
            } else if (!this.wantsToFight() && this.hasGun() && this.getTarget() == null && this.holster.isEmpty()
                    && this.tickCount % 20 == 0) {
                this.holster = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
        // Un civil al que apuntan con un arma entrega oro
        if (this.isCivil() && !this.paid && this.tickCount % 5 == 0) {
            for (Player p : level.players()) {
                if (p.distanceToSqr(this) < 28.0 * 28.0 && CiudadanoGoals.aimedAt(p, this)) {
                    this.payTribute(p);
                    break;
                }
            }
        }
        // Los ladrones solo existen de noche: al amanecer se esfuman (soltando lo robado si lo llevan)
        if (this.isThief() && !isNight(level) && this.random.nextInt(60) == 0) {
            this.dropLoot();
            this.vanish(level);
            return;
        }
        // Un jugador que apunta con un arma a un armado lo provoca (y a los armados de alrededor)
        if (this.tickCount % 5 == 0 && !this.isCivil() && this.carriesGun() && this.provoked <= 0) {
            for (Player p : level.players()) {
                if (p.distanceToSqr(this) < 28.0 * 28.0 && CiudadanoGoals.aimedAt(p, this)) {
                    this.provoke(p);
                    for (CiudadanoEntity c : level.getEntitiesOfClass(CiudadanoEntity.class,
                            this.getBoundingBox().inflate(16.0), e -> e != this && e.isAlive() && e.carriesGun())) {
                        c.provoke(p);
                    }
                    break;
                }
            }
        }
        // Hostiles (armados / ladrones) de noche: buscan al jugador más cercano, también a través de cristales.
        // Se asigna el objetivo a mano para que también funcione con jugadores en creativo.
        if (this.tickCount % 10 == 0 && this.isHostileKind() && isNight(level) && this.getTarget() == null
                && this.fleeTicks <= 0 && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            // Los armados detectan al jugador desde muy lejos y a través de las paredes; el ladrón, sólo si lo ve.
            boolean gunner = this.carriesGun();
            double reach = gunner ? (this.ambush ? 11.0 : 110.0) : 40.0;
            Player best = null;
            double bd = reach * reach;
            for (Player p : level.players()) {
                double d = p.distanceToSqr(this);
                if (!p.isSpectator() && p.isAlive() && d < bd && (gunner || CiudadanoGoals.canSee(this, p))) {
                    best = p;
                    bd = d;
                }
            }
            if (best != null) {
                this.ambush = false;
                this.setTarget(best);
            }
        }
        if (this.getTarget() != null && this.provoked <= 0 && this.distanceToSqr(this.getTarget()) > (this.carriesGun() ? 130.0 * 130.0 : 56.0 * 56.0)) {
            this.setTarget(null);
        }
        // Armados sin motivo de día: se calman
        if (!this.isCivil() && !isNight(level) && this.provoked <= 0 && this.getTarget() != null
                && !(this.isThief() && this.hasGun())) {
            this.setTarget(null);
        }
        // Ladrón que huyó con un botín (sin arma): desaparece al terminar la huida
        if (this.isThief() && !this.hasGun() && this.fleeTicks == 0 && !this.loot.isEmpty() && this.tickCount % 20 == 0
                && this.fleeFrom != null) {
            this.vanish(level);
            return;
        }
        if (this.fleeTicks > 0) {
            this.fleeTicks--;
        }
        // Al amanecer, los ladrones sin arma y sin botín vuelven a ser gente normal (dejan de perseguir)
        if (this.getTarget() instanceof Player p && (p.isSpectator() || !p.isAlive())) {
            this.setTarget(null);
        }
        this.setAggressive(this.getTarget() != null && this.hasGun());
    }

    void dropLoot() {
        for (int i = 0; i < this.loot.getContainerSize(); i++) {
            ItemStack st = this.loot.getItem(i);
            if (!st.isEmpty()) {
                this.spawnAtLocation(st.copy());
            }
        }
        this.loot.clearContent();
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Saca el arma de la cintura (si la lleva guardada). */
    void drawGun() {
        if (!this.hasGun() && this.holster.getItem() instanceof Gun) {
            this.setItemSlot(EquipmentSlot.MAINHAND, this.holster);
            this.holster = ItemStack.EMPTY;
            this.setDropChance(EquipmentSlot.MAINHAND, 0.5f);
            this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 0.8f);
        }
    }

    void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        for (int i = 0; i < this.loot.getContainerSize(); i++) {
            // el botín se lo lleva consigo
            this.loot.setItem(i, ItemStack.EMPTY);
        }
        this.discard();
    }

    // ------------------------------------------------------------------ Botín

    /** Guarda lo robado; si es un arma, la empuña. */
    void takeLoot(ItemStack stack) {
        ItemStack rest = this.loot.addItem(stack.copy());
        if (stack.getItem() instanceof Gun && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            this.setDropChance(EquipmentSlot.MAINHAND, 1.0f);
        }
        if (!rest.isEmpty()) {
            this.spawnAtLocation(rest);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            if (!this.holster.isEmpty() && this.random.nextFloat() < 0.5f) {
                this.spawnAtLocation(this.holster.copy());
            }
            this.holster = ItemStack.EMPTY;
            for (int i = 0; i < this.loot.getContainerSize(); i++) {
                ItemStack st = this.loot.getItem(i);
                if (!st.isEmpty() && !(st.getItem() instanceof Gun && ItemStack.isSameItemSameComponents(st, this.getMainHandItem()))) {
                    this.spawnAtLocation(st.copy());
                }
            }
            this.loot.clearContent();
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ Apariencia y datos

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
    }

    public int getSkin() {
        return Math.floorMod(this.entityData.get(DATA_SKIN), this.isThiefType() ? THIEF_SKINS : TOTAL_SKINS);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, skin);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", this.getSkin());
        tag.putInt("Provoked", this.provoked);
        tag.putInt("Role", Math.max(0, this.role));
        tag.putBoolean("Paid", this.paid);
        tag.putBoolean("Ambush", this.ambush);
        if (!this.holster.isEmpty()) {
            tag.put("Holster", this.holster.save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkin(tag.getInt("Skin"));
        this.provoked = tag.getInt("Provoked");
        this.role = tag.contains("Role") ? tag.getInt("Role") : 0;
        this.paid = tag.getBoolean("Paid");
        this.ambush = tag.getBoolean("Ambush");
        this.holster = tag.contains("Holster")
                ? ItemStack.parse(this.registryAccess(), tag.getCompound("Holster")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (this.isThiefType()) {
            this.role = 2;
        } else if (this.role < 0) {                           // huevo o comando: rol al azar (civil o armado)
            boolean night = level.getLevel().getDayTime() % 24000L >= 13000L && level.getLevel().getDayTime() % 24000L < 23000L;
            int r = level.getRandom().nextInt(10);
            this.role = night ? (r < 3 ? 0 : 1) : (r < 7 ? 0 : 1);
        }
        // civiles y armados comparten las 20 skins (el rol no se ve); los ladrones tienen las suyas
        this.setSkin(level.getRandom().nextInt(this.isThiefType() ? THIEF_SKINS : TOTAL_SKINS));
        if (this.isArmedType()) {
            Item gun = switch (level.getRandom().nextInt(3)) {
                case 0 -> ModItems.PISTOLA.get();
                case 1 -> ModItems.AK47.get();
                default -> ModItems.ESCOPETA.get();
            };
            this.holster = new ItemStack(gun);                // guardada hasta que haga falta
        }
        return result;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
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
