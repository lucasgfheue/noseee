package com.example.armasreales;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * «PRISMA» (ficticio): rifle de precisión refractaria con cámara prismática violeta. Munición REFRACT-5.
 * Precisión 97/100.
 *  - Clic izquierdo: disparo de precisión (1 cápsula), rayo violeta recto.
 *  - Agachado + clic izquierdo, habilidad REFRACCIÓN (2 cápsulas): el rayo viaja hasta un prisma de energía
 *    (donde impacta o a 16 bloques) y se divide en 7 rayos de colores que se abren en abanico.
 */
public class PrismRifleItem extends Item implements Gun {
    private static final double RANGE = 150.0;
    private static final float DAMAGE = 12.0f;
    private static final int COOLDOWN = 25;
    private static final double HIP_SPREAD = 0.012;        // precisión 97
    private static final double AIM_SPREAD = 0.0005;

    private static final int SPECIAL_COST = 2;
    private static final int SPECIAL_COOLDOWN = 55;
    private static final double PRISM_DISTANCE = 16.0;
    private static final double SPLIT_ANGLE = 0.10;        // apertura del abanico (rad)
    private static final double SPLIT_RANGE = 70.0;
    private static final float SPLIT_DAMAGE = 5.0f;

    private static final Vector3f[] COLORS = {
            new Vector3f(1.0f, 0.15f, 0.15f), new Vector3f(1.0f, 0.55f, 0.1f), new Vector3f(1.0f, 0.95f, 0.2f),
            new Vector3f(0.2f, 1.0f, 0.35f), new Vector3f(0.2f, 0.9f, 1.0f), new Vector3f(0.3f, 0.35f, 1.0f),
            new Vector3f(0.75f, 0.3f, 1.0f)
    };

    public PrismRifleItem(Properties properties) {
        super(properties);
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
        return 0.4f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        boolean special = player.isShiftKeyDown();
        int cost = special ? SPECIAL_COST : 1;
        boolean creative = player.getAbilities().instabuild;
        if (!creative && countAmmo(player) < cost) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }
        if (special) {
            refract(level, player);
        } else {
            shoot(level, player);
        }
        if (!creative) {
            takeAmmo(player, cost);
            gun.hurtAndBreak(cost, player, EquipmentSlot.MAINHAND);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, special ? SPECIAL_COOLDOWN : COOLDOWN);
        return false;
    }

    private static int countAmmo(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.REFRACT_5.get())) total += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.REFRACT_5.get())) total += s.getCount();
        }
        return total;
    }

    private static void takeAmmo(Player player, int n) {
        int left = n;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(ModItems.REFRACT_5.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left > 0 && s.is(ModItems.REFRACT_5.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
    }

    private void shoot(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);
        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().playerAttack(player), DAMAGE);
        }
        Vec3 end = eye.add(look.scale(hit.distance()));
        BeamEntity.flash(level, player, BeamEntity.STYLE_VIOLET, (float) hit.distance(), 6);
        level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 5, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.5f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.4f, 0.8f);
    }

    /** REFRACCIÓN: el rayo se divide en 7 rayos de colores al atravesar el prisma de energía. */
    private void refract(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit first = Bullets.trace(level, player, eye, look, PRISM_DISTANCE);
        double d = Math.max(1.0, first.distance() - (first.entity() == null ? 0.4 : 0.0));
        if (first.entity() != null) {
            first.entity().invulnerableTime = 0;
            first.entity().hurt(level.damageSources().playerAttack(player), SPLIT_DAMAGE);
        }
        Vec3 prism = eye.add(look.scale(d));

        // Tramo hasta el prisma: línea violeta
        DustParticleOptions violet = new DustParticleOptions(COLORS[6], 1.0f);
        for (double t = 1.0; t < d; t += 0.6) {
            Vec3 p = eye.add(look.scale(t));
            level.sendParticles(violet, p.x, p.y - 0.1, p.z, 1, 0, 0, 0, 0);
        }
        // El prisma: destello y ráfaga de partículas brillantes
        level.sendParticles(ParticleTypes.FLASH, prism.x, prism.y, prism.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, prism.x, prism.y, prism.z, 14, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(new DustParticleOptions(COLORS[6], 2.0f), prism.x, prism.y, prism.z, 10, 0.2, 0.2, 0.2, 0.0);

        // Base ortonormal perpendicular a la dirección
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize();
        Vec3 v = look.cross(u).normalize();

        for (int i = 0; i < 7; i++) {
            Vec3 dir;
            if (i == 0) {
                dir = look;
            } else {
                double a = 2 * Math.PI * (i - 1) / 6.0;
                dir = look.add(u.scale(Math.cos(a) * SPLIT_ANGLE)).add(v.scale(Math.sin(a) * SPLIT_ANGLE)).normalize();
            }
            Bullets.Hit h = Bullets.trace(level, player, prism, dir, SPLIT_RANGE);
            if (h.entity() != null && h.entity() != first.entity()) {
                h.entity().invulnerableTime = 0;
                h.entity().hurt(level.damageSources().playerAttack(player), SPLIT_DAMAGE);
            } else if (h.entity() != null) {
                h.entity().invulnerableTime = 0;
                h.entity().hurt(level.damageSources().playerAttack(player), SPLIT_DAMAGE);
            }
            DustParticleOptions col = new DustParticleOptions(COLORS[i], 1.1f);
            for (double t = 0.5; t < h.distance(); t += 1.0) {
                Vec3 p = prism.add(dir.scale(t));
                level.sendParticles(col, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            Vec3 e = prism.add(dir.scale(h.distance()));
            level.sendParticles(col, e.x, e.y, e.z, 3, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, e.x, e.y, e.z, 1, 0.05, 0.05, 0.05, 0.02);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 1.6f, 1.2f);
        level.playSound(null, prism.x, prism.y, prism.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.8f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.2f, 1.1f);
    }
}
