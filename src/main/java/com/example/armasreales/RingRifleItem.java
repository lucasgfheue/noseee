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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fusil Anillo (ficticio): fusil automático de cargas LAB-10 (distorsión). Precisión 86, cadencia 58.
 * Habilidad "Desvío" (agachado + clic izquierdo): crea durante 5 segundos un escudo celeste
 * translúcido frente al jugador (ver ShieldEntity) que desvía los proyectiles ENERGÉTICOS (disparos eléctricos y del taser). No detiene
 * explosiones ni ataques cuerpo a cuerpo.
 */
public class RingRifleItem extends Item implements Gun {
    private static final double RANGE = 110.0;
    private static final float DAMAGE = 5.5f;
    /** Cadencia 58/100 ≈ un disparo cada 4 ticks (5 disparos por segundo). */
    private static final int TICKS_BETWEEN_SHOTS = 4;
    /** Precisión 86/100: poca dispersión (en cadera / apuntando). */
    private static final double HIP_SPREAD = 0.028;
    private static final double AIM_SPREAD = 0.005;
    private static final double MAX_RECOIL = 0.014;

    private static final int SHIELD_COST = 5;           // cargas LAB-10 que gasta el escudo
    private static final int DEFLECT_COOLDOWN = 200;     // 10 s (el escudo dura 5 s)

    public RingRifleItem(Properties properties) {
        super(properties);
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
        return 0.65f;
    }

    // ------------------------------------------------------------------ Estado de la habilidad

    private static long getLong(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0L : data.copyTag().getLong(key);
    }

    private static void setLong(ItemStack stack, String key, long value) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putLong(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Cuántas Cargas LAB-10 lleva el jugador en total (inventario y mano secundaria). */
    private static int countAmmo(ServerPlayer player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.CARGA_LAB10.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.CARGA_LAB10.get())) total += s.getCount();
        }
        return total;
    }

    private static void consumeAmmo(ServerPlayer player, int amount) {
        int left = amount;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.CARGA_LAB10.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.CARGA_LAB10.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
    }

    private void activateDeflect(ServerLevel level, ServerPlayer player, ItemStack gun) {
        long now = level.getGameTime();
        if (now < getLong(gun, "ReadyAt")) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.8f, 1.8f);
            player.displayClientMessage(Component.translatable("message.armasreales.desvio_recarga"), true);
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && countAmmo(player) < SHIELD_COST) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.8f, 1.4f);
            player.displayClientMessage(Component.translatable("message.armasreales.escudo_sin_carga", SHIELD_COST), true);
            return;
        }
        if (!creative) {
            consumeAmmo(player, SHIELD_COST);
        }
        ShieldEntity.spawn(level, player);
        setLong(gun, "ReadyAt", now + DEFLECT_COOLDOWN);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8f, 2.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable("message.armasreales.desvio_activo", SHIELD_COST), true);
    }

    // ------------------------------------------------------------------ Disparo

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.isShiftKeyDown()) {
            activateDeflect(player.serverLevel(), player, gun);
            return false;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (AmmoUtil.find(player, ModItems.CARGA_LAB10.get()).isEmpty() && !player.getAbilities().instabuild) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.6f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }
        return true;   // disparo continuo: los tiros salen en onFireHeld
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (player.isShiftKeyDown()) {
            return false;
        }
        if (heldTicks % TICKS_BETWEEN_SHOTS != 0) {
            return true;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.CARGA_LAB10.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.6f);
            return false;
        }
        double spread = (player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD)
                + Math.min(MAX_RECOIL, heldTicks * 0.0008);
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
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), DAMAGE);
            if (target instanceof LivingEntity living) {       // la distorsión los frena un instante
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.2f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 0.9f, 1.4f);

        Vec3 muzzle = eye.add(look.scale(1.0));
        level.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y - 0.12, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
        // Pequeños anillos espaciales a lo largo de la trayectoria
        for (double d = 2.0; d < hit.distance(); d += 2.5) {
            Vec3 p = eye.add(look.scale(d));
            drawRing(level, p, look, 0.18 + 0.1 * Math.sin(d));
        }
    }

    /** Dibuja un anillo de partículas perpendicular a la dirección dada. */
    private static void drawRing(ServerLevel level, Vec3 center, Vec3 dir, double radius) {
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u).normalize();
        int n = radius > 0.5 ? 16 : 7;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec3 p = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
