package com.example.armasreales;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «MONOLITO» (ficticio): cañón electromagnético portátil con tres anillos azules. Núcleos MASS-1, capacidad 3.
 * Precisión 86/100.
 *  - Clic izquierdo: disparo electromagnético concentrado (gasta 1 carga).
 *  - Agachado + clic izquierdo, habilidad ONDA LINEAL (gasta 1 carga): concentra la energía en un impacto
 *    que lanza una onda de choque recta; daña, empuja hacia delante y desestabiliza todo lo que cruza el pasillo.
 *  - Cuando se queda sin cargas recarga sola con núcleos MASS-1 del inventario.
 */
public class MonolithCannonItem extends Item implements Gun {
    public static final int MAX_LOADED = 3;
    public static final int RELOAD_TICKS = 100;       // 5 s

    private static final double RANGE = 140.0;
    private static final int SHOT_COOLDOWN = 30;
    private static final int WAVE_COOLDOWN = 55;
    private static final double HIP_SPREAD = 0.024;    // precisión 86
    private static final double AIM_SPREAD = 0.004;
    private static final float SHOT_DAMAGE = 16.0f;
    private static final double WAVE_LENGTH = 40.0;
    private static final double WAVE_WIDTH = 2.4;      // radio del pasillo
    private static final float WAVE_DAMAGE = 14.0f;

    public MonolithCannonItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ Estado en el ítem

    private static int getInt(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(key);
    }

    private static void setInt(ItemStack stack, String key, int value) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        if (tag.getInt(key) == value && tag.contains(key)) {
            return;
        }
        tag.putInt(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getLoaded(ItemStack stack) {
        return getInt(stack, "Cargas");
    }

    private static int getReload(ItemStack stack) {
        return getInt(stack, "Recarga");
    }

    // ------------------------------------------------------------------ Indicador (barra del objeto)

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            return Math.max(1, Math.round(13.0f * reload / RELOAD_TICKS));
        }
        int loaded = getLoaded(stack);
        return loaded > 0 ? Math.round(13.0f * loaded / MAX_LOADED) : 1;
    }

    /** Azul oscuro mientras recarga o está vacío; azul brillante al completar la carga. */
    @Override
    public int getBarColor(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            float t = Mth.clamp(reload / (float) RELOAD_TICKS, 0.0f, 1.0f);
            int g = (int) (50 + 120 * t);
            return (30 << 16) | (g << 8) | 230;
        }
        return getLoaded(stack) > 0 ? 0x50B4FF : 0x1C3C8C;
    }

    /** El estado cambia cada tick: que no se repita la animación de sacar el arma. */
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
        return 0.7f;
    }

    // ------------------------------------------------------------------ Recarga

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        if (!(level instanceof ServerLevel server) || !(holder instanceof ServerPlayer player)) {
            return;
        }
        int reload = getReload(stack);
        if (reload <= 0) {
            return;
        }
        if (!selected) {                       // si guardas el arma, se cancela la recarga
            setInt(stack, "Recarga", 0);
            return;
        }
        reload++;
        if (reload % 22 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND,
                    SoundSource.PLAYERS, 0.5f, 0.6f + reload / (float) RELOAD_TICKS);
        }
        if (reload < RELOAD_TICKS) {
            setInt(stack, "Recarga", reload);
            return;
        }
        // Recarga completa
        int loaded;
        if (player.getAbilities().instabuild) {
            loaded = MAX_LOADED;
        } else {
            loaded = takeCores(player, MAX_LOADED);
        }
        setInt(stack, "Cargas", loaded);
        setInt(stack, "Recarga", 0);
        if (loaded > 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.9f, 1.9f);
            server.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.PLAYERS, 0.8f, 0.7f);
            player.displayClientMessage(Component.translatable("message.armasreales.monolito_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.monolito_sin_nucleos"), true);
        }
    }

    private static int countCores(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.MASS_1.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.MASS_1.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeCores(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.MASS_1.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.MASS_1.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return max - left;
    }

    // ------------------------------------------------------------------ Disparo y habilidad

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (getReload(gun) > 0) {
            player.displayClientMessage(Component.translatable("message.armasreales.monolito_recargando"), true);
            return false;
        }
        if (getLoaded(gun) <= 0) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && countCores(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.monolito_sin_nucleos"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.6f);
            player.displayClientMessage(Component.translatable("message.armasreales.monolito_recargando"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            wave(level, player, gun);
        } else {
            shoot(level, player, gun);
        }
        return false;
    }

    private void spend(ServerPlayer player, ItemStack gun) {
        setInt(gun, "Cargas", Math.max(0, getLoaded(gun) - 1));
        if (!player.getAbilities().instabuild) {
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }

    /** Disparo electromagnético concentrado. */
    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);
        Vec3 end = eye.add(look.scale(hit.distance()));

        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().playerAttack(player), SHOT_DAMAGE);
            if (hit.entity() instanceof LivingEntity le) {
                le.knockback(0.8, -look.x, -look.z);
            }
        }
        BeamEntity.flash(level, player, BeamEntity.STYLE_LASER, (float) eye.distanceTo(end), 8);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 18, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.8f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.8f, 0.6f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        announceRemaining(player, gun);
    }

    /** ONDA LINEAL: impacto concentrado que lanza una onda de choque recta hacia delante. */
    private void wave(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Bullets.Hit probe = Bullets.trace(level, player, eye, look, WAVE_LENGTH);
        double len = Math.max(3.0, probe.distance());
        Vec3 start = eye.add(look.scale(0.8)).add(0.0, -0.2, 0.0);
        AABB box = new AABB(start, start.add(look.scale(len))).inflate(WAVE_WIDTH);
        for (Entity e : level.getEntities(player, box, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(start);
            double along = c.dot(look);
            if (along < 0.0 || along > len) continue;
            double off = c.subtract(look.scale(along)).length();
            if (off > WAVE_WIDTH + e.getBbWidth() * 0.5) continue;
            double f = Math.max(0.35, 1.0 - along / (len * 1.15));          // más fuerte cerca del cañón
            LivingEntity target = (LivingEntity) e;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), (float) (WAVE_DAMAGE * f));
            target.setDeltaMovement(target.getDeltaMovement().add(look.x * (1.0 + f), 0.35, look.z * (1.0 + f)));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
        }

        // Onda visual: anillos azules que avanzan en línea recta, más cerca cada vez
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize();
        Vec3 v = look.cross(u).normalize();
        net.minecraft.core.particles.DustParticleOptions blue =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.3f, 0.65f, 1.0f), 1.2f);
        for (double d = 1.5; d < len; d += 2.5) {
            Vec3 c = start.add(look.scale(d));
            double r = 0.8 + 1.6 * Math.min(1.0, d / 14.0);
            int n = 14;
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                Vec3 p = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
                level.sendParticles(blue, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, look.x, look.y, look.z, 0.5);
                }
            }
        }
        Vec3 end = start.add(look.scale(len));
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.5, 0.5, 0.5, 0.0);
        level.sendParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.5f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.0f, 1.6f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, WAVE_COOLDOWN);
        announceRemaining(player, gun);
    }

    private void announceRemaining(ServerPlayer player, ItemStack gun) {
        player.displayClientMessage(Component.translatable("message.armasreales.monolito_cargas",
                getLoaded(gun), MAX_LOADED), true);
    }
}
