package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Arco de Hierro (experimental, ficticio): fusil electromagnético pesado con cápsulas FERRO-9.
 * Precisión 89, cadencia 22 (lenta). Mantén el clic izquierdo para cargar los anillos magnéticos y suéltalo
 * para disparar; el disparo completamente cargado genera una onda electromagnética visual.
 */
public class IronBowItem extends Item implements Gun {
    /** Estado de carga de cada jugador: ticks descontados por interrupciones y pausa tras recibir daño. */
    private static final class Charge {
        int offset;
        int lastHeld;
        long pausedUntil;
        boolean announced;
    }

    private static final java.util.Map<java.util.UUID, Charge> CHARGES = new java.util.HashMap<>();
    /** Fracción de la energía acumulada que se conserva al recibir daño. */
    private static final float KEEP_ON_HIT = 0.4f;
    private static final int PAUSE_ON_HIT = 12;

    private static final double RANGE = 160.0;
    private static final int FULL_CHARGE_TICKS = 20;
    private static final int MIN_CHARGE_TICKS = 3;
    private static final float MIN_DAMAGE = 6.0f;
    private static final float MAX_DAMAGE = 15.0f;
    /** Cadencia 22/100: recarga larga entre disparos. */
    private static final int COOLDOWN = 22;
    /** Precisión 89/100: casi sin dispersión. */
    private static final double HIP_SPREAD = 0.018;
    private static final double AIM_SPREAD = 0.002;

    public IronBowItem(Properties properties) {
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
        return 0.5f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (AmmoUtil.find(player, ModItems.FERRO9.get()).isEmpty() && !player.getAbilities().instabuild) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }
        CHARGES.put(player.getUUID(), new Charge());
        return true;   // empieza a cargar
    }

    /** Llamado cuando el jugador recibe daño mientras carga: se corta la carga y se pierde parte de la energía. */
    public static void interruptCharge(ServerPlayer player) {
        Charge c = CHARGES.get(player.getUUID());
        if (c == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int current = Math.max(0, c.lastHeld - c.offset);
        if (current <= 0) {
            return;
        }
        int kept = (int) (current * KEEP_ON_HIT);
        c.offset += current - kept;
        c.pausedUntil = level.getGameTime() + PAUSE_ON_HIT;
        c.announced = false;
        Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(1.0)).add(0.0, -0.2, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 24, 0.3, 0.3, 0.3, 0.2);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.9f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.4f, 2.0f);
        player.displayClientMessage(Component.translatable("message.armasreales.arco_interrumpido"), true);
    }

    /** Mientras se mantiene el clic: los anillos se cargan (partículas que se cierran hacia la boca). */
    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        ServerLevel level = player.serverLevel();
        Charge c = CHARGES.computeIfAbsent(player.getUUID(), id -> new Charge());
        c.lastHeld = heldTicks;
        if (level.getGameTime() < c.pausedUntil) {          // interrumpida: la carga se detiene un momento
            c.offset++;
            return true;
        }
        int charge = Math.max(0, heldTicks - c.offset);
        float frac = Math.min(1.0f, charge / (float) FULL_CHARGE_TICKS);
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(1.2)).add(0.0, -0.15, 0.0);
        if (charge < FULL_CHARGE_TICKS) {
            c.announced = false;
        }
        if (heldTicks % 2 == 0) {
            ring(level, muzzle.add(look.scale(0.5)), look, 0.9 - 0.65 * frac, ParticleTypes.ELECTRIC_SPARK, 8);
        }
        if (heldTicks % 4 == 0 && charge > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.35f, 0.6f + 1.2f * frac);
        }
        if (charge >= FULL_CHARGE_TICKS && !c.announced) {
            c.announced = true;
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.7f, 1.7f);
            player.displayClientMessage(Component.translatable("message.armasreales.arco_cargado"), true);
        }
        return true;
    }

    /** Al soltar el clic se dispara con la carga acumulada. */
    @Override
    public void onFireStopped(ServerPlayer player, ItemStack gun, int rawHeld) {
        Charge c = CHARGES.remove(player.getUUID());
        int heldTicks = Math.max(0, rawHeld - (c == null ? 0 : c.offset));
        if (heldTicks < MIN_CHARGE_TICKS || player.getCooldowns().isOnCooldown(this)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.FERRO9.get());
        if (ammo.isEmpty() && !creative) {
            return;
        }
        ServerLevel level = player.serverLevel();
        float frac = Math.min(1.0f, heldTicks / (float) FULL_CHARGE_TICKS);
        boolean charged = frac >= 1.0f;
        float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * frac;

        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);

        Entity target = hit.entity();
        if (target != null) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), damage);
            if (target instanceof LivingEntity living) {
                living.knockback(0.3 + 0.7 * frac, -look.x, -look.z);
            }
        }

        // Estela: trazo de chispas; el disparo cargado genera además la onda electromagnética visual
        Vec3 muzzle = eye.add(look.scale(1.2));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y - 0.15, muzzle.z, 10, 0.06, 0.06, 0.06, 0.08);
        for (double d = 1.5; d < hit.distance(); d += 1.0) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y - 0.1, p.z, 1, 0, 0, 0, 0);
        }
        if (charged) {
            int i = 0;
            for (double d = 2.0; d < hit.distance(); d += 3.0, i++) {
                ring(level, eye.add(look.scale(d)), look, 0.35 + 0.12 * i, ParticleTypes.ELECTRIC_SPARK, 18);
                if (i % 2 == 0) {
                    ring(level, eye.add(look.scale(d)), look, 0.2 + 0.08 * i, ParticleTypes.SOUL_FIRE_FLAME, 10);
                }
                if (i > 24) break;
            }
            Vec3 end = eye.add(look.scale(hit.distance()));
            for (int k = 0; k < 4; k++) {
                ring(level, end, look, 0.4 + k * 0.45, ParticleTypes.ELECTRIC_SPARK, 20);
            }
            level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.5f, 1.6f);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2.0f, 0.55f + 0.2f * frac);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.35f, 1.9f);

        player.swing(InteractionHand.MAIN_HAND, true);
        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
    }

    /** Anillo de partículas perpendicular a la dirección dada. */
    private static void ring(ServerLevel level, Vec3 center, Vec3 dir,
                             double radius, net.minecraft.core.particles.ParticleOptions type, int n) {
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u).normalize();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec3 p = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            level.sendParticles(type, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
