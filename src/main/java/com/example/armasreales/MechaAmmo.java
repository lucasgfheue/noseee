package com.example.armasreales;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Munición del mecha: cada brazo dispara lo que lleve en su hueco. Los proyectiles propios del mod se reutilizan
 * (granadas, cohetes, calabazas, ácido, descargas eléctricas); el resto son disparos instantáneos con su propio
 * daño, efecto y partículas, una versión simplificada del arma original.
 */
public final class MechaAmmo {
    private MechaAmmo() {}

    private static final int NONE = 0, FREEZE = 1, POISON = 2, WITHER = 3, BURN = 4;
    private static final int[][] EXPLOSIVE_KINDS = {
            {ExplosiveShot.GRENADE, 45}, {ExplosiveShot.ROCKET, 0}, {ExplosiveShot.CLUSTER, 0}, {ExplosiveShot.STICKY, 40}};
    private static final float[] EXPLOSIVE_SPEED = {2.6f, 3.2f, 1.8f, 3.0f};
    private static final float[] EXPLOSIVE_POWER = {1.4f, 2.2f, 1.6f, 1.5f};
    private static int explosiveCounter = 0;

    /** Disparo instantáneo: daño, espera, perdigones, dispersión, alcance, atraviesa, efecto, explosión, partícula. */
    private record Prof(float damage, int cooldown, int pellets, double spread, double range, boolean pierce,
                        int effect, float explosion, ParticleOptions particle) {}

    private static Prof profile(ItemStack a) {
        if (a.is(ModItems.MUNICION_PISTOLA.get())) {
            return new Prof(8.0f, 5, 1, 0.0, 120.0, false, NONE, 0.0f, ParticleTypes.CRIT);
        }
        if (a.is(ModItems.MUNICION_FUSIL.get())) {
            return new Prof(10.0f, 4, 1, 0.01, 120.0, false, NONE, 0.0f, ParticleTypes.CRIT);
        }
        if (a.is(ModItems.MUNICION_FRANCOTIRADOR.get())) {
            return new Prof(45.0f, 30, 1, 0.0, 220.0, false, NONE, 0.0f, ParticleTypes.CRIT);
        }
        if (a.is(ModItems.MUNICION_MAGNUM.get())) {
            return new Prof(20.0f, 10, 1, 0.0, 120.0, false, NONE, 0.0f, ParticleTypes.CRIT);
        }
        if (a.is(ModItems.CARTUCHO_ESCOPETA.get())) {
            return new Prof(4.5f, 18, 8, 0.07, 40.0, false, NONE, 0.0f, ParticleTypes.SMOKE);
        }
        if (a.is(ModItems.CELDA_ENERGIA.get())) {
            return new Prof(14.0f, 8, 1, 0.0, 100.0, false, NONE, 0.0f, ParticleTypes.END_ROD);
        }
        if (a.is(ModItems.CELDA_CRIOGENICA.get())) {
            return new Prof(10.0f, 8, 1, 0.0, 90.0, false, FREEZE, 0.0f, ParticleTypes.SNOWFLAKE);
        }
        if (a.is(ModItems.FROST_CELL.get())) {
            return new Prof(12.0f, 8, 1, 0.0, 90.0, false, FREEZE, 0.0f, ParticleTypes.SNOWFLAKE);
        }
        if (a.is(ModItems.CARGA_LAB10.get())) {
            return new Prof(18.0f, 14, 1, 0.0, 80.0, true, NONE, 0.0f, ParticleTypes.ELECTRIC_SPARK);
        }
        if (a.is(ModItems.OMG3.get())) {
            return new Prof(25.0f, 40, 1, 0.0, 120.0, false, NONE, 3.5f, ParticleTypes.FLAME);
        }
        if (a.is(ModItems.ECL4.get())) {
            return new Prof(20.0f, 20, 1, 0.0, 100.0, false, WITHER, 0.0f, ParticleTypes.LARGE_SMOKE);
        }
        if (a.is(ModItems.REFRACT_5.get())) {
            return new Prof(16.0f, 10, 1, 0.0, 100.0, true, NONE, 0.0f, ParticleTypes.END_ROD);
        }
        if (a.is(ModItems.SWARM_6.get())) {
            return new Prof(6.0f, 20, 5, 0.1, 80.0, false, NONE, 0.0f, ParticleTypes.CRIT);
        }
        if (a.is(ModItems.MASS_1.get())) {
            return new Prof(60.0f, 45, 1, 0.0, 150.0, false, NONE, 2.5f, ParticleTypes.CLOUD);
        }
        return null;
    }

    public static boolean isAmmo(ItemStack a) {
        return a.is(Items.TNT) || a.is(Items.FIRE_CHARGE) || a.is(Items.ARROW) || a.is(Items.SLIME_BALL)
                || a.is(Items.JACK_O_LANTERN) || a.is(ModItems.FERRO9.get()) || a.is(ModItems.CARGA_EXPLOSIVA.get())
                || a.is(ModItems.BOLA_ELECTRICA.get()) || a.is(ModItems.CHISPA.get()) || a.is(ModItems.BIOCORE.get())
                || a.is(ModItems.SENTINEL_B.get()) || a.is(ModItems.SOL_3.get()) || profile(a) != null;
    }

    /** Dispara una unidad de esa munición. Devuelve la espera en ticks (0 si no pudo disparar). */
    public static int fire(MechaEntity mecha, ServerPlayer player, ServerLevel level, Vec3 muzzle, Vec3 dir, ItemStack a) {
        int cooldown;
        if (a.is(Items.TNT)) {
            TntProjectile shell = new TntProjectile(level, player);
            shell.setPos(muzzle.x, muzzle.y - shell.getBbHeight() / 2.0, muzzle.z);
            shell.shoot(dir.x, dir.y, dir.z, 3.2f, 0.0f);
            level.addFreshEntity(shell);
            sound(level, mecha, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3.0f, 0.5f);
            cooldown = 16;
        } else if (a.is(Items.FIRE_CHARGE)) {
            LargeFireball fireball = new LargeFireball(level, player, dir, 2);
            fireball.setPos(muzzle.x, muzzle.y, muzzle.z);
            level.addFreshEntity(fireball);
            sound(level, mecha, SoundEvents.BLAZE_SHOOT, 2.0f, 0.6f);
            cooldown = 14;
        } else if (a.is(Items.ARROW) || a.is(ModItems.FERRO9.get())) {
            boolean heavy = a.is(ModItems.FERRO9.get());
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            arrow.setPos(muzzle.x, muzzle.y, muzzle.z);
            arrow.shoot(dir.x, dir.y, dir.z, heavy ? 5.5f : 4.0f, 0.0f);
            arrow.setBaseDamage(heavy ? 18.0 : 6.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(arrow);
            sound(level, mecha, SoundEvents.CROSSBOW_SHOOT, 2.0f, heavy ? 0.5f : 0.7f);
            cooldown = heavy ? 18 : 6;
        } else if (a.is(ModItems.CARGA_EXPLOSIVA.get())) {
            int k = explosiveCounter++ % EXPLOSIVE_KINDS.length;     // granada, cohete, racimo y adhesivo, por turnos
            ExplosiveShot shot = new ExplosiveShot(level, player, EXPLOSIVE_KINDS[k][0], EXPLOSIVE_POWER[k], EXPLOSIVE_KINDS[k][1]);
            shot.setPos(muzzle.x, muzzle.y, muzzle.z);
            shot.shoot(dir.x, dir.y, dir.z, EXPLOSIVE_SPEED[k], 0.5f);
            level.addFreshEntity(shot);
            sound(level, mecha, SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0f, 0.7f);
            cooldown = 24;
        } else if (a.is(Items.JACK_O_LANTERN)) {
            PumpkinProjectile shot = new PumpkinProjectile(level, player);
            shot.setPos(muzzle.x, muzzle.y, muzzle.z);
            shot.shoot(dir.x, dir.y, dir.z, 2.4f, 0.3f);
            level.addFreshEntity(shot);
            sound(level, mecha, SoundEvents.PUMPKIN_CARVE, 2.0f, 0.6f);
            cooldown = 20;
        } else if (a.is(Items.SLIME_BALL)) {
            AcidProjectile shot = new AcidProjectile(level, player);
            shot.setPos(muzzle.x, muzzle.y, muzzle.z);
            shot.shoot(dir.x, dir.y, dir.z, 2.0f, 0.3f);
            level.addFreshEntity(shot);
            sound(level, mecha, SoundEvents.SLIME_SQUISH, 2.0f, 0.8f);
            cooldown = 10;
        } else if (a.is(ModItems.BOLA_ELECTRICA.get()) || a.is(ModItems.CHISPA.get()) || a.is(ModItems.BIOCORE.get())
                || a.is(ModItems.SENTINEL_B.get()) || a.is(ModItems.SOL_3.get())) {
            int kind;
            float damage;
            float speed = 2.5f;
            if (a.is(ModItems.CHISPA.get())) {
                kind = ElectricShot.SPARK;
                damage = 8.0f;
                cooldown = 8;
            } else if (a.is(ModItems.BIOCORE.get())) {
                kind = ElectricShot.BIO;
                damage = 12.0f;
                cooldown = 14;
            } else if (a.is(ModItems.SENTINEL_B.get())) {
                kind = ElectricShot.TRACER;
                damage = 6.0f;
                speed = 3.5f;
                cooldown = 3;
            } else if (a.is(ModItems.SOL_3.get())) {
                kind = ElectricShot.ORB;
                damage = 12.0f;
                speed = 1.8f;
                cooldown = 25;
            } else {
                kind = ElectricShot.BALL;
                damage = 9.0f;
                cooldown = 12;
            }
            ElectricShot shot = new ElectricShot(level, player, kind, damage);
            shot.setPos(muzzle.x, muzzle.y, muzzle.z);
            shot.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
            level.addFreshEntity(shot);
            sound(level, mecha, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 1.2f);
        } else {
            Prof pr = profile(a);
            if (pr == null) {
                return 0;
            }
            hitscan(mecha, player, level, muzzle, dir, pr);
            sound(level, mecha, SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 0.8f);
            cooldown = pr.cooldown();
        }
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.15, 0.15, 0.15, 0.02);
        return cooldown;
    }

    private static void sound(ServerLevel level, Entity at, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        level.playSound(null, at.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    private static void hitscan(MechaEntity mecha, ServerPlayer player, ServerLevel level, Vec3 muzzle, Vec3 dir, Prof pr) {
        for (int n = 0; n < pr.pellets(); n++) {
            Vec3 d = Bullets.spread(level, dir, pr.spread());
            // Los disparos que atraviesan ignoran a las entidades al trazar y las buscan después a lo largo de la línea
            Bullets.Hit hit = Bullets.trace(level, player, muzzle, d, pr.range(),
                    e -> !pr.pierce() && e != mecha && e != player);
            Vec3 end = hit.end();
            for (double dd = 2.0; dd < hit.distance(); dd += 2.0) {
                Vec3 q = muzzle.add(d.scale(dd));
                level.sendParticles(pr.particle(), q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (pr.pierce()) {
                AABB box = new AABB(muzzle, end).inflate(1.0);
                List<Entity> list = level.getEntities(player, box,
                        e -> e != mecha && e != player && e instanceof LivingEntity && e.isPickable() && !e.isSpectator());
                for (Entity e : list) {
                    if (e.getBoundingBox().inflate(0.3).clip(muzzle, end).isPresent()) {
                        damage(level, player, e, pr);
                    }
                }
            } else if (hit.entity() != null) {
                damage(level, player, hit.entity(), pr);
            }
            if (pr.explosion() > 0.0f && hit.distance() < pr.range() - 0.5) {
                level.explode(player, end.x, end.y, end.z, pr.explosion(), Level.ExplosionInteraction.TNT);
            }
        }
    }

    private static void damage(ServerLevel level, ServerPlayer player, Entity target, Prof pr) {
        target.hurt(level.damageSources().playerAttack(player), pr.damage());
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        switch (pr.effect()) {
            case FREEZE -> {
                living.setTicksFrozen(living.getTicksRequiredToFreeze() + 60);
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            }
            case POISON -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            case WITHER -> living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            case BURN -> living.setRemainingFireTicks(100);
            default -> { }
        }
    }
}
