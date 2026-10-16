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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
 * Tanque «Singularidad» (ficticio, Proyecto X): el diseño más extraño de la generación, un tanque asimétrico montado
 * alrededor de un reactor experimental violeta, con la torreta desplazada a un lateral y placas que flotan
 * alrededor del núcleo. Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a
 * explosiones de TNT).
 *  - El reactor acumula energía solo (unos 25 s). Con la carga al 100 % el siguiente disparo NO lanza un obús: crea
 *    una SINGULARIDAD en el punto al que apunta el conductor (hasta 40 bloques). Durante 8 s la zona (radio 7)
 *    ralentiza fuertemente a todo ser vivo que haya dentro, salvo al conductor, y los arrastra suavemente al centro.
 *  - Mientras la singularidad está activa el reactor está en SOBRECARGA (destellos blancos en el núcleo) y los paneles
 *    de refrigeración se abren hacia fuera. Después empieza a recargarse.
 *  - Sin la carga completa dispara obuses normales (cadencia lenta).
 *  - No cambia de color con tintes.
 */
public class SingularityTankEntity extends TankEntity {
    public static final int CHARGE_MAX = 100;
    private static final int CHARGE_EVERY = 5;              // +1 cada 5 ticks = 25 s para cargar
    public static final int ZONE_TICKS = 160;
    private static final double ZONE_RADIUS = 7.0;
    private static final double AIM_RANGE = 40.0;
    private static final int RELOAD_TICKS = 40;

    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(SingularityTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ZONE_UNTIL =
            SynchedEntityData.defineId(SingularityTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(SingularityTankEntity.class, EntityDataSerializers.LONG);

    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.58f, 0.3f, 1.0f), 1.5f);
    private static final DustParticleOptions DEEP = new DustParticleOptions(new Vector3f(0.28f, 0.1f, 0.6f), 2.0f);

    private Vec3 zoneCenter = null;

    public SingularityTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24);
    }

    @Override
    protected double seatTopY() {
        return 1.5 * SCALE;
    }

    @Override
    public int[] interiorAccent() {
        return new int[]{190, 90, 255};
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

    /** Hay una singularidad activa (el reactor está en sobrecarga). */
    public boolean isOverloaded() {
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

    // Geometría del cañón (debe coincidir con SingularityTankRenderer): torreta a 0,85 a la derecha del centro,
    // eje a 1,57 de alto y 0,9 por delante del eje de la torreta
    @Override
    protected double turretOffsetX() {
        return -0.85 * SCALE;
    }

    @Override
    protected double pivotY() {
        return 1.57 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.9 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 2.2 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.0 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 4.0;
    }

    @Override
    protected double crushHalfWidth() {
        return 3.4;
    }

    // ------------------------------------------------------------------ Cañón y singularidad

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (this.getCharge() >= CHARGE_MAX && !this.isOverloaded()) {
            this.createSingularity(level, player, now);
            this.cannonCooldown = 30;
        } else {
            if (!this.fireShell(player, 0.0)) {
                this.cannonCooldown = 10;
                return;
            }
            this.cannonCooldown = RELOAD_TICKS;
            Vec3 m = this.muzzle(player);
            level.sendParticles(VIOLET, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 6, 0.2, 0.2, 0.2, 0.2);
        }
        this.entityData.set(DATA_LAST_SHOT, now);
    }

    private Vec3 muzzle(ServerPlayer player) {
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 tankFlat = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 tankSide = new Vec3(-tankFlat.z, 0.0, tankFlat.x);
        return this.position().add(0.0, this.pivotY(), 0.0).add(tankSide.scale(-this.turretOffsetX()))
                .add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
    }

    private void createSingularity(ServerLevel level, ServerPlayer player, long now) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(AIM_RANGE));
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        this.zoneCenter = center.add(0.0, 0.6, 0.0);
        this.entityData.set(DATA_ZONE_UNTIL, now + ZONE_TICKS);
        this.entityData.set(DATA_CHARGE, 0);
        Vec3 m = this.muzzle(player);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 30, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        level.playSound(null, this.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2f, 1.6f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(this.zoneCenter), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 2.0f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.isOverloaded() && this.zoneCenter != null) {
            this.tickZone(level);
        } else {
            this.zoneCenter = null;
            if (this.getCharge() < CHARGE_MAX && this.tickCount % CHARGE_EVERY == 0) {
                int charge = this.getCharge() + 1;
                this.entityData.set(DATA_CHARGE, charge);
                if (charge >= CHARGE_MAX) {
                    level.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                            SoundSource.PLAYERS, 1.4f, 0.7f);
                }
            }
        }
    }

    private void tickZone(ServerLevel level) {
        Vec3 c = this.zoneCenter;
        long left = this.entityData.get(DATA_ZONE_UNTIL) - level.getGameTime();
        // Efecto de ralentización y atracción suave hacia el centro
        if (this.tickCount % 4 == 0) {
            AABB box = new AABB(c.x - ZONE_RADIUS, c.y - ZONE_RADIUS, c.z - ZONE_RADIUS,
                    c.x + ZONE_RADIUS, c.y + ZONE_RADIUS, c.z + ZONE_RADIUS);
            LivingEntity driver = this.getControllingPassenger();
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != driver && e != this && e.getVehicle() != this && !(e instanceof TankEntity)
                            && e.position().distanceTo(c) <= ZONE_RADIUS);
            for (LivingEntity e : targets) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 3, false, false));
                Vec3 pull = c.subtract(e.position());
                if (pull.length() > 1.0) {
                    e.push(pull.normalize().scale(0.05));
                    e.hurtMarked = true;
                }
            }
        }
        // Visuales: anillo que se contrae, núcleo oscuro y chispas
        if (this.tickCount % 2 == 0) {
            double phase = (left % 40) / 40.0;
            double r = ZONE_RADIUS * (1.0 - 0.7 * phase);
            double rot = this.tickCount * 0.12;
            for (int i = 0; i < 14; i++) {
                double a = rot + i * (Math.PI * 2.0 / 14.0);
                level.sendParticles(VIOLET, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 8; i++) {
                double a = -rot * 1.5 + i * (Math.PI * 2.0 / 8.0);
                level.sendParticles(DEEP, c.x + Math.cos(a) * 1.4, c.y + 0.4 + 0.3 * Math.sin(rot * 3 + i),
                        c.z + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.6, c.z, 10, 1.2, 0.8, 1.2, 0.6);
        }
        if (this.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.6, c.z, 12, 2.5, 1.2, 2.5, 0.2);
            level.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 1.2f, 0.5f);
        }
        if (left <= 1L) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.6, c.z, 40, 1.5, 1.0, 1.5, 0.4);
            level.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 1.6f, 0.6f);
            this.zoneCenter = null;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // la Singularidad no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_SINGULARIDAD.get();
    }
}
