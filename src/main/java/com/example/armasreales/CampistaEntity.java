package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.ItemLike;

/**
 * Campista: habitante de los campamentos. Intercambia armas (pistola, AK-47, escopeta) y comida
 * (pan, manzanas, zanahorias y papas) por hierro y diamantes.
 */
public class CampistaEntity extends AbstractVillager {
    public static final int SKINS = 5;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(CampistaEntity.class, EntityDataSerializers.INT);

    public CampistaEntity(EntityType<? extends CampistaEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new CampistaShootGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(5, new InteractGoal(this, Player.class, 3.0f, 1.0f));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Neutrales: si golpean a uno, todos los campistas cercanos atacan a ese jugador
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ------------------------------------------------------------------ Apariencia

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
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
        tag.putInt("Skin", this.getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkin(tag.getInt("Skin"));
    }

    // ------------------------------------------------------------------ Comercio

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isAlive() && !this.isTrading() && !this.isBaby() && this.getTarget() == null) {
            if (!this.level().isClientSide) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private void add(ItemLike cost, int costCount, ItemLike result, int resultCount, int maxUses) {
        this.getOffers().add(new MerchantOffer(new ItemCost(cost, costCount),
                new ItemStack(result, resultCount), maxUses, 4, 0.05f));
    }

    @Override
    protected void updateTrades() {
        // Tú pagas con hierro y diamantes; ellos te dan armas, comida y balas
        add(Items.IRON_INGOT, 4, ModItems.PISTOLA.get(), 1, 6);
        add(Items.DIAMOND, 2, ModItems.AK47.get(), 1, 4);
        add(Items.DIAMOND, 1, ModItems.ESCOPETA.get(), 1, 4);
        add(Items.IRON_INGOT, 8, ModItems.ESCOPETA.get(), 1, 6);
        // Comida
        add(Items.IRON_INGOT, 1, Items.BREAD, 6, 16);
        add(Items.IRON_INGOT, 1, Items.APPLE, 8, 16);
        add(Items.IRON_INGOT, 1, Items.CARROT, 10, 16);
        add(Items.IRON_INGOT, 1, Items.POTATO, 10, 16);
        // Balas
        add(Items.IRON_INGOT, 1, ModItems.MUNICION_PISTOLA.get(), 12, 16);
        add(Items.IRON_INGOT, 1, ModItems.CARTUCHO_ESCOPETA.get(), 8, 16);
        add(Items.IRON_INGOT, 2, ModItems.MUNICION_FUSIL.get(), 16, 16);
        add(Items.DIAMOND, 1, ModItems.MUNICION_FUSIL.get(), 48, 8);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int xp = 3 + this.random.nextInt(4);
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), xp));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ------------------------------------------------------------------ Varios

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // Sin sonidos propios (nada de sonidos de aldeano)
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        // en silencio
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.setSkin(level.getRandom().nextInt(SKINS));
        if (this.getMainHandItem().isEmpty()) {
            Item gun = switch (level.getRandom().nextInt(3)) {
                case 0 -> ModItems.PISTOLA.get();
                case 1 -> ModItems.AK47.get();
                default -> ModItems.ESCOPETA.get();
            };
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(gun));
        }
        return result;
    }
}
