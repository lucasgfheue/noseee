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
 * X-32 «OMEGA» (única, ficticia): cañón experimental de energía. Núcleos OMG-3, capacidad 3, recarga 5,5 s.
 * Precisión 82, cadencia 8 (muy lenta).
 *  - Clic izquierdo: disparo de energía concentrada (gasta 1 carga).
 *  - Agachado + clic izquierdo, habilidad COLAPSO (gasta 1 carga): onda expansiva de gran alcance; el daño
 *    baja con la distancia y los objetivos del borde pueden esquivar parte del efecto.
 *  - Cuando se queda sin cargas recarga sola (5,5 s) con núcleos OMG-3 del inventario.
 * El indicador pasa de rojo a blanco al completar la carga (barra del objeto y modelo del arma).
 */
public class OmegaCannonItem extends Item implements Gun {
    public static final int MAX_LOADED = 3;
    public static final int RELOAD_TICKS = 110;       // 5,5 s

    private static final double RANGE = 130.0;
    private static final int SHOT_COOLDOWN = 40;      // cadencia 8: muy lenta
    private static final int COLLAPSE_COOLDOWN = 60;
    private static final double HIP_SPREAD = 0.03;    // precisión 82
    private static final double AIM_SPREAD = 0.006;
    private static final float SHOT_DAMAGE = 20.0f;
    private static final float SPLASH_DAMAGE = 8.0f;
    private static final double SPLASH_RADIUS = 2.5;
    private static final double COLLAPSE_RADIUS = 14.0;
    private static final float COLLAPSE_MAX_DAMAGE = 40.0f;

    public OmegaCannonItem(Properties properties) {
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

    /** Rojo mientras recarga o está vacío; pasa a blanco al completar la carga. */
    @Override
    public int getBarColor(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            float t = Mth.clamp(reload / (float) RELOAD_TICKS, 0.0f, 1.0f);
            int gb = (int) (40 + 215 * t * t);
            return (255 << 16) | (gb << 8) | gb;
        }
        return getLoaded(stack) > 0 ? 0xFFFFFF : 0xE02828;
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
            player.displayClientMessage(Component.translatable("message.armasreales.omega_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.omega_sin_nucleos"), true);
        }
    }

    private static int countCores(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.OMG3.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.OMG3.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeCores(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.OMG3.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.OMG3.get())) {
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
            player.displayClientMessage(Component.translatable("message.armasreales.omega_recargando"), true);
            return false;
        }
        if (getLoaded(gun) <= 0) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && countCores(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.omega_sin_nucleos"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.6f);
            player.displayClientMessage(Component.translatable("message.armasreales.omega_recargando"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            collapse(level, player, gun);
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

    /** Disparo de energía concentrada: golpe fuerte a un objetivo y estallido pequeño alrededor. */
    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);
        Vec3 end = eye.add(look.scale(hit.distance()));

        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().playerAttack(player), SHOT_DAMAGE);
        }
        AABB area = new AABB(end, end).inflate(SPLASH_RADIUS);
        for (Entity e : level.getEntities(player, area,
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x != hit.entity())) {
            if (e.position().distanceTo(end) > SPLASH_RADIUS + 0.5) continue;
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), SPLASH_DAMAGE);
        }

        BeamEntity.flash(level, player, BeamEntity.STYLE_OMEGA, (float) eye.distanceTo(end), 10);
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 14, 0.4, 0.4, 0.4, 0.08);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 20, 0.5, 0.5, 0.5, 0.25);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.9f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 2.0f, 0.5f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        announceRemaining(player, gun);
    }

    /** COLAPSO: onda expansiva virtual de gran alcance. Más daño en el centro; en el borde se puede esquivar. */
    private void collapse(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 center = player.position().add(0.0, 0.9, 0.0);
        AABB area = new AABB(center, center).inflate(COLLAPSE_RADIUS);
        int hits = 0;
        for (Entity e : level.getEntities(player, area,
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            LivingEntity target = (LivingEntity) e;
            Vec3 tc = target.getBoundingBox().getCenter();
            double dist = tc.distanceTo(center);
            if (dist > COLLAPSE_RADIUS) continue;

            double factor = Math.max(0.12, 1.0 - dist / COLLAPSE_RADIUS);       // 1 en el centro, ~0 en el borde
            boolean evaded = dist > COLLAPSE_RADIUS * 0.7 && level.random.nextFloat() < 0.5f;
            float damage = (float) (COLLAPSE_MAX_DAMAGE * factor * (evaded ? 0.3 : 1.0));
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), damage);
            hits++;
            if (evaded) {
                level.sendParticles(ParticleTypes.CLOUD, tc.x, tc.y, tc.z, 4, 0.2, 0.3, 0.2, 0.05);
                continue;                                  // esquivó el efecto de desestabilización
            }
            target.knockback(0.4 + 1.4 * factor, center.x - tc.x, center.z - tc.z);
            int slow = 40 + (int) (100 * factor);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, factor > 0.5 ? 2 : 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, slow, 1));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40 + (int) (80 * factor), 0));
        }

        // Onda visual: anillos concéntricos que salen del centro, rojos hacia blancos
        double baseY = player.getY() + 0.3;
        double[] radii = {2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0};
        for (double r : radii) {
            int n = (int) (10 + r * 4);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                double cx = Math.cos(a);
                double cz = Math.sin(a);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + cx * r, baseY, player.getZ() + cz * r, 0, cx, 0.02, cz, 0.6);
                if (i % 2 == 0) {
                    level.sendParticles(r < 8.0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                            player.getX() + cx * r, baseY + 0.2, player.getZ() + cz * r, 0, cx, 0.04, cz, 0.35);
                }
            }
        }
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5, player.getZ(), 6, 1.5, 0.3, 1.5, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 20, 2.0, 0.3, 2.0, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.6f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.0f, 1.4f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, COLLAPSE_COOLDOWN);
        announceRemaining(player, gun);
    }

    private void announceRemaining(ServerPlayer player, ItemStack gun) {
        player.displayClientMessage(Component.translatable("message.armasreales.omega_cargas",
                getLoaded(gun), MAX_LOADED), true);
    }
}
