package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Revólver genérico: un disparo potente por pulsación. Clic derecho apunta, clic izquierdo dispara. */
public class RevolverItem extends Item implements Gun {
    public enum Fx { NONE, WITHER, SLOW, IGNITE }

    private final java.util.function.Supplier<Item> ammoItem;
    private final float damage;
    private final int cooldown;
    private final double range;
    private final double hipSpread;
    private final Fx fx;
    private final float pitch;

    public RevolverItem(Properties properties, java.util.function.Supplier<Item> ammo, float damage, int cooldown,
                        double range, double hipSpread, Fx fx, float pitch) {
        super(properties);
        this.ammoItem = ammo;
        this.damage = damage;
        this.cooldown = cooldown;
        this.range = range;
        this.hipSpread = hipSpread;
        this.fx = fx;
        this.pitch = pitch;
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
        return 0.8f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, this.ammoItem.get());

        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.6f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }

        shoot(player.serverLevel(), player, player.isUsingItem());
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, this.cooldown);
        return false;
    }

    private void shoot(ServerLevel level, Player player, boolean aiming) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (!aiming) {
            look = look.add(level.random.nextGaussian() * this.hipSpread,
                    level.random.nextGaussian() * this.hipSpread,
                    level.random.nextGaussian() * this.hipSpread).normalize();
        }
        Vec3 end = eye.add(look.scale(this.range));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        AABB searchBox = new AABB(eye, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, player, eye, end, searchBox,
                e -> !e.isSpectator() && e.isPickable());

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            end = entityHit.getLocation();
            target.hurt(level.damageSources().playerAttack(player), this.damage);
            if (target instanceof LivingEntity living) {            // el retroceso del impacto empuja al objetivo
                living.knockback(0.5, -look.x, -look.z);
                switch (this.fx) {
                    case WITHER -> living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 60, 0));
                    case SLOW -> living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    case IGNITE -> living.setRemainingFireTicks(80);
                    default -> { }
                }
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.8f, this.pitch);

        Vec3 muzzle = eye.add(look.scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 8, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 4, 0.01, 0.01, 0.01, 0.0);

        double dist = eye.distanceTo(end);
        for (double d = 2.0; d < dist; d += 2.0) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
