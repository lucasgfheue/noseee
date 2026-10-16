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

    // ------------------------------------------------------------------ Cristales y escaleras

    static boolean isGlass(net.minecraft.world.level.block.state.BlockState st) {
        return st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                || st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES);
    }

    /** Resultado de mirar de un punto a otro ignorando los cristales. */
    record Look(boolean clear, net.minecraft.core.BlockPos glass) {}

    static Look look(net.minecraft.world.level.Level level, Entity who, Vec3 from, Vec3 to) {
        net.minecraft.core.BlockPos firstGlass = null;
        Vec3 start = from;
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0e-4) {
            return new Look(true, null);
        }
        dir = dir.scale(1.0 / len);
        for (int i = 0; i < 10; i++) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(start, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, who));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return new Look(true, firstGlass);
            }
            net.minecraft.core.BlockPos bp = hit.getBlockPos();
            if (!isGlass(level.getBlockState(bp))) {
                return new Look(false, firstGlass);
            }
            if (firstGlass == null) {
                firstGlass = bp;
            }
            start = hit.getLocation().add(dir.scale(0.06));
            if (start.distanceToSqr(from) >= len * len) {
                return new Look(true, firstGlass);
            }
        }
        return new Look(false, firstGlass);
    }

    static boolean canSee(CiudadanoEntity mob, Entity target) {
        return look(mob.level(), mob, mob.getEyePosition(), target.getEyePosition()).clear();
    }

    static void breakGlass(CiudadanoEntity mob, net.minecraft.core.BlockPos pos, boolean shot) {
        var level = mob.level();
        if (!(level instanceof ServerLevel sl) || !isGlass(level.getBlockState(pos))) {
            return;
        }
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (shot) {
            level.playSound(null, mob.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.2f, 0.7f);
        }
        sl.destroyBlock(pos, false, mob);
    }

    /** Sin arma: rompe a golpes el cristal que le separa de su objetivo. */
    static final class BreakGlassGoal extends Goal {
        private final CiudadanoEntity mob;
        private int cooldown;

        BreakGlassGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.noneOf(Flag.class));
        }

        private net.minecraft.core.BlockPos glass() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || !mob.wantsToFight() || mob.carriesGun()) {
                return null;
            }
            Look l = look(mob.level(), mob, mob.getEyePosition(), t.getEyePosition());
            if (l.glass() == null) {
                return null;
            }
            return l.glass().getCenter().distanceToSqr(mob.getEyePosition()) < 3.6 * 3.6 ? l.glass() : null;
        }

        @Override
        public boolean canUse() {
            return mob.tickCount % 5 == 0 && glass() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return glass() != null;
        }

        @Override
        public void start() {
            this.cooldown = 8;
        }

        @Override
        public void tick() {
            if (--this.cooldown <= 0) {
                var g = glass();
                if (g != null) {
                    breakGlass(mob, g, false);
                }
                this.cooldown = 12;
            }
        }
    }

    /** Sube escaleras de mano hacia un objetivo situado más arriba. */
    static final class ClimbLadderGoal extends Goal {
        private final CiudadanoEntity mob;
        private net.minecraft.core.BlockPos ladder;
        private int giveUp;

        ClimbLadderGoal(CiudadanoEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        private boolean wanted() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.wantsToFight() && t.getY() - mob.getY() > 1.8;
        }

        @Override
        public boolean canUse() {
            if (mob.tickCount % 10 != 0 || !wanted()) {
                return false;
            }
            if (mob.onClimbable()) {
                this.ladder = mob.blockPosition();
                return true;
            }
            net.minecraft.core.BlockPos base = mob.blockPosition();
            net.minecraft.core.BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        net.minecraft.core.BlockPos p = base.offset(dx, dy, dz);
                        var st = mob.level().getBlockState(p);
                        if (st.is(net.minecraft.world.level.block.Blocks.LADDER)) {
                            double d = p.distSqr(base);
                            if (d < bd) {
                                bd = d;
                                best = p;
                            }
                        }
                    }
                }
            }
            this.ladder = best;
            return best != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.ladder != null && this.giveUp < 400 && wanted() || mob.onClimbable() && wanted();
        }

        @Override
        public void start() {
            this.giveUp = 0;
        }

        @Override
        public void stop() {
            this.ladder = null;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.giveUp++;
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0f, 30.0f);
            if (mob.onClimbable()) {
                mob.getJumpControl().jump();                       // en escalera, saltar = trepar
                var st = mob.level().getBlockState(mob.blockPosition());
                net.minecraft.core.Direction wall = st.hasProperty(net.minecraft.world.level.block.LadderBlock.FACING)
                        ? st.getValue(net.minecraft.world.level.block.LadderBlock.FACING).getOpposite()
                        : net.minecraft.core.Direction.NORTH;
                Vec3 c = mob.blockPosition().getCenter();
                boolean top = !mob.level().getBlockState(mob.blockPosition().above()).is(net.minecraft.world.level.block.Blocks.LADDER);
                if (top || mob.getY() + 0.5 >= t.getY()) {
                    // llegó arriba: salir de la escalera hacia el objetivo
                    mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
                    if (top) {
                        Vec3 out = Vec3.atLowerCornerOf(wall.getOpposite().getNormal()).scale(0.12);
                        mob.setDeltaMovement(mob.getDeltaMovement().add(out.x, 0.0, out.z));
                    }
                } else {
                    mob.getMoveControl().setWantedPosition(c.x + wall.getStepX() * 0.45, c.y + 1.0,
                            c.z + wall.getStepZ() * 0.45, 0.6);
                }
                return;
            }
            net.minecraft.core.BlockPos l = this.ladder;
            if (l == null) {
                return;
            }
            var st = mob.level().getBlockState(l);
            if (!st.is(net.minecraft.world.level.block.Blocks.LADDER)) {
                this.ladder = null;
                return;
            }
            Vec3 c = l.getCenter();
            double hd = Math.hypot(mob.getX() - c.x, mob.getZ() - c.z);
            if (hd > 1.6) {
                if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
                    net.minecraft.core.Direction f = st.getValue(net.minecraft.world.level.block.LadderBlock.FACING);
                    net.minecraft.core.BlockPos front = l.relative(f);
                    mob.getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 1.2);
                }
            } else {
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(c.x, l.getY(), c.z, 1.0);   // entra en la celda de la escalera
            }
        }
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
            if (!mob.carriesGun() && !mob.isThief()) {
                return false;
            }
            Player p = mob.level().getNearestPlayer(mob, 64.0);
            if (p == null || p.isSpectator()) {
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
            return mob.distanceToSqr(t) < max * max && CiudadanoGoals.canSee(mob, t);
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
            if (this.shotCooldown <= 0 && CiudadanoGoals.canSee(mob, target)) {
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
            Look lk = look(level, mob, eye, aim);
            if (lk.glass() != null) {                    // primero rompe el cristal de un disparo
                CiudadanoGoals.breakGlass(mob, lk.glass(), true);
                return;
            }
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
