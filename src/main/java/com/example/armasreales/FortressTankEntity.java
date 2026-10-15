package com.example.armasreales;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Tanque «Fortaleza» (ficticio, Proyecto X): mismo tamaño y mecánicas que los otros tanques (conducción,
 * cañón de TNT, invulnerable salvo a explosiones de TNT), con sistema de escudo de cuatro emisores.
 *  - El escudo se activa solo: al subirse el conductor (3 s) y cuando una explosión de TNT golpea al tanque (5 s).
 *  - Con el escudo activo el tanque recibe solo el 25 % del daño de las explosiones.
 *  - Tras activarse por daño necesita 10 s para volver a hacerlo.
 *  - No cambia de color con tintes.
 */
public class FortressTankEntity extends TankEntity {
    public static final int SHIELD_TICKS = 100;
    public static final int MOUNT_SHIELD_TICKS = 60;
    private static final int RECHARGE_TICKS = 200;

    private static final EntityDataAccessor<Long> DATA_SHIELD_UNTIL =
            SynchedEntityData.defineId(FortressTankEntity.class, EntityDataSerializers.LONG);

    private long rechargeUntil = 0L;
    private boolean hadRider = false;

    public FortressTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD_UNTIL, 0L);
    }

    /** Ticks de escudo que quedan (0 = apagado). Sirve en cliente y servidor. */
    public int shieldRemaining() {
        long left = this.entityData.get(DATA_SHIELD_UNTIL) - this.level().getGameTime();
        return left > 0L ? (int) left : 0;
    }

    public boolean isShieldActive() {
        return this.shieldRemaining() > 0;
    }

    private void activateShield(int ticks) {
        long until = this.level().getGameTime() + ticks;
        if (until > this.entityData.get(DATA_SHIELD_UNTIL)) {
            this.entityData.set(DATA_SHIELD_UNTIL, until);
            this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.4f, 1.5f);
            this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.6f, 1.2f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            boolean rider = this.getControllingPassenger() != null;
            if (rider && !this.hadRider) {
                this.activateShield(MOUNT_SHIELD_TICKS);
            }
            this.hadRider = rider;
            if (this.tickCount % 20 == 0 && this.isShieldActive()) {
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_AMBIENT,
                        SoundSource.PLAYERS, 0.5f, 1.8f);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (explosion && this.isShieldActive()) {
            amount *= 0.25f;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && explosion && !this.level().isClientSide && !this.isShieldActive()
                && this.level().getGameTime() >= this.rechargeUntil) {
            this.activateShield(SHIELD_TICKS);
            this.rechargeUntil = this.level().getGameTime() + SHIELD_TICKS + RECHARGE_TICKS;
        }
        return hurt;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Fortaleza no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_FORTALEZA.get();
    }
}
