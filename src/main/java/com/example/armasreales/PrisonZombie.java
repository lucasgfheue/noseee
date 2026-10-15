package com.example.armasreales;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Zombis de la prisión. Hay tres tipos (cada uno es su propia entidad):
 * caminante (zombi corriente que no se quema), prisionero (mono naranja, sin armas), policía (pistola) y SWAT (armadura, escudo, 40 % menos de daño,
 * siempre con pistola o fusil de asalto). Disparan con sus armas gracias a ZombieShootGoal.
 */
public class PrisonZombie extends ArmedZombie {
    public static final int PRISONER = 0;
    public static final int POLICE = 1;
    public static final int SWAT = 2;
    public static final int WALKER = 3;

    private final int role;

    public PrisonZombie(EntityType<? extends PrisonZombie> type, Level level, int role) {
        super(type, level);
        this.role = role;
        this.setPersistenceRequired();
    }

    public int getRole() {
        return this.role;
    }

    public static AttributeSupplier.Builder createAttributes(int role) {
        AttributeSupplier.Builder b = Zombie.createAttributes();
        switch (role) {
            case POLICE -> b.add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ARMOR, 4.0);
            case SWAT -> b.add(Attributes.MAX_HEALTH, 40.0).add(Attributes.ARMOR, 0.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
            default -> b.add(Attributes.MAX_HEALTH, 20.0);
        }
        return b;
    }

    /** Viven en la prisión: la luz del sol no los quema. */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    /** Equipo según el tipo: nada para los prisioneros; los policías y SWAT siempre llevan arma. */
    @Override
    protected void armUp(RandomSource random) {
        this.equip(random);
    }

    private void equip(RandomSource random) {
        if (this.role == PRISONER) {
            return;
        }
        if (this.role == SWAT) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(
                    random.nextFloat() < 0.6f ? ModItems.AK47.get() : ModItems.PISTOLA.get()));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PISTOLA.get()));
        }
        this.setDropChance(EquipmentSlot.MAINHAND, 0.1f);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
    }

    /** Garantiza el arma aunque aparezca desde una estructura o por otro medio sin pasar por finalizeSpawn. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.role != PRISONER) {
            ItemStack main = this.getMainHandItem();
            if (!main.is(ModItems.PISTOLA.get()) && !main.is(ModItems.AK47.get())) {
                this.equip(this.random);
            }
            if (this.role == SWAT && !this.getOffhandItem().is(Items.SHIELD)) {
                this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        }
    }

    /** El escudo y el equipo antidisturbios del SWAT reducen un 40 % el daño recibido. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, this.role == SWAT ? amount * 0.6f : amount);
    }
}
