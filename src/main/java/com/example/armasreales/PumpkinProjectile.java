package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Linterna de calabaza disparada: brilla, deja chispas y explota al chocar sin romper bloques. */
public class PumpkinProjectile extends ThrowableItemProjectile {
    private static final float EXPLOSION_POWER = 2.5f;
    private static final int MAX_LIFE_TICKS = 120;

    public PumpkinProjectile(EntityType<? extends PumpkinProjectile> type, Level level) {
        super(type, level);
    }

    public PumpkinProjectile(Level level, LivingEntity owner) {
        super(ModEntities.PUMPKIN_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.JACK_O_LANTERN;
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
        double y = this.getY(0.5);
        level.sendParticles(ParticleTypes.FLAME, getX(), y, getZ(), 3, 0.15, 0.15, 0.15, 0.01);
        level.sendParticles(ParticleTypes.LAVA, getX(), y, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 1, 0.1, 0.1, 0.1, 0.01);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // NONE: la explosión hiere a las entidades pero no rompe ningún bloque
        level.explode(this, getX(), getY(0.5), getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY(0.5), getZ(), 40, 0.6, 0.6, 0.6, 0.08);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY(0.5), getZ(), 15, 0.5, 0.5, 0.5, 0.0);
        level.playSound(null, this.blockPosition(), SoundEvents.PUMPKIN_CARVE,
                SoundSource.NEUTRAL, 1.0f, 0.5f);
        this.discard();
    }
}
