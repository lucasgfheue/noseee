package com.example.armasreales;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Tanque «Reactor Viviente» (ficticio, Proyecto X): cuerpo de anchura media con la torreta asimétrica y una parte
 * trasera elevada que alberga una esfera violeta visible dentro de una jaula metálica. Mismas mecánicas base que los
 * otros tanques (conducción, obús de TNT, invulnerable salvo a explosiones de TNT).
 *  - El reactor acumula energía solo (unos 20 s). Con la carga al 100 % el siguiente disparo NO lanza un obús: abre
 *    un POZO DE DISTORSIÓN en el punto al que apunta el conductor (hasta 45 bloques). Durante 10 s la zona (radio 10)
 *    ralentiza fuertemente a los VEHÍCULOS que haya dentro (otros tanques, barcos, vagonetas y monturas con jinete),
 *    salvo al propio tanque. No afecta a quien va a pie.
 *  - Mientras el pozo está activo el centro del reactor se vuelve blanco y los paneles de refrigeración se abren.
 *    Después empieza a recargarse.
 *  - Sin la carga completa dispara obuses normales (cadencia lenta).
 *  - No cambia de color con tintes.
 */
public class ReactorTankEntity extends TankEntity {
    public static final int CHARGE_MAX = 100;
    private static final int CHARGE_EVERY = 4;               // +1 cada 4 ticks = 20 s para cargar
    public static final int ZONE_TICKS = 200;
    private static final double ZONE_RADIUS = 10.0;
    private static final double AIM_RANGE = 45.0;
    private static final int RELOAD_TICKS = 40;

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(ReactorTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ZONE_UNTIL =
            SynchedEntityData.defineId(ReactorTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(ReactorTankEntity.class, EntityDataSerializers.LONG);

    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(0.95f, 0.25f, 0.8f), 1.5f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.5f, 0.25f, 0.95f), 1.7f);

    private Vec3 zoneCenter = null;

    public ReactorTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 450.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
        builder.define(DATA_ZONE_UNTIL, 0L);
        builder.define(DATA_LAST_SHOT, -1000L);
    }

    /** Carga del reactor, de 0 a CHARGE_MAX. */
    public int getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    /** Hay un pozo de distorsión activo (el centro del reactor está blanco). */
    public boolean isDistorting() {
        return this.entityData.get(DATA_ZONE_UNTIL) > this.level().getGameTime();
    }

    /** Apertura de los paneles de refrigeración, de 0 (cerrados) a 1 (abiertos del todo); sirve en el cliente. */
    public float openFraction(float partialTick) {
        long until = this.entityData.get(DATA_ZONE_UNTIL);
        float now = this.level().getGameTime() + partialTick;
        float start = until - ZONE_TICKS;
        if (now < start) {
            return 0.0f;
        }
        if (now < until) {
            return Mth.clamp((now - start) / 10.0f, 0.0f, 1.0f);
        }
        return Mth.clamp(1.0f - (now - until) / 20.0f, 0.0f, 1.0f);
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    // Geometría del cañón (debe coincidir con ReactorTankRenderer): torreta a 0,75 a la derecha y 0,85 por delante del
    // centro, eje a 1,6 de alto y 0,9 por delante del eje de la torreta
    @Override
    protected double turretOffsetX() {
        return -0.75 * SCALE;
    }

    @Override
    protected double turretOffsetZ() {
        return 0.85 * SCALE;
    }

    @Override
    protected double pivotY() {
        return 1.6 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.9 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 2.3 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.0 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 4.4;
    }

    @Override
    protected double crushHalfWidth() {
        return 3.2;
    }

    // ------------------------------------------------------------------ Cañón y pozo de distorsión

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (this.getCharge() >= CHARGE_MAX && !this.isDistorting()) {
            this.openWell(level, player, now);
            this.cannonCooldown = 30;
        } else {
            if (!this.fireShell(player, 0.0)) {
                this.cannonCooldown = 10;
                return;
            }
            this.cannonCooldown = RELOAD_TICKS;
            Vec3 m = this.muzzle(player);
            level.sendParticles(MAGENTA, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 6, 0.2, 0.2, 0.2, 0.2);
        }
        this.entityData.set(DATA_LAST_SHOT, now);
    }

    private Vec3 muzzle(ServerPlayer player) {
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 tankFlat = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 tankSide = new Vec3(-tankFlat.z, 0.0, tankFlat.x);
        return this.position().add(0.0, this.pivotY(), 0.0).add(tankSide.scale(-this.turretOffsetX()))
                .add(tankFlat.scale(this.turretOffsetZ())).add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
    }

    private void openWell(ServerLevel level, ServerPlayer player, long now) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(AIM_RANGE));
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        this.zoneCenter = center.add(0.0, 0.4, 0.0);
        this.entityData.set(DATA_ZONE_UNTIL, now + ZONE_TICKS);
        this.entityData.set(DATA_CHARGE, 0);
        Vec3 m = this.muzzle(player);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 30, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        level.playSound(null, this.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2f, 1.4f);
        level.playSound(null, BlockPos.containing(this.zoneCenter), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 2.0f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.isDistorting() && this.zoneCenter != null) {
            this.tickWell(level);
        } else {
            this.zoneCenter = null;
            if (this.getCharge() < CHARGE_MAX && this.tickCount % CHARGE_EVERY == 0) {
                int charge = this.getCharge() + 1;
                this.entityData.set(DATA_CHARGE, charge);
                if (charge >= CHARGE_MAX) {
                    level.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                            SoundSource.PLAYERS, 1.4f, 0.8f);
                }
            }
        }
    }

    /** Un vehículo para el pozo: tanques, barcos, vagonetas y cualquier montura con jinete. */
    private boolean isVehicle(Entity e) {
        if (e == this || e.getVehicle() == this) {
            return false;
        }
        return e instanceof TankEntity || e instanceof Boat || e instanceof AbstractMinecart
                || (e instanceof LivingEntity && e.getControllingPassenger() != null);
    }

    private void tickWell(ServerLevel level) {
        Vec3 c = this.zoneCenter;
        long left = this.entityData.get(DATA_ZONE_UNTIL) - level.getGameTime();
        if (this.tickCount % 4 == 0) {
            AABB box = new AABB(c.x - ZONE_RADIUS, c.y - ZONE_RADIUS, c.z - ZONE_RADIUS,
                    c.x + ZONE_RADIUS, c.y + ZONE_RADIUS, c.z + ZONE_RADIUS);
            List<Entity> targets = level.getEntities((Entity) null, box,
                    e -> this.isVehicle(e) && e.position().distanceTo(c) <= ZONE_RADIUS);
            for (Entity e : targets) {
                if (e instanceof LivingEntity living) {
                    // los tanques son Mob: la lentitud reduce su velocidad de conducción
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 3, false, false));
                } else {
                    Vec3 v = e.getDeltaMovement();
                    e.setDeltaMovement(v.x * 0.45, v.y, v.z * 0.45);
                    e.hurtMarked = true;
                }
            }
        }
        // Visuales: anillo exterior estable, anillos que se contraen y núcleo magenta
        if (this.tickCount % 2 == 0) {
            double rot = this.tickCount * 0.1;
            for (int i = 0; i < 20; i++) {
                double a = rot + i * (Math.PI * 2.0 / 20.0);
                level.sendParticles(MAGENTA, c.x + Math.cos(a) * ZONE_RADIUS, c.y + 0.1, c.z + Math.sin(a) * ZONE_RADIUS,
                        1, 0, 0, 0, 0);
            }
            double phase = (left % 30) / 30.0;
            double r = ZONE_RADIUS * (1.0 - 0.85 * phase);
            for (int i = 0; i < 12; i++) {
                double a = -rot * 1.3 + i * (Math.PI * 2.0 / 12.0);
                level.sendParticles(VIOLET, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.5, c.z, 8, 1.5, 0.6, 1.5, 0.6);
        }
        if (this.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 14, 4.0, 0.8, 4.0, 0.2);
            level.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 1.3f, 0.5f);
        }
        if (left <= 1L) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 40, 2.0, 0.8, 2.0, 0.4);
            level.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 1.6f, 0.6f);
            this.zoneCenter = null;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Reactor Viviente no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_REACTOR.get();
    }
}
