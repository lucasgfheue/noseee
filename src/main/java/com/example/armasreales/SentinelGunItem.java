package com.example.armasreales;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * «SENTINELA» (ficticia): ametralladora de defensa pesada con núcleo ámbar y refrigeración visible.
 * Munición SENTINEL-B (cada cartucho carga 20 disparos). Cargador de 100 unidades. Cadencia 91/100.
 *  - Mantén el clic izquierdo: ráfaga continua de trazos ámbar.
 *  - Habilidad CONTROL DE ZONA: cuanto más tiempo sigue disparando en un mismo puesto, más se cierra la
 *    dispersión, hasta un límite. Moverse la enfría; al dejar de disparar el foco se pierde poco a poco.
 *  - Cuando se vacía recarga sola (4 s) con cartuchos SENTINEL-B del inventario.
 */
public class SentinelGunItem extends Item implements Gun {
    public static final int MAGAZINE = 100;
    public static final int ROUNDS_PER_ITEM = 20;
    public static final int RELOAD_TICKS = 80;
    public static final int MAX_FOCUS = 100;

    private static final double RANGE = 100.0;
    private static final float DAMAGE = 3.0f;
    private static final int SHOT_INTERVAL = 2;          // cadencia 91: ~10 disparos por segundo
    private static final double HIP_SPREAD = 0.05;
    private static final double HIP_MIN = 0.012;         // límite de precisión a la cadera
    private static final double AIM_SPREAD = 0.028;
    private static final double AIM_MIN = 0.004;         // límite de precisión apuntando
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.08f), 0.7f);

    public SentinelGunItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ Estado en el ítem

    private static int getInt(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(key);
    }

    private static long getLong(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0L : data.copyTag().getLong(key);
    }

    private static void setInt(ItemStack stack, String key, int value) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        if (tag.contains(key) && tag.getInt(key) == value) {
            return;
        }
        tag.putInt(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setLong(ItemStack stack, String key, long value) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putLong(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CustomData initialData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cargador", MAGAZINE);
        return CustomData.of(tag);
    }

    public static int getMag(ItemStack stack) {
        return getInt(stack, "Cargador");
    }

    private static int getReload(ItemStack stack) {
        return getInt(stack, "Recarga");
    }

    private static int getFocus(ItemStack stack) {
        return getInt(stack, "Foco");
    }

    // ------------------------------------------------------------------ Barra del objeto

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
        return Math.max(1, Math.round(13.0f * getMag(stack) / MAGAZINE));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return getReload(stack) > 0 ? 0xFFE9B0 : 0xFFA623;
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

    // ------------------------------------------------------------------ Munición

    private static int countAmmo(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.SENTINEL_B.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.SENTINEL_B.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeAmmo(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.SENTINEL_B.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.SENTINEL_B.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return max - left;
    }

    // ------------------------------------------------------------------ Tick: recarga y pérdida de foco

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        if (!(level instanceof ServerLevel server) || !(holder instanceof ServerPlayer player)) {
            return;
        }
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            stack.set(DataComponents.CUSTOM_DATA, initialData());
        }
        int focus = getFocus(stack);
        if (focus > 0 && server.getGameTime() - getLong(stack, "UltimoTiro") > 30) {
            setInt(stack, "Foco", Math.max(0, focus - 2));
        }
        int reload = getReload(stack);
        if (reload <= 0) {
            return;
        }
        if (!selected) {
            setInt(stack, "Recarga", 0);
            return;
        }
        reload++;
        if (reload % 16 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND,
                    SoundSource.PLAYERS, 0.5f, 0.7f + reload / (float) RELOAD_TICKS * 0.6f);
            server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY() - 0.4, player.getZ(),
                    3, 0.25, 0.1, 0.25, 0.01);
        }
        if (reload < RELOAD_TICKS) {
            setInt(stack, "Recarga", reload);
            return;
        }
        int loaded;
        if (player.getAbilities().instabuild) {
            loaded = MAGAZINE;
        } else {
            int items = Math.min((MAGAZINE + ROUNDS_PER_ITEM - 1) / ROUNDS_PER_ITEM, countAmmo(player));
            takeAmmo(player, items);
            loaded = Math.min(MAGAZINE, items * ROUNDS_PER_ITEM);
        }
        setInt(stack, "Cargador", loaded);
        setInt(stack, "Recarga", 0);
        setInt(stack, "Foco", 0);
        if (loaded > 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.PLAYERS, 0.9f, 0.8f);
            server.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.7f, 1.6f);
            player.displayClientMessage(Component.translatable("message.armasreales.sentinela_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.sentinela_sin_municion"), true);
        }
    }

    // ------------------------------------------------------------------ Disparo

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (getReload(gun) > 0) {
            player.displayClientMessage(Component.translatable("message.armasreales.sentinela_recargando"), true);
            return false;
        }
        if (getMag(gun) <= 0) {
            ServerLevel level = player.serverLevel();
            if (!player.getAbilities().instabuild && countAmmo(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.sentinela_sin_municion"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.7f);
            player.displayClientMessage(Component.translatable("message.armasreales.sentinela_recargando"), true);
            return false;
        }
        return true;   // ráfaga continua: los tiros salen en onFireHeld
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (getReload(gun) > 0 || getMag(gun) <= 0) {
            return false;
        }
        if (heldTicks % SHOT_INTERVAL != 0) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        int mag = getMag(gun);
        int focus = getFocus(gun);

        // CONTROL DE ZONA: en el puesto (quieto) gana foco rápido; en movimiento solo lento
        Vec3 move = player.getDeltaMovement();
        boolean still = move.x * move.x + move.z * move.z < 0.0025;
        focus = Math.min(MAX_FOCUS, focus + (still ? 4 : 1));
        double t = focus / (double) MAX_FOCUS;
        boolean aiming = player.isUsingItem();
        double base = aiming ? AIM_SPREAD : HIP_SPREAD;
        double min = aiming ? AIM_MIN : HIP_MIN;
        double spread = base + (min - base) * t;

        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), spread);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);
        Entity target = hit.entity();
        if (target != null) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), DAMAGE);
        }

        // Trazo ámbar brillante
        for (double d = 1.5; d < hit.distance(); d += 1.2) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(AMBER, p.x, p.y - 0.1, p.z, 1, 0, 0, 0, 0);
        }
        Vec3 end = eye.add(look.scale(hit.distance()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 2, 0.08, 0.08, 0.08, 0.1);

        // Refrigeración visible: vapor blanco cada ciertos disparos, más con más foco
        if (mag % 10 == 0 || (focus >= MAX_FOCUS && mag % 4 == 0)) {
            Vec3 vent = eye.add(look.scale(0.7)).add(0.0, -0.25, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 2, 0.08, 0.05, 0.08, 0.02);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.0f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK,
                SoundSource.PLAYERS, 0.35f, 1.9f);

        mag--;
        setInt(gun, "Cargador", mag);
        setInt(gun, "Foco", focus);
        setLong(gun, "UltimoTiro", level.getGameTime());
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild && mag % 10 == 0) {
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        if (mag % 5 == 0) {
            player.displayClientMessage(Component.translatable("message.armasreales.sentinela_estado",
                    mag, MAGAZINE, focus), true);
        }
        return mag > 0;
    }

    @Override
    public void onFireStopped(ServerPlayer player, ItemStack gun, int heldTicks) {
        player.getCooldowns().addCooldown(this, 3);
    }
}
