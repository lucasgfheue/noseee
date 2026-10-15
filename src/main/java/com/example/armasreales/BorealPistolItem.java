package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * BÓREAS (experimental, ficticia): pistola criogénica con cápsulas FROST-Cell. Precisión 87/100.
 * Habilidad: cada impacto acumula frío en el objetivo (hasta 5 acumulaciones, que se pierden si pasa un rato
 * sin recibir impactos); cuanto más frío acumulado, más lo ralentiza, de forma temporal y sin inmovilizarlo.
 */
public class BorealPistolItem extends Item implements Gun {
    private static final double RANGE = 90.0;
    private static final float DAMAGE = 4.5f;
    private static final int COOLDOWN = 8;
    /** Precisión 87/100: dispersión muy baja. */
    private static final double HIP_SPREAD = 0.022;
    private static final double AIM_SPREAD = 0.003;

    private static final int MAX_COLD = 5;
    private static final int COLD_MEMORY_TICKS = 120;     // 6 s sin impactos y el frío acumulado se pierde
    private static final int SLOW_TICKS = 70;

    public BorealPistolItem(Properties properties) {
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
        return 0.85f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.FROST_CELL.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.8f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = Bullets.spread(level, player.getLookAngle(), player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD);
        Bullets.Hit hit = Bullets.trace(level, player, eye, look, RANGE);

        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;
            hit.entity().hurt(level.damageSources().playerAttack(player), DAMAGE);
            if (hit.entity() instanceof LivingEntity living) {
                addCold(level, living);
            }
        }

        // Vapor helado al activarse el núcleo + estela de copos
        Vec3 muzzle = eye.add(look.scale(0.9));
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y - 0.12, muzzle.z, 8, 0.06, 0.06, 0.06, 0.03);
        level.sendParticles(ParticleTypes.SNOWFLAKE, muzzle.x, muzzle.y - 0.12, muzzle.z, 10, 0.1, 0.1, 0.1, 0.05);
        for (double d = 2.0; d < hit.distance(); d += 1.5) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y - 0.1, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (((int) d) % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y - 0.1, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_STEP,
                SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.25f, 2.0f);

        player.swing(InteractionHand.MAIN_HAND, true);
        if (!creative) {
            ammo.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return false;
    }

    /** Acumula una marca de frío en el objetivo y aplica la ralentización correspondiente. */
    private static void addCold(ServerLevel level, LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        long now = level.getGameTime();
        int cold = data.getInt("BoreasFrio");
        if (now > data.getLong("BoreasHasta")) {
            cold = 0;                                   // el frío anterior ya se disipó
        }
        cold = Math.min(MAX_COLD, cold + 1);
        data.putInt("BoreasFrio", cold);
        data.putLong("BoreasHasta", now + COLD_MEMORY_TICKS);

        // Más frío = más lento, pero nunca inmovilizado (máximo Lentitud III)
        int amp = Math.min(2, cold - 1);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, amp));
        target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 20, cold * 35));

        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 6 + cold * 3, 0.3, 0.4, 0.3, 0.04);
        if (cold == MAX_COLD) {
            level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 12, 0.35, 0.5, 0.35, 0.06);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }
}
