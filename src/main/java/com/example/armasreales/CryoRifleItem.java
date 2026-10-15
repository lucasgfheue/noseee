package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Rifle criogénico: arma de energía automática. Cada disparo daña y congela (ralentiza) al objetivo. */
public class CryoRifleItem extends Item implements Gun {
    private static final float DAMAGE = 5.0f;
    private static final double RANGE = 80.0;
    private static final int TICKS_BETWEEN_SHOTS = 3;      // ~7 disparos por segundo
    private static final double HIP_SPREAD = 0.04;
    private static final double AIM_SPREAD = 0.01;
    private static final double MAX_RECOIL_SPREAD = 0.025;   // la dispersión crece al mantener el gatillo

    public CryoRifleItem(Properties properties) {
        super(properties);
    }

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
        return 0.75f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (AmmoUtil.find(player, ModItems.CELDA_CRIOGENICA.get()).isEmpty() && !player.getAbilities().instabuild) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.8f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }
        return true;   // disparo continuo: los tiros salen en onFireHeld
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (heldTicks % TICKS_BETWEEN_SHOTS != 0) {
            return true;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.CELDA_CRIOGENICA.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.8f);
            return false;
        }
        double spread = player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD;
        spread += Math.min(MAX_RECOIL_SPREAD, heldTicks * 0.0015);
        shoot(player.serverLevel(), player, spread);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            ammo.shrink(1);
            if ((heldTicks / TICKS_BETWEEN_SHOTS) % 6 == 0) {
                gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
        return true;
    }

    @Override
    public void onFireStopped(ServerPlayer player, ItemStack gun, int heldTicks) {
        player.getCooldowns().addCooldown(this, 3);
    }

    private void shoot(ServerLevel level, Player player, double spread) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), spread);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);

        Entity target = hit.entity();
        if (target != null) {
            target.invulnerableTime = 0;   // cada bala debe poder herir aunque vayan en ráfaga
            target.hurt(level.damageSources().playerAttack(player), DAMAGE);
            if (target instanceof LivingEntity living) {            // congelación: ralentiza y enfría
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + 40, living.getTicksFrozen() + 25));
                level.sendParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getY() + living.getBbHeight() * 0.6,
                        living.getZ(), 8, 0.25, 0.3, 0.25, 0.02);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.2f, 1.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.3f, 2.0f);
        Vec3 muzzle = eye.add(look.scale(1.0));
        level.sendParticles(ParticleTypes.SNOWFLAKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 4, 0.04, 0.04, 0.04, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y - 0.1, muzzle.z, 1, 0.01, 0.01, 0.01, 0.0);
        for (double d = 2.0; d < hit.distance(); d += 2.0) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
