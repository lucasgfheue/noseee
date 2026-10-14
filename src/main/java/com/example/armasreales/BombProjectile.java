package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Bomba arrojada: explota al instante al tocar una entidad o un bloque (rompe bloques como el TNT). */
public class BombProjectile extends ThrowableItemProjectile {
    private static final float EXPLOSION_POWER = 3.5f;
    private static final int MAX_LIFE_TICKS = 200;

    public BombProjectile(EntityType<? extends BombProjectile> type, Level level) {
        super(type, level);
    }

    public BombProjectile(Level level, LivingEntity owner) {
        super(ModEntities.BOMB_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BOMBA.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > MAX_LIFE_TICKS) {
            this.discard();
            return;
        }
        // Chispa de la mecha
        level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(0.5) + 0.3, getZ(), 2, 0.04, 0.04, 0.04, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.5) + 0.3, getZ(), 1, 0.05, 0.05, 0.05, 0.0);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.level().explode(this, getX(), getY(0.5), getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
            this.discard();
        }
    }
}
