package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(5, new InteractGoal(this, Player.class, 3.0f, 1.0f));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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
        if (this.isAlive() && !this.isTrading() && !this.isBaby()) {
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
        // Armas -> hierro y diamantes
        add(ModItems.PISTOLA.get(), 1, Items.IRON_INGOT, 4, 6);
        add(ModItems.AK47.get(), 1, Items.DIAMOND, 2, 4);
        add(ModItems.ESCOPETA.get(), 1, Items.DIAMOND, 1, 4);
        add(ModItems.ESCOPETA.get(), 1, Items.IRON_INGOT, 8, 6);
        // Comida -> hierro
        add(Items.BREAD, 6, Items.IRON_INGOT, 1, 16);
        add(Items.APPLE, 8, Items.IRON_INGOT, 1, 16);
        add(Items.CARROT, 10, Items.IRON_INGOT, 1, 16);
        add(Items.POTATO, 10, Items.IRON_INGOT, 1, 16);
        // Comida -> diamantes
        add(Items.BREAD, 24, Items.DIAMOND, 1, 8);
        add(Items.APPLE, 28, Items.DIAMOND, 1, 8);
        add(Items.CARROT, 32, Items.DIAMOND, 1, 8);
        add(Items.POTATO, 32, Items.DIAMOND, 1, 8);
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

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}
