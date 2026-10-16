package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comportamientos de los ciudadanos: huir de quien apunta, disparar, robar. */
final class CiudadanoGoals {
    private CiudadanoGoals() {}

    /** ¿Está el jugador apuntando con un arma a esta entidad? */
    static boolean aimedAt(Player p, LivingEntity e) {
        if (!(p.getMainHandItem().getItem() instanceof Gun) && !(p.getOffhandItem().getItem() instanceof Gun)) {
            return false;
        }
        if (p.isSpectator()) {
            return false;
        }
        Vec3 to = e.getBoundingBox().getCenter().subtract(p.getEyePosition());
        double d = to.length();
        if (d < 0.5 || d > 28.0) {
            return false;
        }
        double tan = 1.0 / d;                                   // ~1 bloque de margen
        double minCos = 1.0 / Math.sqrt(1.0 + tan * tan);
        return p.getViewVector(1.0f).dot(to) / d > Math.min(0.995, minCos) && p.hasLineOfSight(e);
    }

    // ------------------------------------------------------------------ Huir de quien apunta

    static final class FleeAimGoal extends Goal {
        private final CiudadanoEntity mob;
        private Player from;

        FleeAimGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean flees() {
            return mob.isCivil() || (mob.isThief() && !mob.hasGun() && !CiudadanoEntity.isNight(mob.level()));
        }

        @Override
        public boolean canUse() {
            if (!flees() || mob.tickCount % 4 != 0 || mob.fleeTicks > 0) {
                return false;
            }
            Player best = null;
            double bestD = Double.MAX_VALUE;
            for (Player p : mob.level().players()) {
                double d = p.distanceToSqr(mob);
                if (d < 28.0 * 28.0 && d < bestD && CiudadanoGoals.aimedAt(p, mob)) {
                    best = p;
                    bestD = d;
                }
            }
            this.from = best;
            return best != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.from != null && this.from.isAlive() && mob.distanceToSqr(this.from) < 26.0 * 26.0
                    && (!mob.getNavigation().isDone() || CiudadanoGoals.aimedAt(this.from, mob));
        }

        @Override
        public void start() {
            this.run();
        }

        private void run() {
            Vec3 pos = DefaultRandomPos.getPosAway(mob, 16, 7, this.from.position());
            if (pos != null) {
                mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.5);
            }
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone() && this.from != null) {
                this.run();
            }
        }

        @Override
        public void stop() {
            this.from = null;
        }
    }

    /** Tras un robo, el ladrón huye de su víctima. */
    static final class FleeAfterTheftGoal extends Goal {
        private final CiudadanoEntity mob;

        FleeAfterTheftGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.fleeTicks > 0 && mob.fleeFrom != null && !mob.hasGun();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void tick() {
            if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
                Vec3 pos = DefaultRandomPos.getPosAway(mob, 18, 7, mob.fleeFrom);
                if (pos != null) {
                    mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.45);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Buscar al jugador de noche

    static final class NightSeekGoal extends Goal {
        private final CiudadanoEntity mob;
        private Player player;

        NightSeekGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.isCivil() || mob.tickCount % 20 != 0 || mob.getTarget() != null || mob.fleeTicks > 0
                    || !CiudadanoEntity.isNight(mob.level())
                    || mob.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                return false;
            }
            if (!mob.hasGun() && !mob.isThief()) {
                return false;
            }
            Player p = mob.level().getNearestPlayer(mob, 64.0);
            if (p == null || p.isCreative() || p.isSpectator()) {
                return false;
            }
            this.player = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isAlive() && mob.getTarget() == null
                    && !mob.getNavigation().isDone() && CiudadanoEntity.isNight(mob.level());
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(this.player, 1.0);
        }

        @Override
        public void stop() {
            this.player = null;
        }
    }

    // ------------------------------------------------------------------ Robar

    static final class StealGoal extends Goal {
        private final CiudadanoEntity mob;

        StealGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return mob.isThief() && !mob.hasGun() && mob.stealCooldown <= 0 && mob.fleeTicks <= 0
                    && CiudadanoEntity.isNight(mob.level()) && t instanceof Player p && p.isAlive()
                    && !p.isCreative() && !p.isSpectator();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(mob.getTarget() instanceof Player player)) {
                return;
            }
            mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
            if (mob.tickCount % 4 == 0) {
                mob.getNavigation().moveTo(player, 1.3);
            }
            if (mob.distanceToSqr(player) < 2.8 && mob.hasLineOfSight(player)) {
                this.steal(player);
            }
        }

        private void steal(Player player) {
            List<Integer> guns = new ArrayList<>();
            List<Integer> all = new ArrayList<>();
            var items = player.getInventory().items;
            for (int i = 0; i < items.size(); i++) {
                ItemStack st = items.get(i);
                if (!st.isEmpty()) {
                    all.add(i);
                    if (st.getItem() instanceof Gun) {
                        guns.add(i);
                    }
                }
            }
            mob.stealCooldown = 200;
            if (all.isEmpty()) {
                return;
            }
            List<Integer> pool = !guns.isEmpty() && mob.getRandom().nextFloat() < 0.7f ? guns : all;
            int slot = pool.get(mob.getRandom().nextInt(pool.size()));
            ItemStack taken = items.get(slot).copy();
            items.set(slot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            mob.takeLoot(taken);
            player.displayClientMessage(Component.translatable("msg.armasreales.robado", taken.getHoverName()), true);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.0f, 0.6f);
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
            }
            if (taken.getItem() instanceof Gun) {
                mob.provoked = 1200;                    // con el arma robada te ataca a ti
            } else {
                mob.fleeFrom = player.position();
                mob.fleeTicks = 300;
                mob.setTarget(null);
            }
        }
    }

    // ------------------------------------------------------------------ Disparar

    static final class ShootGoal extends Goal {
        private final CiudadanoEntity mob;
        private int shotCooldown = 20;
        private int burstLeft;
        private int burstDelay;

        ShootGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private double range() {
            Item i = mob.getMainHandItem().getItem();
            return i instanceof SniperItem ? 45.0 : 24.0;
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || !mob.hasGun() || !mob.wantsToFight()) {
                return false;
            }
            double max = range();
            return mob.distanceToSqr(t) < max * max && mob.getSensing().hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() || this.burstLeft > 0;
        }

        @Override
        public void start() {
            mob.setAggressive(true);
        }

        @Override
        public void stop() {
            mob.setAggressive(false);
            mob.getNavigation().stop();
            this.burstLeft = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            mob.getLookControl().setLookAt(target, 40.0f, 40.0f);
            double dist = mob.distanceTo(target);
            if (dist > 12.0) {
                mob.getNavigation().moveTo(target, 1.0);
            } else {
                mob.getNavigation().stop();
            }
            if (this.shotCooldown > 0) {
                this.shotCooldown--;
            }
            Item gun = mob.getMainHandItem().getItem();
            if (this.burstLeft > 0) {
                if (--this.burstDelay <= 0) {
                    fire(level, target, gun, dist);
                    this.burstLeft--;
                    this.burstDelay = 2;
                    if (this.burstLeft == 0) {
                        this.shotCooldown = 35;
                    }
                }
                return;
            }
            if (this.shotCooldown <= 0 && mob.getSensing().hasLineOfSight(target)) {
                if (gun == ModItems.AK47.get()) {
                    this.burstLeft = 4;
                    this.burstDelay = 0;
                } else {
                    fire(level, target, gun, dist);
                    this.shotCooldown = gun == ModItems.ESCOPETA.get() ? 45 : gun instanceof SniperItem ? 60 : 25;
                }
            }
        }

        private void fire(ServerLevel level, LivingEntity target, Item gun, double dist) {
            Vec3 eye = mob.getEyePosition();
            Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            Vec3 dir = aim.subtract(eye).normalize();
            DamageSource source = level.damageSources().mobAttack(mob);
            java.util.function.Predicate<Entity> notCitizen = e -> !(e instanceof CiudadanoEntity);

            if (gun == ModItems.ESCOPETA.get()) {
                Map<Entity, Float> damage = new LinkedHashMap<>();
                for (int i = 0; i < 8; i++) {
                    Vec3 d = Bullets.spread(level, dir, 0.07);
                    Bullets.Hit hit = Bullets.trace(level, mob, eye, d, 30.0, notCitizen);
                    if (hit.entity() != null) {
                        float falloff = (float) Math.max(0.0, 1.0 - hit.distance() / 20.0);
                        damage.merge(hit.entity(), 0.4f + 3.0f * falloff, Float::sum);
                    }
                }
                for (Map.Entry<Entity, Float> e : damage.entrySet()) {
                    e.getKey().invulnerableTime = 0;
                    e.getKey().hurt(source, e.getValue());
                }
                level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.7f, 1.6f);
                effects(level, eye, dir, 6);
                return;
            }
            boolean ak = gun == ModItems.AK47.get();
            float dmg = ak ? 3.0f : gun instanceof SniperItem ? 12.0f : gun instanceof MagnumItem ? 8.0f
                    : gun instanceof RevolverItem ? 6.0f : gun instanceof PistolItem ? 4.0f : 5.0f;
            double spread = (ak ? 0.05 : 0.035) + dist * 0.0015;
            Vec3 d = Bullets.spread(level, dir, spread);
            Bullets.Hit hit = Bullets.trace(level, mob, eye, d, gun instanceof SniperItem ? 80.0 : 50.0, notCitizen);
            if (hit.entity() != null) {
                hit.entity().invulnerableTime = 0;
                hit.entity().hurt(source, dmg);
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.4f,
                    ak ? 0.55f : 0.7f);
            effects(level, eye, d, 2);
        }

        private void effects(ServerLevel level, Vec3 eye, Vec3 dir, int smoke) {
            Vec3 muzzle = eye.add(dir.scale(0.9));
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, smoke, 0.05, 0.05, 0.05, 0.01);
            level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
