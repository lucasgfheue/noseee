package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque «Leviatán Mk II» (ficticio, Proyecto X): evolución del Leviatán original, más largo y más alto, con
 * arquitectura dividida en secciones (frontal escalonado, torreta central de asedio y sección trasera elevada con el
 * reactor). Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a explosiones
 * de TNT).
 *  - Núcleo de asedio: el cañón electromagnético acumula energía solo (unos 15 s). Con la carga al 100 % el
 *    siguiente disparo es una SALVA DE ASEDIO: cinco obuses de TNT a la vez (gasta hasta cinco TNT).
 *  - Después el sistema queda SOBRECALENTADO durante 10 s: no puede disparar, avanza mucho más despacio, los
 *    radiadores se abren y sale vapor. Al enfriarse empieza a cargar de nuevo.
 *  - Mientras no está listo el núcleo dispara obuses normales (cadencia lenta).
 *  - No cambia de color con tintes.
 */
public class LeviathanTankEntity extends TankEntity {
    public static final int CHARGE_MAX = 100;
    private static final int CHARGE_EVERY = 3;              // +1 cada 3 ticks = 15 s para cargar
    private static final int OVERHEAT_TICKS = 200;
    private static final int RELOAD_TICKS = 50;
    private static final float OVERHEAT_SPEED = 0.35f;
    private static final double[] SALVO_OFFSETS = {-2.4, -1.2, 0.0, 1.2, 2.4};

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(LeviathanTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_OVERHEAT_UNTIL =
            SynchedEntityData.defineId(LeviathanTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(LeviathanTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SIEGE =
            SynchedEntityData.defineId(LeviathanTankEntity.class, EntityDataSerializers.LONG);

    public LeviathanTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 900.0)
                .add(Attributes.MOVEMENT_SPEED, 0.14);      // enorme y lento
    }

    @Override
    protected double seatTopY() {
        return 2.05 * SCALE;
    }

    @Override
    public int[] interiorAccent() {
        return new int[]{80, 200, 255};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
        builder.define(DATA_OVERHEAT_UNTIL, 0L);
        builder.define(DATA_LAST_SHOT, -1000L);
        builder.define(DATA_LAST_SIEGE, -1000L);
    }

    /** Carga del núcleo de asedio, de 0 a CHARGE_MAX. */
    public int getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    public boolean isOverheated() {
        return this.entityData.get(DATA_OVERHEAT_UNTIL) > this.level().getGameTime();
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    /** Igual, pero sólo cuenta las salvas de asedio (fogonazo y retroceso mayores). */
    public float ticksSinceSiege(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SIEGE)) + partialTick;
    }

    // Geometría del cañón (debe coincidir con LeviathanTankRenderer): eje a 1,9 de alto, torreta a 0,3 por delante del centro y cañón a 1,15 de su eje
    @Override
    protected double turretOffsetZ() {
        return 0.3 * SCALE;
    }

    @Override
    protected double pivotY() {
        return 1.9 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 1.15 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 3.2 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.4 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 6.8;                                    // casco de unos 14 de largo
    }

    @Override
    protected double crushHalfWidth() {
        return 3.0;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float speed = super.getRiddenSpeed(player);
        return this.isOverheated() ? speed * OVERHEAT_SPEED : speed;
    }

    // ------------------------------------------------------------------ Cañón y núcleo de asedio

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (this.isOverheated()) {
            level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.5f);
            this.cannonCooldown = 10;
            return;
        }
        long now = level.getGameTime();
        boolean siege = this.getCharge() >= CHARGE_MAX;
        if (siege) {
            int fired = 0;
            for (double off : SALVO_OFFSETS) {
                if (this.fireShell(player, off)) {
                    fired++;
                } else {
                    break;
                }
            }
            if (fired == 0) {
                this.cannonCooldown = 10;
                return;
            }
            this.entityData.set(DATA_LAST_SIEGE, now);
            this.entityData.set(DATA_CHARGE, 0);
            this.entityData.set(DATA_OVERHEAT_UNTIL, now + OVERHEAT_TICKS);
            this.cannonCooldown = OVERHEAT_TICKS;
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                    SoundSource.PLAYERS, 2.0f, 0.5f);
            level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.PLAYERS, 2.0f, 0.6f);
        } else {
            if (!this.fireShell(player, 0.0)) {
                this.cannonCooldown = 10;
                return;
            }
            this.cannonCooldown = RELOAD_TICKS;
        }
        this.entityData.set(DATA_LAST_SHOT, now);
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 muzzle = this.position().add(0.0, this.pivotY(), 0.0).add(Vec3.directionFromRotation(0.0f, this.getYRot()).scale(this.turretOffsetZ())).add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z,
                siege ? 40 : 10, 0.4, 0.4, 0.4, 0.3);
        if (siege) {
            level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.isOverheated()) {
            if (this.tickCount % 3 == 0) {
                // vapor por los radiadores de la torreta y el reactor trasero
                Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
                Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
                Vec3 turret = this.position().add(0.0, 2.1 * SCALE, 0.0).add(flat.scale(0.4 * SCALE));
                Vec3 rear = this.position().add(0.0, 2.8 * SCALE, 0.0).add(flat.scale(-2.7 * SCALE));
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = turret.add(side.scale(s * 1.1 * SCALE));
                    level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.2, 0.15, 0.2, 0.03);
                }
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, rear.x, rear.y, rear.z, 1, 0.5, 0.1, 0.5, 0.02);
            }
            if (this.tickCount % 20 == 0) {
                level.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 1.2f, 0.6f);
            }
        } else if (this.getCharge() < CHARGE_MAX && this.tickCount % CHARGE_EVERY == 0) {
            int charge = this.getCharge() + 1;
            this.entityData.set(DATA_CHARGE, charge);
            if (charge >= CHARGE_MAX) {
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.6f, 0.6f);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Leviatán Mk II no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_LEVIATAN.get();
    }
}
