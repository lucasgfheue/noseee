package com.example.armasreales;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque «Apocalipsis» (ficticio, Proyecto X): el más grande de todos. Combina la anchura de un acorazado y la
 * longitud de un rompemundos con un reactor central elevado, seis módulos de orugas independientes y una torreta
 * monumental con cañón de energía y dos módulos secundarios. Mismas mecánicas base que los otros tanques (conducción,
 * obús de TNT, invulnerable salvo a explosiones de TNT).
 *  - El reactor acumula energía solo (unos 30 s). Con la carga al 100 %, el siguiente disparo activa el
 *    PROTOCOLO APOCALIPSIS: durante 10 s recibe sólo el 25 % del daño y cada disparo es una andanada de tres
 *    obuses a la vez (cañón principal y los dos módulos secundarios; gasta hasta tres TNT), con recarga muy corta.
 *    Las líneas luminosas del casco se encienden en secuencia y las cubiertas del reactor se abren.
 *  - Al terminar, el reactor entra en ENFRIAMIENTO durante 15 s: recibe un 50 % más de daño, avanza al 60 % de
 *    velocidad y sólo dispara un obús normal con recarga lenta. Después empieza a cargarse de nuevo.
 *  - No cambia de color con tintes.
 */
public class ApocalypseTankEntity extends TankEntity {
    public static final int CHARGE_MAX = 100;
    private static final int CHARGE_EVERY = 6;               // +1 cada 6 ticks = 30 s para cargar
    public static final int ACTIVE_TICKS = 200;
    public static final int COOL_TICKS = 300;
    private static final int NORMAL_RELOAD = 60;
    private static final int COOL_RELOAD = 90;
    private static final int ACTIVE_RELOAD = 14;
    private static final float ACTIVE_DAMAGE = 0.25f;
    private static final float COOL_DAMAGE = 1.5f;
    private static final float COOL_SPEED = 0.6f;
    private static final double[] VOLLEY = {0.0, -2.9, 2.9};    // cañón principal y módulos secundarios

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(ApocalypseTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ACTIVE_UNTIL =
            SynchedEntityData.defineId(ApocalypseTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_COOL_UNTIL =
            SynchedEntityData.defineId(ApocalypseTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(ApocalypseTankEntity.class, EntityDataSerializers.LONG);

    private boolean wasActive = false;

    public ApocalypseTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.13);      // el más grande y pesado
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
        builder.define(DATA_ACTIVE_UNTIL, 0L);
        builder.define(DATA_COOL_UNTIL, 0L);
        builder.define(DATA_LAST_SHOT, -1000L);
    }

    /** Carga del reactor, de 0 a CHARGE_MAX. */
    public int getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    /** Protocolo Apocalipsis en marcha. */
    public boolean isActive() {
        return this.entityData.get(DATA_ACTIVE_UNTIL) > this.level().getGameTime();
    }

    /** Reactor enfriándose tras el protocolo (menos defensa). */
    public boolean isCooling() {
        return this.entityData.get(DATA_COOL_UNTIL) > this.level().getGameTime();
    }

    /** Progreso del protocolo activo en ticks (con decimales); sólo tiene sentido si isActive(). */
    public float activeTicks(float partialTick) {
        long until = this.entityData.get(DATA_ACTIVE_UNTIL);
        return (this.level().getGameTime() + partialTick) - (until - ACTIVE_TICKS);
    }

    /** Apertura de las cubiertas del reactor, de 0 a 1; sirve en el cliente. */
    public float openFraction(float partialTick) {
        long until = this.entityData.get(DATA_ACTIVE_UNTIL);
        float now = this.level().getGameTime() + partialTick;
        float start = until - ACTIVE_TICKS;
        if (now < start) {
            return 0.0f;
        }
        if (now < until) {
            return Mth.clamp((now - start) / 12.0f, 0.0f, 1.0f);
        }
        return Mth.clamp(1.0f - (now - until) / 30.0f, 0.0f, 1.0f);
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    // Geometría del cañón (debe coincidir con ApocalypseTankRenderer): torreta a 0,3 por delante del centro, eje a 2,2 de alto y 1,2 por
    // delante del centro de la torreta
    @Override
    protected double turretOffsetZ() {
        return 0.3 * SCALE;
    }

    @Override
    protected double pivotY() {
        return 2.2 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 1.2 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 4.0 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(3.0 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 7.4;                                    // casco de unos 15 de largo
    }

    @Override
    protected double crushHalfWidth() {
        return 4.0;                                    // unos 8,4 de ancho con los faldones
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float speed = super.getRiddenSpeed(player);
        return this.isCooling() ? speed * COOL_SPEED : speed;
    }

    // ------------------------------------------------------------------ Daño

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float factor = 1.0f;
        if (this.isActive()) {
            factor = ACTIVE_DAMAGE;
        } else if (this.isCooling()) {
            factor = COOL_DAMAGE;
        }
        return super.hurt(source, amount * factor);
    }

    // ------------------------------------------------------------------ Cañón y protocolo

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        boolean active = this.isActive();
        boolean start = !active && !this.isCooling() && this.getCharge() >= CHARGE_MAX;
        if (active || start) {
            int fired = 0;
            for (double off : VOLLEY) {
                if (this.fireShell(player, off)) {
                    fired++;
                } else if (off == 0.0) {
                    break;                              // sin munición: ni el principal sale
                }
            }
            if (fired == 0) {
                this.cannonCooldown = 10;
                return;
            }
            this.cannonCooldown = ACTIVE_RELOAD;
            if (start) {
                this.entityData.set(DATA_ACTIVE_UNTIL, now + ACTIVE_TICKS);
                this.entityData.set(DATA_CHARGE, 0);
                this.wasActive = true;
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.6f, 0.6f);
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
                Vec3 c = this.position().add(0.0, 2.4 * SCALE, 0.0)
                        .add(Vec3.directionFromRotation(0.0f, this.getYRot()).scale(-2.65 * SCALE));
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 40, 1.0, 0.6, 1.0, 0.1);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 1.0, 0.6, 1.0, 0.4);
                level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            }
        } else {
            if (!this.fireShell(player, 0.0)) {
                this.cannonCooldown = 10;
                return;
            }
            this.cannonCooldown = this.isCooling() ? COOL_RELOAD : NORMAL_RELOAD;
        }
        this.entityData.set(DATA_LAST_SHOT, now);
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 m = this.position().add(0.0, this.pivotY(), 0.0)
                .add(Vec3.directionFromRotation(0.0f, this.getYRot()).scale(this.turretOffsetZ())).add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, active || start ? 30 : 10, 0.4, 0.4, 0.4, 0.3);
        level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 10, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        boolean active = this.isActive();
        Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 reactor = this.position().add(0.0, 2.9 * SCALE, 0.0).add(flat.scale(-2.65 * SCALE));
        if (this.wasActive && !active) {
            // fin del protocolo: el reactor entra en enfriamiento
            this.wasActive = false;
            this.entityData.set(DATA_COOL_UNTIL, now + COOL_TICKS);
            level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.PLAYERS, 2.0f, 0.4f);
            level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE,
                    SoundSource.PLAYERS, 2.0f, 0.4f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, reactor.x, reactor.y, reactor.z, 30, 1.0, 0.5, 1.0, 0.05);
        }
        if (active) {
            if (this.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, reactor.x, reactor.y, reactor.z, 3, 0.9, 0.4, 0.9, 0.2);
                level.sendParticles(ParticleTypes.FLAME, reactor.x, reactor.y, reactor.z, 1, 0.6, 0.2, 0.6, 0.03);
            }
            if (this.tickCount % 20 == 0) {
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_AMBIENT,
                        SoundSource.PLAYERS, 1.6f, 0.5f);
            }
        } else if (this.isCooling()) {
            if (this.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, reactor.x, reactor.y + 0.5, reactor.z,
                        1, 0.8, 0.1, 0.8, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, reactor.x, reactor.y, reactor.z, 2, 0.8, 0.2, 0.8, 0.02);
            }
        } else if (this.getCharge() < CHARGE_MAX && this.tickCount % CHARGE_EVERY == 0) {
            int charge = this.getCharge() + 1;
            this.entityData.set(DATA_CHARGE, charge);
            if (charge >= CHARGE_MAX) {
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.PLAYERS, 1.6f, 0.5f);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Apocalipsis no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_APOCALIPSIS.get();
    }
}
