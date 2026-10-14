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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escopeta: un disparo lanza varios perdigones. Cada perdigón hace mucho daño de cerca
 * y casi nada de lejos; los perdigones que aciertan al mismo objetivo se suman en un solo golpe.
 */
public class ShotgunItem extends Item implements Gun {
    private static final int PELLETS = 12;
    private static final double RANGE = 40.0;
    private static final float MAX_PELLET_DAMAGE = 3.5f;   // a quemarropa: hasta 12 x 3.5 = 42
    private static final float MIN_PELLET_DAMAGE = 0.4f;
    private static final double FALLOFF_DISTANCE = 22.0;    // a esta distancia el daño ya es el mínimo
    private static final double HIP_SPREAD = 0.1;
    private static final double AIM_SPREAD = 0.06;
    private static final int COOLDOWN_TICKS = 25;

    public ShotgunItem(Properties properties) {
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
        return 0.85f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.CARTUCHO_ESCOPETA.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }

        shoot(player.serverLevel(), player);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return false;
    }

    private void shoot(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double spread = player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD;

        Map<Entity, Float> damage = new LinkedHashMap<>();
        for (int i = 0; i < PELLETS; i++) {
            Vec3 dir = Bullets.spread(level, look, spread);
            Bullets.Hit hit = Bullets.trace(level, player, eye, dir, RANGE);
            if (hit.entity() != null) {
                float falloff = (float) Math.max(0.0, 1.0 - hit.distance() / FALLOFF_DISTANCE);
                float dmg = MIN_PELLET_DAMAGE + (MAX_PELLET_DAMAGE - MIN_PELLET_DAMAGE) * falloff;
                damage.merge(hit.entity(), dmg, Float::sum);
            }
            // Estela corta de cada perdigón
            for (double d = 3.0; d < Math.min(hit.distance(), 12.0); d += 3.0) {
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        for (Map.Entry<Entity, Float> e : damage.entrySet()) {
            e.getKey().invulnerableTime = 0;
            e.getKey().hurt(level.damageSources().playerAttack(player), e.getValue());
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.7f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2.0f, 0.5f);
        Vec3 muzzle = eye.add(look.scale(1.2));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 8, 0.12, 0.12, 0.12, 0.03);
    }
}
