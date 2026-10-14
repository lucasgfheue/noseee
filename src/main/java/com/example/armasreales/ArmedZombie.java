package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Zombi propio del mod: 6 variantes de aspecto y puede llevar armas (pistola 20 %, escopeta 15 %,
 * AK-47 7 %). Dispara sus armas y arroja cuchillos o bombas que lleve en la mano secundaria.
 */
public class ArmedZombie extends Zombie {
    public static final int VARIANTS = 12;
    public static final float CHANCE_PISTOL = 0.20f;
    public static final float CHANCE_SHOTGUN = 0.15f;
    public static final float CHANCE_AK = 0.07f;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(ArmedZombie.class, EntityDataSerializers.INT);

    public ArmedZombie(EntityType<? extends ArmedZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new ZombieShootGoal(this));
    }

    // ------------------------------------------------------------------ Variante

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    public int getVariant() {
        return Math.floorMod(this.entityData.get(DATA_VARIANT), VARIANTS);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, Math.floorMod(variant, VARIANTS));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(tag.getInt("Variant"));
    }

    /** Sin zombis bebé: el modelo de los pequeños no tiene las variantes. */
    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    // ------------------------------------------------------------------ Aparición y equipo

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        RandomSource random = level.getRandom();
        this.setVariant(random.nextInt(VARIANTS));
        this.armUp(random);
        return result;
    }

    /** Una sola tirada decide el arma principal: AK 7 %, escopeta 15 %, pistola 20 %, nada 58 %. */
    private void armUp(RandomSource random) {
        float roll = random.nextFloat();
        Item gun = null;
        if (roll < CHANCE_AK) {
            gun = ModItems.AK47.get();
        } else if (roll < CHANCE_AK + CHANCE_SHOTGUN) {
            gun = ModItems.ESCOPETA.get();
        } else if (roll < CHANCE_AK + CHANCE_SHOTGUN + CHANCE_PISTOL) {
            gun = ModItems.PISTOLA.get();
        }
        if (gun != null) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(gun));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.15f);
        }
        // Objetos para arrojar en la mano secundaria: bombas o cuchillos
        float t = random.nextFloat();
        if (t < 0.12f) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.BOMBA.get(), 1 + random.nextInt(2)));
        } else if (t < 0.30f) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.CUCHILLO.get(), 1 + random.nextInt(3)));
        }
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
    }
}
