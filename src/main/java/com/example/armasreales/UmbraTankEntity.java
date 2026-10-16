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
 * Tanque «Umbra» (ficticio): chasis intermedio de titanio negro con cerámica oscura, torreta compacta y asimétrica
 * (cañón energético a la derecha, sensores y disipadores a la izquierda) y un reactor violeta en la parte trasera.
 * Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a explosiones de TNT).
 *  - Clic izquierdo: el reactor acumula energía (su centro se vuelve blanco) y al completarse dispara el cañón.
 *  - Cada disparo calienta el reactor. Con tres disparos seguidos se SOBRECARGA: los paneles de refrigeración se abren
 *    como pétalos, las juntas brillan en magenta, el tanque emite vapor y el cañón no puede disparar unos segundos.
 *  - Si se deja de disparar, el reactor se enfría solo y los pétalos se cierran.
 *  - No cambia de color con tintes.
 */
public class UmbraTankEntity extends TankEntity {
    public static final int CHARGE_TICKS = 18;
    public static final int HEAT_MAX = 100;
    private static final int HEAT_PER_SHOT = 34;       // tres disparos seguidos = sobrecarga
    public static final int OVERLOAD_TICKS = 100;
    private static final int RELOAD_TICKS = 20;
    private static final int COOL_DELAY = 50;          // ticks sin disparar antes de que empiece a enfriarse

    /** Posición del cañón (debe coincidir con UmbraTankRenderer): a la derecha del eje de la torreta. */
    public static final double LATERAL = 0.55 * SCALE;

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(UmbraTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEAT =
            SynchedEntityData.defineId(UmbraTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_OVERLOAD_UNTIL =
            SynchedEntityData.defineId(UmbraTankEntity.class, EntityDataSerializers.LONG);

    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65f, 0.3f, 1.0f), 1.5f);
    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.8f), 1.3f);

    /** Animación suave (cliente y servidor): apertura de los pétalos y destello del reactor tras el disparo. */
    public float petals, petalsO, flash, flashO;

    private int prevCharge = 0;
    private long lastShot = 0L;

    public UmbraTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 260.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27);
    }

    @Override
    public int[] interiorAccent() {
        return new int[]{170, 60, 255};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
        builder.define(DATA_HEAT, 0);
        builder.define(DATA_OVERLOAD_UNTIL, 0L);
    }

    public int getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    /** Calor del reactor, de 0 a HEAT_MAX. */
    public int getHeat() {
        return this.entityData.get(DATA_HEAT);
    }

    public boolean isOverloaded() {
        return this.entityData.get(DATA_OVERLOAD_UNTIL) > this.level().getGameTime();
    }

    // Geometría del cañón (debe coincidir con UmbraTankRenderer): eje a 1,04 de alto y 0,1 por delante del centro
    @Override
    protected double pivotY() {
        return 1.04 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.1 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 1.8 * SCALE;
    }

    // ------------------------------------------------------------------ Cañón: carga, disparo y sobrecarga

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0 || this.getCharge() > 0) {
            return;
        }
        boolean noAmmo = !player.getAbilities().instabuild && AmmoUtil.find(player, Items.TNT).isEmpty();
        if (this.isOverloaded() || noAmmo) {
            this.level().playSound(null, this.blockPosition(), this.isOverloaded()
                            ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            this.cannonCooldown = 10;
            return;
        }
        this.entityData.set(DATA_CHARGE, 1);
    }

    @Override
    public void tick() {
        super.tick();

        // Animación (ambos lados): los pétalos se abren con el calor y del todo en la sobrecarga
        int chargeNow = this.getCharge();
        this.flashO = this.flash;
        this.flash = (this.prevCharge > 0 && chargeNow == 0) ? 1.0f : this.flash * 0.8f;
        this.prevCharge = chargeNow;
        this.petalsO = this.petals;
        float target = this.isOverloaded() ? 1.0f : 0.55f * this.getHeat() / HEAT_MAX;
        this.petals += (target - this.petals) * 0.15f;

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        int charge = this.getCharge();
        if (charge > 0) {
            if (!(this.getControllingPassenger() instanceof ServerPlayer player)) {
                this.entityData.set(DATA_CHARGE, 0);                 // sin conductor: se cancela
            } else {
                charge++;
                this.entityData.set(DATA_CHARGE, charge);
                if (charge % 3 == 0) {
                    level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 1.2f, 0.6f + 1.4f * charge / CHARGE_TICKS);
                }
                if (charge >= CHARGE_TICKS) {
                    this.entityData.set(DATA_CHARGE, 0);
                    boolean fired = this.fireShell(player, LATERAL);
                    this.cannonCooldown = fired ? RELOAD_TICKS : 10;
                    if (fired) {
                        this.lastShot = now;
                        this.muzzleBlast(level, player);
                        int heat = Math.min(HEAT_MAX, this.getHeat() + HEAT_PER_SHOT);
                        this.entityData.set(DATA_HEAT, heat);
                        if (heat >= HEAT_MAX) {
                            this.startOverload(level, now);
                        }
                    }
                }
            }
        }

        if (this.isOverloaded()) {
            long left = this.entityData.get(DATA_OVERLOAD_UNTIL) - now;
            this.entityData.set(DATA_HEAT, (int) (HEAT_MAX * left / OVERLOAD_TICKS));
            if (this.tickCount % 2 == 0) {
                this.vent(level);
            }
            if (this.tickCount % 10 == 0) {
                level.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.8f, 1.4f);
            }
            if (left <= 1L) {
                this.entityData.set(DATA_HEAT, 0);
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.0f, 1.6f);
            }
        } else if (this.getHeat() > 0 && now - this.lastShot > COOL_DELAY && this.tickCount % 2 == 0) {
            this.entityData.set(DATA_HEAT, this.getHeat() - 1);
        }
    }

    private void startOverload(ServerLevel level, long now) {
        this.entityData.set(DATA_OVERLOAD_UNTIL, now + OVERLOAD_TICKS);
        this.cannonCooldown = Math.max(this.cannonCooldown, OVERLOAD_TICKS);
        level.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1.6f, 0.8f);
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
                SoundSource.PLAYERS, 1.6f, 0.6f);
        Vec3 rear = this.reactorPos();
        level.sendParticles(ParticleTypes.CLOUD, rear.x, rear.y, rear.z, 24, 0.5, 0.3, 0.5, 0.06);
        level.sendParticles(MAGENTA, rear.x, rear.y, rear.z, 16, 0.4, 0.4, 0.4, 0.0);
    }

    /** Dónde está el reactor (parte trasera de la torreta), según hacia dónde mira el conductor. */
    private Vec3 reactorPos() {
        float yaw = this.getControllingPassenger() != null ? this.getControllingPassenger().getYRot() : this.getYRot();
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        return this.position().add(flat.scale(-0.66 * SCALE)).add(0.0, (0.82 + 0.34) * SCALE, 0.0);
    }

    /** Vapor ficticio que sale de los pétalos durante la sobrecarga. */
    private void vent(ServerLevel level) {
        Vec3 rear = this.reactorPos();
        level.sendParticles(ParticleTypes.CLOUD, rear.x, rear.y, rear.z, 3, 0.45, 0.3, 0.45, 0.03);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, rear.x, rear.y + 0.4, rear.z, 2, 0.4, 0.2, 0.4, 0.02);
        if (this.tickCount % 6 == 0) {
            level.sendParticles(MAGENTA, rear.x, rear.y, rear.z, 3, 0.45, 0.35, 0.45, 0.0);
        }
    }

    private void muzzleBlast(ServerLevel level, ServerPlayer player) {
        Vec3 dir = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), MIN_PITCH, MAX_PITCH), player.getYRot());
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 muzzle = this.position().add(0.0, this.pivotY(), 0.0).add(flat.scale(this.pivotForward()))
                .add(side.scale(LATERAL)).add(dir.scale(this.barrelLength()));
        level.sendParticles(VIOLET, muzzle.x, muzzle.y, muzzle.z, 14, 0.25, 0.25, 0.25, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 14, 0.3, 0.3, 0.3, 0.3);
        level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.2f, 1.5f);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Umbra no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_UMBRA.get();
    }
}
