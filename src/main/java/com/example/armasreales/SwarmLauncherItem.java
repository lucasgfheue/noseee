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
 * «ENJAMBRE» (ficticio): lanzador de cápsulas autónomas con módulo verde. Cápsulas SWARM-6, capacidad 6.
 * Control 94/100: los dispositivos giran con rapidez hacia su objetivo.
 *  - Clic izquierdo: libera 1 dispositivo (1 carga) que marca y persigue al objetivo más cercano a tu mira.
 *  - Agachado + clic izquierdo, habilidad ENJAMBRE: marca hasta 3 objetivos y libera 2 dispositivos por carga
 *    (usa hasta 3 cargas). Al llegar, dañan (6 cada uno) y ralentizan al objetivo; nunca lo inmovilizan.
 *  - Cuando se queda sin cargas recarga sola con cápsulas SWARM-6 del inventario.
 */
public class SwarmLauncherItem extends Item implements Gun {
    public static final int MAX_LOADED = 6;
    public static final int RELOAD_TICKS = 70;        // 3,5 s

    private static final int SHOT_COOLDOWN = 10;
    private static final int ZONE_COOLDOWN = 45;
    private static final double LOCK_RANGE = 36.0;
    private static final double LOCK_CONE = 0.965;       // cos ~15°: control 94
    private static final int SWARM_CHARGES = 3;
    private static final int DRONES_PER_CHARGE = 2;

    public SwarmLauncherItem(Properties properties) {
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

    /** Verde oscuro mientras recarga o está vacío; verde brillante al completar la carga. */
    @Override
    public int getBarColor(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            float t = Mth.clamp(reload / (float) RELOAD_TICKS, 0.0f, 1.0f);
            int g = (int) (70 + 150 * t);
            return (30 << 16) | (g << 8) | 40;
        }
        return getLoaded(stack) > 0 ? 0x40FF70 : 0x1E5A2E;
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
            player.displayClientMessage(Component.translatable("message.armasreales.enjambre_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.enjambre_sin_capsulas"), true);
        }
    }

    private static int countCores(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.SWARM_6.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.SWARM_6.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeCores(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.SWARM_6.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.SWARM_6.get())) {
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
            player.displayClientMessage(Component.translatable("message.armasreales.enjambre_recargando"), true);
            return false;
        }
        if (getLoaded(gun) <= 0) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && countCores(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.enjambre_sin_capsulas"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.9f);
            player.displayClientMessage(Component.translatable("message.armasreales.enjambre_recargando"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            swarm(level, player, gun);
        } else {
            shoot(level, player, gun);
        }
        return false;
    }

    private void spend(ServerPlayer player, ItemStack gun, int n) {
        setInt(gun, "Cargas", Math.max(0, getLoaded(gun) - n));
        if (!player.getAbilities().instabuild) {
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }

    /** Dispositivos marcables: seres vivos cerca de la mira, ordenados por cercanía angular. */
    private static java.util.List<LivingEntity> findTargets(ServerLevel level, ServerPlayer player, int max) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(LOCK_RANGE);
        java.util.List<LivingEntity> found = new java.util.ArrayList<>();
        for (Entity e : level.getEntities(player, box, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator()
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            LivingEntity le = (LivingEntity) e;
            Vec3 to = le.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > LOCK_RANGE || dist < 0.5) continue;
            if (to.normalize().dot(look) < LOCK_CONE) continue;
            if (!player.hasLineOfSight(le)) continue;
            found.add(le);
        }
        found.sort(java.util.Comparator.comparingDouble(
                (LivingEntity le) -> -le.getBoundingBox().getCenter().subtract(eye).normalize().dot(look)));
        return found.size() > max ? new java.util.ArrayList<>(found.subList(0, max)) : found;
    }

    private static void mark(ServerLevel level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));   // objetivo marcado
        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + target.getBbHeight() * 0.6, c.z, 6, 0.3, 0.3, 0.3, 0.0);
    }

    /** Libera 1 dispositivo hacia el objetivo más cercano a la mira. */
    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        java.util.List<LivingEntity> targets = findTargets(level, player, 1);
        LivingEntity target = targets.isEmpty() ? null : targets.get(0);
        if (target != null) {
            mark(level, target);
        }
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.15, 0.0);
        SwarmDroneEntity.launch(level, player, from, look, target, 0.5);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND,
                SoundSource.PLAYERS, 0.8f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_EXIT,
                SoundSource.PLAYERS, 1.0f, 1.4f);

        spend(player, gun, 1);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        announceRemaining(player, gun);
    }

    /** ENJAMBRE: marca hasta 3 objetivos y libera varios dispositivos por carga. */
    private void swarm(ServerLevel level, ServerPlayer player, ItemStack gun) {
        int charges = Math.min(SWARM_CHARGES, getLoaded(gun));
        java.util.List<LivingEntity> targets = findTargets(level, player, 3);
        for (LivingEntity t : targets) {
            mark(level, t);
        }
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.15, 0.0);
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = look.cross(up).normalize();
        int total = charges * DRONES_PER_CHARGE;
        for (int i = 0; i < total; i++) {
            double off = (i - (total - 1) / 2.0) * 0.35;                       // abanico inicial
            Vec3 dir = look.add(side.scale(off * 0.5)).add(0.0, 0.08 * (i % 2 == 0 ? 1 : -1), 0.0).normalize();
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            SwarmDroneEntity.launch(level, player, from, dir, target, 0.45 + 0.05 * (i % 3));
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, from.x, from.y, from.z, 14, 0.25, 0.25, 0.25, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_EXIT,
                SoundSource.PLAYERS, 1.5f, 0.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND,
                SoundSource.PLAYERS, 0.9f, 1.2f);

        spend(player, gun, charges);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, ZONE_COOLDOWN);
        announceRemaining(player, gun);
    }

    private void announceRemaining(ServerPlayer player, ItemStack gun) {
        player.displayClientMessage(Component.translatable("message.armasreales.enjambre_cargas",
                getLoaded(gun), MAX_LOADED), true);
    }
}
