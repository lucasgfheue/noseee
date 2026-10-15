package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Arma cuerpo a cuerpo con un efecto al golpear. */
public class MeleeFxItem extends Item {
    public enum Fx { VOLCANIC, SHADOW }

    private final Fx fx;

    public MeleeFxItem(Properties properties, Fx fx) {
        super(properties);
        this.fx = fx;
    }

    /** damage = daño extra sobre la mano (1); speed = modificador a la velocidad de ataque base (4.0). */
    public static ItemAttributeModifiers attributes(double damage, double speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel level) {
            switch (this.fx) {
                case VOLCANIC -> {
                    target.setRemainingFireTicks(120);
                    double dx = target.getX() - attacker.getX();
                    double dz = target.getZ() - attacker.getZ();
                    target.knockback(0.9, -dx, -dz);
                    level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
                }
                case SHADOW -> {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }
}
