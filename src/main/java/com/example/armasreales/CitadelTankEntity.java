package com.example.armasreales;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Tanque «Ciudadela» (ficticio): fortaleza sobre orugas larguísima, con un cañón electromagnético de longitud
 * extraordinaria. Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a
 * explosiones de TNT) y más pesado: más vida y más lento.
 *  - Clic izquierdo: empieza la CARGA. Se despliegan los estabilizadores (el tanque no puede moverse) y las
 *    bobinas del cañón se iluminan una a una desde la base hasta la boca; al completarse dispara.
 *  - Tras disparar, los estabilizadores se repliegan a los 2 s y el cañón necesita 4 s para volver a cargar.
 *  - No cambia de color con tintes.
 */
public class CitadelTankEntity extends TankEntity {
    public static final int CHARGE_TICKS = 36;
    private static final int RETRACT_DELAY = 40;
    private static final int RELOAD_TICKS = 80;

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(CitadelTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DEPLOYED =
            SynchedEntityData.defineId(CitadelTankEntity.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0f, 0.68f, 0.12f), 1.6f);

    /** Animación suave (cliente y servidor): estabilizadores y resplandor de las bobinas. */
    public float deploy, deployO, coilGlow, coilGlowO;

    private long retractAt = 0L;

    public CitadelTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.17);      // más pesado: más lento que el tanque base
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
        builder.define(DATA_DEPLOYED, false);
    }

    public int getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    public boolean isDeployed() {
        return this.entityData.get(DATA_DEPLOYED);
    }

    // Geometría del cañón (debe coincidir con CitadelTankRenderer): eje a 2,3 de alto y 0,6 por delante del centro
    @Override
    protected double pivotY() {
        return 2.3 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.6 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 3.4 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.4 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 4.5;
    }

    // ------------------------------------------------------------------ Conducción: bloqueada con los estabilizadores

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        if (this.isDeployed()) {
            return;                                    // anclado: no gira
        }
        super.tickRidden(player, travelVector);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        if (this.isDeployed()) {
            return Vec3.ZERO;                          // anclado: no avanza
        }
        return super.getRiddenInput(player, travelVector);
    }

    // ------------------------------------------------------------------ Cañón: carga, disparo y estabilizadores

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0 || this.getCharge() > 0) {
            return;
        }
        if (!player.getAbilities().instabuild && AmmoUtil.find(player, Items.TNT).isEmpty()) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            this.cannonCooldown = 10;
            return;
        }
        this.entityData.set(DATA_CHARGE, 1);
        if (!this.isDeployed()) {
            this.entityData.set(DATA_DEPLOYED, true);
            this.level().playSound(null, this.blockPosition(), SoundEvents.PISTON_EXTEND,
                    SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        this.retractAt = Long.MAX_VALUE;
    }

    @Override
    public void tick() {
        super.tick();
        this.deployO = this.deploy;
        this.deploy = Mth.clamp(this.deploy + (this.isDeployed() ? 0.07f : -0.07f), 0.0f, 1.0f);
        this.coilGlowO = this.coilGlow;
        this.coilGlow = this.getCharge() > 0 ? 0.0f : this.coilGlow * 0.8f;

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int charge = this.getCharge();
        if (charge > 0) {
            if (!(this.getControllingPassenger() instanceof ServerPlayer player)) {
                this.entityData.set(DATA_CHARGE, 0);                 // sin conductor: se cancela
                this.retractAt = level.getGameTime() + 10;
            } else {
                charge++;
                this.entityData.set(DATA_CHARGE, charge);
                if (charge % 4 == 0) {
                    level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 1.6f, 0.5f + 1.5f * charge / CHARGE_TICKS);
                }
                if (charge >= CHARGE_TICKS) {
                    this.entityData.set(DATA_CHARGE, 0);
                    this.coilGlow = 1.0f;
                    boolean fired = this.fireShell(player, 0.0);
                    this.cannonCooldown = fired ? RELOAD_TICKS : 10;
                    this.retractAt = level.getGameTime() + RETRACT_DELAY;
                    if (fired) {
                        this.muzzleBlast(level, player);
                    }
                }
            }
        }
        if (this.isDeployed() && this.getCharge() == 0 && level.getGameTime() >= this.retractAt) {
            this.entityData.set(DATA_DEPLOYED, false);
            level.playSound(null, this.blockPosition(), SoundEvents.PISTON_CONTRACT,
                    SoundSource.PLAYERS, 2.0f, 0.6f);
        }
    }

    private void muzzleBlast(ServerLevel level, ServerPlayer player) {
        Vec3 dir = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), MIN_PITCH, MAX_PITCH), player.getYRot());
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 muzzle = this.position().add(0.0, this.pivotY(), 0.0).add(flat.scale(this.pivotForward()))
                .add(dir.scale(this.barrelLength()));
        level.sendParticles(AMBER, muzzle.x, muzzle.y, muzzle.z, 24, 0.4, 0.4, 0.4, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 30, 0.5, 0.5, 0.5, 0.4);
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 3.0f, 1.2f);
        level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 2.0f, 0.7f);
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
        return ModItems.TANQUE_CIUDADELA.get();
    }
}
