package com.example.armasreales;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Revólver Titán (ficticio): cada disparo normal almacena energía en su tambor-capacitor.
 * Modo especial (agachado + clic izquierdo): descarga TODA la energía acumulada en un único pulso
 * que atraviesa entidades; cuanta más energía, más daño.
 */
public class TitanRevolverItem extends Item implements Gun {
    public static final int MAX_CHARGE = 12;
    private static final float SHOT_DAMAGE = 7.0f;
    private static final double SHOT_RANGE = 100.0;
    private static final double PULSE_RANGE = 120.0;
    private static final int SHOT_COOLDOWN = 14;
    private static final int PULSE_COOLDOWN = 50;

    public TitanRevolverItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ Energía almacenada

    public static int getCharge(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt("Energia");
    }

    public static void setCharge(ItemStack stack, int charge) {
        CompoundTag tag = new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            tag = data.copyTag();
        }
        tag.putInt("Energia", Math.max(0, Math.min(MAX_CHARGE, charge)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** La barra del objeto muestra la energía acumulada (celeste) en lugar del desgaste. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) > 0 || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int c = getCharge(stack);
        return c > 0 ? Math.max(1, Math.round(13.0f * c / MAX_CHARGE)) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return getCharge(stack) > 0 ? 0x4DD8FF : super.getBarColor(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ------------------------------------------------------------------ Uso

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public float aimZoom() {
        return 0.8f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (player.isShiftKeyDown()) {
            pulse(level, player, gun);
        } else {
            shoot(level, player, gun);
        }
        return false;
    }

    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.MUNICION_MAGNUM.get());
        if (ammo.isEmpty() && !creative) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.5f);
            player.getCooldowns().addCooldown(this, 8);
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (!player.isUsingItem()) {
            look = Bullets.spread(level, look, 0.035);
        }
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, SHOT_RANGE);
        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().playerAttack(player), SHOT_DAMAGE);
            if (hit.entity() instanceof LivingEntity living) {
                living.knockback(0.4, -look.x, -look.z);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.6f, 0.75f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.4f, 2.0f);
        Vec3 muzzle = eye.add(look.scale(0.9));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y - 0.1, muzzle.z, 6, 0.05, 0.05, 0.05, 0.05);
        double dist = hit.distance();
        for (double d = 2.0; d < dist; d += 2.0) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }

        int charge = Math.min(MAX_CHARGE, getCharge(gun) + 1);
        setCharge(gun, charge);
        player.displayClientMessage(Component.translatable("message.armasreales.titan_energia", charge, MAX_CHARGE), true);
        if (charge == MAX_CHARGE) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.6f, 1.8f);
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
    }

    /** Descarga toda la energía en un único pulso que atraviesa entidades. */
    private void pulse(ServerLevel level, ServerPlayer player, ItemStack gun) {
        int charge = getCharge(gun);
        if (charge <= 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            player.displayClientMessage(Component.translatable("message.armasreales.titan_vacio"), true);
            player.getCooldowns().addCooldown(this, 10);
            return;
        }
        float damage = 4.0f + charge * 2.5f;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(PULSE_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = new AABB(eye, end).inflate(1.0);
        for (Entity target : level.getEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            if (target.getBoundingBox().inflate(0.35).clip(eye, end).isEmpty()) continue;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), damage);
            ((LivingEntity) target).knockback(0.4 + charge * 0.12, -look.x, -look.z);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(),
                    10 + charge, 0.3, 0.4, 0.3, 0.2);
        }

        BeamEntity.flash(level, player, BeamEntity.STYLE_TITAN, (float) eye.distanceTo(end), 10);
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 20 + charge * 3, 0.5, 0.5, 0.5, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 0.5f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.8f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1.0f, 1.6f);

        setCharge(gun, 0);
        player.displayClientMessage(Component.translatable("message.armasreales.titan_pulso", charge), true);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild) {
            gun.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, PULSE_COOLDOWN);
    }
}
