package com.example.armasreales;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque «Sigilo» (ficticio): silueta baja y furtiva. Mismo tamaño de base y mecánicas que los otros tanques
 * (conducción, cañón de TNT, invulnerable salvo a explosiones de TNT).
 *  - Habilidad CAMUFLAJE: tras 2 s completamente quieto con conductor, el casco se vuelve translúcido con
 *    distorsiones azuladas, las luces se apagan y salen las cámaras laterales. Mientras dura, el conductor es
 *    invisible, los enemigos a más de 8 bloques pierden su objetivo y los hostiles a menos de 24 quedan marcados
 *    (brillan) gracias a las cámaras.
 *  - Se rompe al moverse, al disparar o al bajarse el conductor; tras disparar tarda 3 s en poder volver a entrar.
 *  - No cambia de color con tintes.
 */
public class StealthTankEntity extends TankEntity {
    private static final int STILL_TICKS_NEEDED = 40;
    private static final int FIRE_LOCKOUT = 60;
    private static final double ENTER_SPEED_SQ = 0.0009;      // casi parado
    private static final double BREAK_SPEED_SQ = 0.0036;      // al moverse de verdad se rompe
    private static final double SCAN_RANGE = 24.0;

    private static final EntityDataAccessor<Boolean> DATA_STEALTH =
            SynchedEntityData.defineId(StealthTankEntity.class, EntityDataSerializers.BOOLEAN);

    /** Progreso suave del camuflaje (0 = visible, 1 = translúcido): lo usa el renderizador. */
    public float stealthFade, stealthFadeO;

    private int stillTicks = 0;
    private long lastFire = -1000L;

    public StealthTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STEALTH, false);
    }

    public boolean isStealth() {
        return this.entityData.get(DATA_STEALTH);
    }

    private void setStealth(boolean on) {
        if (this.isStealth() == on) {
            return;
        }
        this.entityData.set(DATA_STEALTH, on);
        this.level().playSound(null, this.blockPosition(),
                on ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.9f, on ? 1.8f : 1.2f);
        this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8f, on ? 0.6f : 1.6f);
    }

    // Geometría propia del cañón (la torreta es baja): debe coincidir con StealthTankRenderer
    @Override
    protected double pivotY() {
        return 0.46 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.25 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 1.85 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return 2;
    }

    @Override
    public void tick() {
        super.tick();
        this.stealthFadeO = this.stealthFade;
        this.stealthFade = Mth.clamp(this.stealthFade + (this.isStealth() ? 0.05f : -0.08f), 0.0f, 1.0f);

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity rider = this.getControllingPassenger();
        Vec3 d = this.getDeltaMovement();
        double speedSq = d.x * d.x + d.z * d.z;
        boolean lockedOut = level.getGameTime() - this.lastFire < FIRE_LOCKOUT;

        if (rider == null || lockedOut || speedSq > BREAK_SPEED_SQ) {
            this.stillTicks = 0;
            this.setStealth(false);
        } else if (!this.isStealth()) {
            this.stillTicks = speedSq < ENTER_SPEED_SQ ? this.stillTicks + 1 : 0;
            if (this.stillTicks >= STILL_TICKS_NEEDED) {
                this.setStealth(true);
            }
        }

        if (this.isStealth() && rider != null) {
            rider.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            if (this.tickCount % 20 == 0) {
                this.scan(level, rider);
            }
        }
    }

    /** Cámaras desplegadas: marcan a los hostiles cercanos y hacen que los lejanos pierdan al conductor. */
    private void scan(ServerLevel level, LivingEntity rider) {
        for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(SCAN_RANGE),
                x -> x instanceof Enemy && x instanceof LivingEntity && x.isAlive())) {
            LivingEntity le = (LivingEntity) e;
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            if (le instanceof Mob mob && mob.getTarget() == rider && mob.distanceTo(this) > 8.0f) {
                mob.setTarget(null);
            }
        }
    }

    @Override
    public void fireCannon(ServerPlayer player) {
        int before = this.cannonCooldown;
        super.fireCannon(player);
        if (this.cannonCooldown > before) {                  // sí disparó
            this.lastFire = this.level().getGameTime();
            this.stillTicks = 0;
            this.setStealth(false);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_SIGILO.get();
    }
}
