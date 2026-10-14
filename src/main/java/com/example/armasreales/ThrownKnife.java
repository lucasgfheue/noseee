package com.example.armasreales;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Cuchillo arrojado: hiere a lo que toca y cae al suelo como objeto para recogerlo. */
public class ThrownKnife extends ThrowableItemProjectile {
    private static final float DAMAGE = 7.0f;

    public ThrownKnife(EntityType<? extends ThrownKnife> type, Level level) {
        super(type, level);
    }

    public ThrownKnife(Level level, LivingEntity owner) {
        super(ModEntities.KNIFE_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CUCHILLO.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        boolean creative = this.getOwner() instanceof Player p && p.getAbilities().instabuild;
        if (!creative) {
            this.spawnAtLocation(new ItemStack(ModItems.CUCHILLO.get()));
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.ARROW_HIT,
                SoundSource.NEUTRAL, 1.0f, 1.4f);
        this.discard();
    }
}
