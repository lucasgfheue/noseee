package com.example.armasreales;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** Lanzador explosivo genérico (granadas, cohetes, racimo, adhesivo). Sus explosiones no rompen bloques. */
public class ExplosiveLauncherItem extends Item implements Gun {
    private final Supplier<Item> ammoItem;
    private final int kind;
    private final float power;
    private final int fuse;
    private final float speed;
    private final int cooldown;
    private final float pitch;

    public ExplosiveLauncherItem(Properties properties, Supplier<Item> ammo, int kind, float power, int fuse,
                                 float speed, int cooldown, float pitch) {
        super(properties);
        this.ammoItem = ammo;
        this.kind = kind;
        this.power = power;
        this.fuse = fuse;
        this.speed = speed;
        this.cooldown = cooldown;
        this.pitch = pitch;
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
        return 0.8f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, this.ammoItem.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }
        Level level = player.level();
        ExplosiveShot shot = new ExplosiveShot(level, player, this.kind, this.power, this.fuse);
        shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, this.speed,
                player.isUsingItem() ? 0.2f : 1.4f);
        level.addFreshEntity(shot);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 1.2f, this.pitch);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.3f, this.pitch + 0.6f);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, this.cooldown);
        return false;
    }
}
