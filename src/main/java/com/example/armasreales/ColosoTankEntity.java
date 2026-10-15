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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Tanque «Coloso» (ficticio): enorme, cuadrado y agresivo, con casco alto, frontal en varias capas de blindaje y una
 * torreta sobredimensionada con un cañón de plasma larguísimo. Mismas mecánicas base que los otros tanques
 * (conducción, obús de TNT, invulnerable salvo a explosiones de TNT) y mucho más pesado: más vida y más lento.
 *  - Cada disparo (y cada explosión de TNT que recibe) llena la barra de FURIA. Al llenarse entra en MODO BERSERKER
 *    durante 8 s: las rejillas brillan con más intensidad, saltan destellos alrededor del cañón, dispara mucho más
 *    rápido y avanza más deprisa. Al terminar la furia vuelve a cero.
 *  - Si se deja de disparar, la furia baja poco a poco.
 *  - No cambia de color con tintes.
 */
public class ColosoTankEntity extends TankEntity {
    public static final int FURY_MAX = 100;
    private static final int FURY_PER_SHOT = 25;           // cuatro disparos seguidos = modo berserker
    private static final int FURY_PER_HIT = 8;
    public static final int BERSERK_TICKS = 160;
    private static final int RELOAD_TICKS = 50;
    private static final int BERSERK_RELOAD = 16;
    private static final int FURY_DECAY_DELAY = 80;
    private static final float BERSERK_SPEED = 1.4f;

    private static final EntityDataAccessor<Integer> DATA_FURY =
            SynchedEntityData.defineId(ColosoTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_BERSERK_UNTIL =
            SynchedEntityData.defineId(ColosoTankEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(ColosoTankEntity.class, EntityDataSerializers.LONG);

    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.1f), 1.6f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.12f, 0.08f), 1.4f);

    private long lastActivity = 0L;

    public ColosoTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2);       // el más pesado de los medianos: lento y contundente
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FURY, 0);
        builder.define(DATA_BERSERK_UNTIL, 0L);
        builder.define(DATA_LAST_SHOT, -1000L);
    }

    /** Furia acumulada, de 0 a FURY_MAX (durante el modo berserker baja con el tiempo que queda). */
    public int getFury() {
        return this.entityData.get(DATA_FURY);
    }

    public boolean isBerserk() {
        return this.entityData.get(DATA_BERSERK_UNTIL) > this.level().getGameTime();
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    // Geometría del cañón (debe coincidir con ColosoTankRenderer): eje a 1,7 de alto y 0,9 por delante del centro
    @Override
    protected double pivotY() {
        return 1.7 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.9 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 2.6 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.5 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 4.0;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float speed = super.getRiddenSpeed(player);
        return this.isBerserk() ? speed * BERSERK_SPEED : speed;
    }

    // ------------------------------------------------------------------ Cañón y furia

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        if (!this.fireShell(player, 0.0)) {
            this.cannonCooldown = 10;
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        this.entityData.set(DATA_LAST_SHOT, now);
        this.lastActivity = now;
        this.cannonCooldown = this.isBerserk() ? BERSERK_RELOAD : RELOAD_TICKS;
        this.muzzleBlast(level, player);
        if (!this.isBerserk()) {
            this.addFury(level, FURY_PER_SHOT);
        }
    }

    private void addFury(ServerLevel level, int amount) {
        int fury = Math.min(FURY_MAX, this.getFury() + amount);
        this.entityData.set(DATA_FURY, fury);
        if (fury >= FURY_MAX && !this.isBerserk()) {
            this.startBerserk(level);
        }
    }

    private void startBerserk(ServerLevel level) {
        long now = level.getGameTime();
        this.entityData.set(DATA_BERSERK_UNTIL, now + BERSERK_TICKS);
        this.cannonCooldown = Math.min(this.cannonCooldown, BERSERK_RELOAD);
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.5f);
        Vec3 c = this.position().add(0.0, 2.2 * SCALE, 0.0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, 1.2, 0.6, 1.2, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 12, 1.2, 0.6, 1.2, 0.04);
        level.sendParticles(RED, c.x, c.y, c.z, 14, 1.2, 0.6, 1.2, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (this.isBerserk()) {
            long left = this.entityData.get(DATA_BERSERK_UNTIL) - now;
            this.entityData.set(DATA_FURY, (int) (FURY_MAX * left / BERSERK_TICKS));
            if (this.tickCount % 2 == 0) {
                this.sparks(level);
            }
            if (this.tickCount % 20 == 0) {
                level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_BURN,
                        SoundSource.PLAYERS, 1.2f, 0.7f);
            }
            if (left <= 1L) {
                this.entityData.set(DATA_FURY, 0);
                this.lastActivity = now;
                level.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 1.4f, 0.7f);
            }
        } else if (this.getFury() > 0 && now - this.lastActivity > FURY_DECAY_DELAY && this.tickCount % 4 == 0) {
            this.entityData.set(DATA_FURY, this.getFury() - 1);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && this.level() instanceof ServerLevel level && !this.isBerserk()) {
            this.lastActivity = level.getGameTime();
            this.addFury(level, FURY_PER_HIT);
        }
        return hurt;
    }

    /** Boca del cañón según hacia dónde apunta el conductor. */
    private Vec3 muzzlePos() {
        float yaw = this.getYRot();
        float pitch = 0.0f;
        if (this.getControllingPassenger() != null) {
            yaw = this.getControllingPassenger().getYRot();
            pitch = Mth.clamp(this.getControllingPassenger().getXRot(), MIN_PITCH, MAX_PITCH);
        }
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        return this.position().add(0.0, this.pivotY(), 0.0).add(flat.scale(this.pivotForward()))
                .add(dir.scale(this.barrelLength()));
    }

    /** Destellos y chispas alrededor del cañón durante el modo berserker. */
    private void sparks(ServerLevel level) {
        Vec3 m = this.muzzlePos();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 5, 0.9, 0.5, 0.9, 0.3);
        level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 2, 0.4, 0.3, 0.4, 0.03);
        if (this.tickCount % 6 == 0) {
            level.sendParticles(ORANGE, m.x, m.y, m.z, 4, 0.6, 0.4, 0.6, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, m.x, m.y, m.z, 2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void muzzleBlast(ServerLevel level, ServerPlayer player) {
        Vec3 m = this.muzzlePos();
        level.sendParticles(ORANGE, m.x, m.y, m.z, 18, 0.4, 0.4, 0.4, 0.0);
        level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 14, 0.3, 0.3, 0.3, 0.12);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 6, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.6f, 0.7f);
        level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 2.0f, 0.4f);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Coloso no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_COLOSO.get();
    }
}
