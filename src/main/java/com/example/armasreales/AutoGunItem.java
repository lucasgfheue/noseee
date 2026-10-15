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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Arma automática genérica: mantén el clic izquierdo para disparar en ráfaga continua. */
public class AutoGunItem extends Item implements Gun {
    public enum Fx { NONE, IGNITE }

    private final java.util.function.Supplier<Item> ammoItem;
    private final float damage;
    private final int ticksBetweenShots;
    private final double hipSpread;
    private final double aimSpread;
    private final double maxRecoilSpread;
    private final float zoom;
    private final Fx fx;
    private final float pitch;
    private static final double RANGE = 90.0;

    public AutoGunItem(Properties properties, java.util.function.Supplier<Item> ammo, float damage, int ticksBetweenShots,
                       double hipSpread, double aimSpread, double maxRecoilSpread, float zoom, Fx fx, float pitch) {
        super(properties);
        this.ammoItem = ammo;
        this.damage = damage;
        this.ticksBetweenShots = ticksBetweenShots;
        this.hipSpread = hipSpread;
        this.aimSpread = aimSpread;
        this.maxRecoilSpread = maxRecoilSpread;
        this.zoom = zoom;
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
        return this.zoom;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (AmmoUtil.find(player, this.ammoItem.get()).isEmpty() && !player.getAbilities().instabuild) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.8f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }
        return true;   // disparo continuo: los tiros salen en onFireHeld
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (heldTicks % this.ticksBetweenShots != 0) {
            return true;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, this.ammoItem.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.8f);
            return false;
        }
        double spread = player.isUsingItem() ? this.aimSpread : this.hipSpread;
        spread += Math.min(this.maxRecoilSpread, heldTicks * 0.0015);
        shoot(player.serverLevel(), player, spread);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            ammo.shrink(1);
            if ((heldTicks / this.ticksBetweenShots) % 6 == 0) {
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
            target.hurt(level.damageSources().playerAttack(player), this.damage);
            if (this.fx == Fx.IGNITE) {
                target.setRemainingFireTicks(80);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.6f, this.pitch);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.25f, 2.0f);
        Vec3 muzzle = eye.add(look.scale(1.0));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
        for (double d = 2.5; d < hit.distance(); d += 2.5) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
