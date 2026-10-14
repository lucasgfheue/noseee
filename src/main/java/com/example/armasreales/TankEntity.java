package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque: casco de 3 de largo x 2 de ancho x 2 de alto.
 *
 * - Se monta con clic derecho. W/S avanzan y retroceden, A/D giran el casco.
 * - Velocidad igual a la de un cerdo montado (atributo 0.25 y la misma fórmula que el cerdo).
 * - Sube escalones de 1 bloque (atributo STEP_HEIGHT = 1.0).
 * - La torreta y el cañón siguen hacia donde mira el conductor; clic izquierdo dispara.
 * - Las ruedas giran según lo que avanza el tanque (sólo visual, se calcula en el cliente).
 */
public class TankEntity extends Mob {
    /** Giro del casco con A/D, en grados por tick. */
    private static final float TURN_SPEED = 3.0f;

    /** Límites de elevación del cañón (en grados; negativo = hacia arriba). */
    public static final float MIN_PITCH = -25.0f;
    public static final float MAX_PITCH = 8.0f;

    /** Dónde está el eje del cañón respecto al tanque (debe coincidir con TankRenderer). */
    public static final double PIVOT_Y = 1.24;
    public static final double PIVOT_FORWARD = 0.72;
    public static final double BARREL_LENGTH = 1.4;

    private static final float SHELL_SPEED = 3.2f;
    private static final int COOLDOWN_TICKS = 40;

    /** Radio de las ruedas y separación entre orugas (para animarlas), en bloques. */
    private static final float WHEEL_RADIUS = 0.34f;
    private static final float HALF_GAUGE = 0.8f;

    // Ángulos de las ruedas (grados), lado izquierdo y derecho. Sólo se usan en el cliente.
    public float wheelAngleL, wheelAngleR, wheelAngleLO, wheelAngleRO;

    private int cannonCooldown = 0;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)   // igual que el cerdo
                .add(Attributes.STEP_HEIGHT, 1.0)       // sube 1 bloque
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ------------------------------------------------------------------ Conducción

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        // A/D giran el casco (xxa > 0 = izquierda = el ángulo baja)
        this.setRot(this.getYRot() - player.xxa * TURN_SPEED, 0.0f);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float forward = player.zza;
        if (forward < 0.0f) {
            forward *= 0.5f;   // marcha atrás más lenta
        }
        return new Vec3(0.0, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        // Exactamente la fórmula del cerdo montado
        return (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.225);
    }

    // ------------------------------------------------------------------ Tick: ruedas y cañón

    @Override
    public void tick() {
        super.tick();
        if (this.cannonCooldown > 0) {
            this.cannonCooldown--;
        }
        if (this.level().isClientSide) {
            this.updateWheels();
        }
    }

    /** Las ruedas giran según el avance a lo largo del casco y según el giro sobre sí mismo. */
    private void updateWheels() {
        this.wheelAngleLO = this.wheelAngleL;
        this.wheelAngleRO = this.wheelAngleR;

        double yawRad = Math.toRadians(this.getYRot());
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double forward = dx * -Math.sin(yawRad) + dz * Math.cos(yawRad);   // avance con signo

        // Girar a la derecha (el ángulo sube): la oruga izquierda avanza y la derecha retrocede
        double turn = Math.toRadians(Mth.wrapDegrees(this.getYRot() - this.yRotO)) * HALF_GAUGE;

        float toDegrees = (float) (180.0 / Math.PI) / WHEEL_RADIUS;
        this.wheelAngleL += (float) (forward + turn) * toDegrees;
        this.wheelAngleR += (float) (forward - turn) * toDegrees;
    }

    // ------------------------------------------------------------------ Cañón

    /** Dispara el cañón (lo llama el servidor cuando el conductor pulsa el clic izquierdo). */
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, Items.TNT);

        if (ammo.isEmpty() && !creative) {
            level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            this.cannonCooldown = 10;
            return;
        }

        float pitch = Mth.clamp(player.getXRot(), MIN_PITCH, MAX_PITCH);
        float yaw = player.getYRot();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 pivot = this.position().add(0.0, PIVOT_Y, 0.0).add(flat.scale(PIVOT_FORWARD));
        Vec3 muzzle = pivot.add(dir.scale(BARREL_LENGTH));

        TntProjectile shell = new TntProjectile(level, player);
        shell.setPos(muzzle.x, muzzle.y - shell.getBbHeight() / 2.0, muzzle.z);
        shell.shoot(dir.x, dir.y, dir.z, SHELL_SPEED, 0.0f);
        level.addFreshEntity(shell);

        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.1, 0.1, 0.1, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.03);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2.0f, 0.4f);

        if (!creative) {
            ammo.shrink(1);
        }
        this.cannonCooldown = COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------ Daño, recogida y otros

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        // Un jugador que no lo está conduciendo lo golpea para recogerlo
        if (source.getEntity() instanceof Player player && !this.hasPassenger(player)) {
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(ModItems.TANQUE.get());
            }
            this.discard();
            return true;
        }
        // Sólo /kill y el vacío pueden destruirlo
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
