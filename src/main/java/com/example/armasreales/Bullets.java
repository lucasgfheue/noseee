package com.example.armasreales;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Trazado de balas instantáneas (sin proyectil) compartido por el AK-47 y la escopeta. */
public final class Bullets {
    private Bullets() {}

    /** Punto final del disparo y la entidad alcanzada (puede ser null). */
    public record Hit(Vec3 end, Entity entity, double distance) {}

    public static Hit trace(ServerLevel level, Player player, Vec3 eye, Vec3 dir, double range) {
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = new AABB(eye, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, player, eye, end, box, e -> !e.isSpectator() && e.isPickable());
        Entity target = null;
        if (entityHit != null) {
            target = entityHit.getEntity();
            end = entityHit.getLocation();
        }
        return new Hit(end, target, eye.distanceTo(end));
    }

    /** Dirección con dispersión gaussiana alrededor de look. */
    public static Vec3 spread(ServerLevel level, Vec3 look, double spread) {
        if (spread <= 0.0) {
            return look;
        }
        return look.add(level.random.nextGaussian() * spread,
                level.random.nextGaussian() * spread,
                level.random.nextGaussian() * spread).normalize();
    }
}
