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
 * LÁZARO (ficticia): rifle de metal oscuro y tejido alienígena con un cristal magenta. Dispara proyectiles
 * orgánicos que envenenan. Guarda hasta 10 cargas; cuando se queda sin ellas absorbe un BIO-CORE (+5 cargas).
 * Habilidad pasiva: si permanece inactiva (sin disparar) recupera lentamente sus cargas por sí sola, incluso
 * guardada en el inventario.
 */
public class LazaroRifleItem extends Item implements Gun {
    public static final int MAX_CHARGES = 10;
    private static final int CORE_CHARGES = 5;
    private static final int IDLE_DELAY = 60;         // 3 s sin disparar antes de empezar a regenerar
    private static final int REGEN_INTERVAL = 40;     // luego +1 carga cada 2 s
    private static final int TICKS_BETWEEN_SHOTS = 6;
    private static final float DAMAGE = 6.5f;
    private static final float SPEED = 4.5f;
    private static final double HIP_SPREAD = 0.045;
    private static final double AIM_SPREAD = 0.012;

    public LazaroRifleItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ Estado

    /** Estado inicial de un Lázaro recién creado: medio cargado. */
    public static CustomData initialData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cargas", MAX_CHARGES / 2);
        tag.putLong("UltimoUso", 0L);
        return CustomData.of(tag);
    }

    public static int getCharges(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt("Cargas");
    }

    private static long getLastUse(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0L : data.copyTag().getLong("UltimoUso");
    }

    private static void setState(ItemStack stack, int charges, long lastUse) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putInt("Cargas", Math.max(0, Math.min(MAX_CHARGES, charges)));
        tag.putLong("UltimoUso", lastUse);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.max(1, Math.round(13.0f * getCharges(stack) / MAX_CHARGES));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return getCharges(stack) > 0 ? 0xE23CD0 : 0x6A2A66;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ------------------------------------------------------------------ Regeneración pasiva

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int charges = getCharges(stack);
        if (charges >= MAX_CHARGES) {
            return;
        }
        long now = server.getGameTime();
        long idle = now - getLastUse(stack);
        if (idle < IDLE_DELAY || (idle - IDLE_DELAY) % REGEN_INTERVAL != 0) {
            return;
        }
        setState(stack, charges + 1, getLastUse(stack));
        if (holder instanceof ServerPlayer player && selected) {
            Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0.0, -0.25, 0.0);
            server.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 5, 0.1, 0.1, 0.1, 0.02);
            server.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                    SoundSource.PLAYERS, 0.35f, 1.6f);
            player.displayClientMessage(Component.translatable("message.armasreales.lazaro_regenera",
                    charges + 1, MAX_CHARGES), true);
        }
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
        return 0.75f;
    }

    /** Sin cargas: absorbe un BIO-CORE del inventario (+5 cargas). Devuelve false si no hay. */
    private boolean feed(ServerPlayer player, ItemStack gun) {
        ServerLevel level = player.serverLevel();
        boolean creative = player.getAbilities().instabuild;
        ItemStack core = AmmoUtil.find(player, ModItems.BIOCORE.get());
        if (core.isEmpty() && !creative) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("message.armasreales.lazaro_vacio"), true);
            return false;
        }
        if (!creative) {
            core.shrink(1);
        }
        setState(gun, getCharges(gun) + CORE_CHARGES, level.getGameTime());
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                SoundSource.PLAYERS, 0.8f, 0.7f);
        Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0.0, -0.25, 0.0);
        level.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 14, 0.15, 0.15, 0.15, 0.05);
        player.displayClientMessage(Component.translatable("message.armasreales.lazaro_absorbe", CORE_CHARGES), true);
        player.getCooldowns().addCooldown(this, 20);
        return true;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (getCharges(gun) <= 0) {
            feed(player, gun);
            return false;
        }
        return true;   // disparo continuo
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (heldTicks % TICKS_BETWEEN_SHOTS != 0) {
            return true;
        }
        int charges = getCharges(gun);
        if (charges <= 0) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(),
                (player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD) + Math.min(0.02, heldTicks * 0.001));

        ElectricShot bolt = new ElectricShot(level, player, ElectricShot.BIO, DAMAGE);
        bolt.setPos(eye.x + look.x * 0.9, eye.y - 0.12 + look.y * 0.9, eye.z + look.z * 0.9);
        bolt.setDeltaMovement(look.scale(SPEED));
        level.addFreshEntity(bolt);

        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING,
                SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS, 0.8f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7f, 0.7f);
        Vec3 muzzle = eye.add(look.scale(1.0));
        level.sendParticles(ParticleTypes.DRAGON_BREATH, muzzle.x, muzzle.y - 0.12, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);

        setState(gun, charges - 1, level.getGameTime());
        if (!player.getAbilities().instabuild) {
            if ((heldTicks / TICKS_BETWEEN_SHOTS) % 5 == 0) {
                gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    @Override
    public void onFireStopped(ServerPlayer player, ItemStack gun, int heldTicks) {
        player.getCooldowns().addCooldown(this, 3);
    }
}
