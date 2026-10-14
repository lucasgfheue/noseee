package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
    private static final int HOME_RADIUS = 14;

    private boolean homeSet;
    private BlockPos home;
    private boolean stocked;
    private int eatCooldown;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(CampistaEntity.class, EntityDataSerializers.INT);

    public CampistaEntity(EntityType<? extends CampistaEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Mirar al cliente sólo si no están peleando (así pueden seguir disparando mientras se comercia)
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this) {
            @Override
            public boolean canUse() {
                return CampistaEntity.this.getTarget() == null && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new CampistaShootGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(3, new GoToFoodGoal());
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(5, new InteractGoal(this, Player.class, 3.0f, 1.0f));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Neutrales: si golpean a uno, todos los campistas cercanos atacan a ese jugador
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        // Atacan a los monstruos hostiles (menos los creepers)
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Mob>(this, Mob.class, 5, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper)));
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
        tag.putBoolean("Stocked", this.stocked);
        if (this.home != null) {
            tag.putInt("HomeX", this.home.getX());
            tag.putInt("HomeY", this.home.getY());
            tag.putInt("HomeZ", this.home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkin(tag.getInt("Skin"));
        this.stocked = tag.getBoolean("Stocked");
        if (tag.contains("HomeX")) {
            this.home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            this.homeSet = true;
            this.restrictTo(this.home, HOME_RADIUS);
        }
    }

    // ------------------------------------------------------------------ Comercio

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && (held.is(Items.BREAD) || held.is(Items.APPLE) || held.is(Items.CARROT)
                || held.is(Items.POTATO))) {
            // Agachado + clic derecho con comida: se la regalas para su mochila
            if (!this.level().isClientSide) {
                ItemStack gift = held.copyWithCount(1);
                if (this.getInventory().addItem(gift).isEmpty() && !player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ------------------------------------------------------------------ Varios

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // Primer tick: fija el campamento como hogar y llena la mochila
        if (!this.homeSet) {
            this.homeSet = true;
            this.home = this.blockPosition();
            this.restrictTo(this.home, HOME_RADIUS);
        }
        if (!this.stocked) {
            this.stocked = true;
            this.getInventory().addItem(new ItemStack(Items.BREAD, 8));
            this.getInventory().addItem(new ItemStack(Items.APPLE, 6));
            this.getInventory().addItem(new ItemStack(Items.CARROT, 6));
            this.getInventory().addItem(new ItemStack(Items.POTATO, 6));
            this.getInventory().addItem(new ItemStack(ModItems.MUNICION_PISTOLA.get(), 16));
        }
        // Se queda quieto mientras comercian, salvo que estén peleando
        if (this.isTrading() && this.getTarget() == null) {
            this.getNavigation().stop();
        }
        // No se alejan del campamento: sueltan objetivos lejanos y vuelven si se alejan
        if (this.home != null) {
            double d2 = this.distanceToSqr(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5);
            if (this.getTarget() != null && d2 > 28.0 * 28.0) {
                this.setTarget(null);
            }
            if (this.getTarget() == null && !this.isTrading() && d2 > 18.0 * 18.0 && this.tickCount % 40 == 0) {
                this.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.0);
            }
        }
        // Comer para curarse
        if (this.eatCooldown > 0) {
            this.eatCooldown--;
        } else if (this.getHealth() <= this.getMaxHealth() * 0.5f && this.tickCount % 10 == 0) {
            this.tryEat(level);
        }
    }

    /** Alimento comestible sin efectos raros (no sirven la carne podrida, el pez globo, etc.). */
    private static FoodProperties edible(ItemStack stack) {
        FoodProperties fp = stack.get(DataComponents.FOOD);
        return fp != null && fp.effects().isEmpty() ? fp : null;
    }

    /** Come el alimento de la mochila que más cura y se cura. */
    private void tryEat(ServerLevel level) {
        int best = -1;
        int bestNutrition = 0;
        for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
            FoodProperties fp = edible(this.getInventory().getItem(i));
            if (fp != null && fp.nutrition() > bestNutrition) {
                best = i;
                bestNutrition = fp.nutrition();
            }
        }
        if (best < 0) {
            return;
        }
        ItemStack st = this.getInventory().getItem(best);
        ItemStack shown = st.copyWithCount(1);
        st.shrink(1);
        this.getInventory().setItem(best, st.isEmpty() ? ItemStack.EMPTY : st);
        this.heal(Math.max(2.0f, bestNutrition));
        this.eatCooldown = 40;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown),
                this.getX(), this.getEyeY() - 0.2, this.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
    }

    // ------------------------------------------------------------------ Recoger comida del suelo

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return edible(stack) != null && this.getInventory().canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        InventoryCarrier.pickUpItem(this, this, itemEntity);
    }

    /** Va a buscar la comida que le tiran. */
    private class GoToFoodGoal extends Goal {
        private ItemEntity food;

        GoToFoodGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (CampistaEntity.this.getTarget() != null || CampistaEntity.this.isTrading()
                    || CampistaEntity.this.tickCount % 10 != 0) {
                return false;
            }
            List<ItemEntity> items = CampistaEntity.this.level().getEntitiesOfClass(ItemEntity.class,
                    CampistaEntity.this.getBoundingBox().inflate(12.0, 4.0, 12.0),
                    e -> e.isAlive() && CampistaEntity.this.wantsToPickUp(e.getItem()));
            this.food = items.stream().min(Comparator.comparingDouble(CampistaEntity.this::distanceToSqr)).orElse(null);
            return this.food != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.food != null && this.food.isAlive() && CampistaEntity.this.getTarget() == null
                    && !CampistaEntity.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            CampistaEntity.this.getNavigation().moveTo(this.food, 1.1);
        }

        @Override
        public void stop() {
            this.food = null;
        }
    }

    /** Al comerciar: lo que te da sale de su mochila y lo que le pagas entra en ella. */
    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        int left = result.getCount();
        for (int i = 0; i < this.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack st = this.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(st, result)) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
                this.getInventory().setItem(i, st.isEmpty() ? ItemStack.EMPTY : st);
            }
        }
        ItemStack rest = this.getInventory().addItem(offer.getCostA());
        if (!rest.isEmpty()) {
            this.spawnAtLocation(rest);
        }
        super_reward(offer);
    }

    private void super_reward(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int xp = 3 + this.random.nextInt(4);
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), xp));
        }
    }

    /** Al morir suelta todo lo que lleva en la mochila. */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack st = this.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    this.spawnAtLocation(st.copy());
                }
            }
            this.getInventory().clearContent();
        }
        super.die(source);
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
