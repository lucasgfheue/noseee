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
 * «SOLARIS» (ficticio): lanzador de energía térmica con reactor naranja. Cápsulas SOL-3, capacidad 4, recarga 4 s.
 * Área de efecto 90/100.
 *  - Clic izquierdo: dardo solar incandescente (gasta 1 carga) que prende fuego al impactar.
 *  - Agachado + clic izquierdo, habilidad SOL: crea una esfera de energía solar que ilumina y calienta una
 *    zona muy amplia durante unos segundos (gasta 1 carga).
 *  - Cuando se queda sin cargas recarga sola con cápsulas SOL-3 del inventario.
 */
public class SolarisLauncherItem extends Item implements Gun {
    public static final int MAX_LOADED = 4;
    public static final int RELOAD_TICKS = 80;        // 4 s

    private static final int SHOT_COOLDOWN = 14;
    private static final int ZONE_COOLDOWN = 60;
    private static final float BOLT_DAMAGE = 9.0f;
    private static final double ZONE_RANGE = 36.0;

    public SolarisLauncherItem(Properties properties) {
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

    /** Naranja oscuro mientras recarga o está vacío; amarillo-blanco al completar la carga. */
    @Override
    public int getBarColor(ItemStack stack) {
        int reload = getReload(stack);
        if (reload > 0) {
            float t = Mth.clamp(reload / (float) RELOAD_TICKS, 0.0f, 1.0f);
            int g = (int) (60 + 130 * t);
            return (255 << 16) | (g << 8) | 20;
        }
        return getLoaded(stack) > 0 ? 0xFFD040 : 0x8A3010;
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
            player.displayClientMessage(Component.translatable("message.armasreales.solaris_lista", loaded), true);
        } else {
            player.displayClientMessage(Component.translatable("message.armasreales.solaris_sin_capsulas"), true);
        }
    }

    private static int countCores(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.SOL_3.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.SOL_3.get())) total += s.getCount();
        }
        return total;
    }

    private static int takeCores(Player player, int max) {
        int left = max;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.SOL_3.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.SOL_3.get())) {
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
            player.displayClientMessage(Component.translatable("message.armasreales.solaris_recargando"), true);
            return false;
        }
        if (getLoaded(gun) <= 0) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && countCores(player) <= 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                player.displayClientMessage(Component.translatable("message.armasreales.solaris_sin_capsulas"), true);
                player.getCooldowns().addCooldown(this, 10);
                return false;
            }
            setInt(gun, "Recarga", 1);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 0.9f);
            player.displayClientMessage(Component.translatable("message.armasreales.solaris_recargando"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            sun(level, player, gun);
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

    /** Dardo solar: trazo incandescente que prende fuego. */
    private void shoot(ServerLevel level, ServerPlayer player, ItemStack gun) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? 0.004 : 0.03);
        ElectricShot bolt = new ElectricShot(level, player, ElectricShot.TRACER, BOLT_DAMAGE);
        bolt.setIgnite(true);
        bolt.setPos(eye.x + look.x * 0.9, eye.y - 0.15 + look.y * 0.9, eye.z + look.z * 0.9);
        bolt.setDeltaMovement(look.scale(4.5));
        level.addFreshEntity(bolt);

        Vec3 muzzle = eye.add(look.scale(1.0)).add(0.0, -0.15, 0.0);
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.06);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.1f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2f, 0.7f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        announceRemaining(player, gun);
    }

    /** SOL: crea la esfera de energía solar donde apuntas. */
    private void sun(ServerLevel level, ServerPlayer player, ItemStack gun) {
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

        SolarSphereEntity.spawn(level, player, at);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.0f, 0.5f);

        spend(player, gun);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, ZONE_COOLDOWN);
        announceRemaining(player, gun);
    }

    private void announceRemaining(ServerPlayer player, ItemStack gun) {
        player.displayClientMessage(Component.translatable("message.armasreales.solaris_cargas",
                getLoaded(gun), MAX_LOADED), true);
    }
}
