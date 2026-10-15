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
 * X-29 «ECLIPSE» (clasificada, ficticia): lanzador de energía. Esferas ECL-4, capacidad 4, recarga 3,7 s.
 *  - Clic izquierdo: lanza una esfera de energía gravitatoria (gasta 1 carga); al impactar atrae a los cercanos.
 *  - Agachado + clic izquierdo, habilidad HORIZONTE (gasta 1 carga): crea una zona que atrae suavemente a los
 *    enemigos hacia el centro y los ralentiza; más fuerte en el centro, nunca inmoviliza.
 *  - Cuando se queda sin cargas recarga sola con esferas ECL-4 del inventario.
 */
public class EclipseLauncherItem extends Item implements Gun {
    public static final int MAX_LOADED = 4;
    public static final int RELOAD_TICKS = 74;        // 3,7 s

    private static final int SHOT_COOLDOWN = 14;
    private static final int ZONE_COOLDOWN = 50;
    private static final float ORB_DAMAGE = 9.0f;
    private static final float ORB_SPEED = 1.7f;
    private static final double ZONE_RANGE = 32.0;

    public EclipseLauncherItem(Properties properties) {
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

    /** Azul oscuro mientras recarga o está vacío; cian brillante al completar la carga. */
    @Override
    public int getBarColor(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            float t = Mth.clamp(reload / (float) RELOAD_TICKS, 0.0f, 1.0f);
            int r = (int) (30 + 50 * t);
            int g = (int) (60 + 180 * t);
            return (r << 16) | (g << 8) | 255;
        }
        return getLoaded(stack) > 0 ? 0x40E8FF : 0x30508A;
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
            player.displayClientMessage(Component.translatable("message.armasreales.eclipse_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.eclipse_sin_esferas"), true);
        }
    }

    private static int countCores(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.ECL4.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.ECL4.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeCores(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.ECL4.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.ECL4.get())) {
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
            player.displayClientMessage(Component.translatable("message.armasreales.eclipse_recargando"), true);
            return false;
        }
        if (getLoaded(gun) <= 0) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && countCores(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.eclipse_sin_esferas"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.9f);
            player.displayClientMessage(Component.translatable("message.armasreales.eclipse_recargando"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            horizon(level, player, gun);
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

    /** Lanza una esfera de energía gravitatoria. */
    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        ElectricShot orb = new ElectricShot(level, player, ElectricShot.ORB, ORB_DAMAGE);
        orb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, ORB_SPEED,
                player.isUsingItem() ? 0.0f : 1.5f);
        level.addFreshEntity(orb);

        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(1.0)).add(0.0, -0.15, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, muzzle.x, muzzle.y, muzzle.z, 12, 0.15, 0.15, 0.15, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2f, 0.7f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        announceRemaining(player, gun);
    }

    /** HORIZONTE: crea la zona de atracción donde apuntas. */
    private void horizon(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, ZONE_RANGE);
        Vec3 at = eye.add(look.scale(hit.distance()));
        // Si apuntas al aire, la zona cae al suelo bajo ese punto
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 12 && level.getBlockState(below).isAir(); i++) {
            below = below.below();
        }
        at = new Vec3(at.x, Math.max(below.getY() + 1.0, at.y - 12.0), at.z);

        GravityZoneEntity.spawn(level, player, at);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8f, 0.5f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, ZONE_COOLDOWN);
        announceRemaining(player, gun);
    }

    private void announceRemaining(ServerPlayer player, ItemStack gun) {
        player.displayClientMessage(Component.translatable("message.armasreales.eclipse_cargas",
                getLoaded(gun), MAX_LOADED), true);
    }
}
