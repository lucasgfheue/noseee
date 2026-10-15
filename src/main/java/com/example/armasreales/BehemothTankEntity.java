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
 * Tanque «Behemoth» (ficticio, Proyecto X): fortaleza industrial sobre orugas, de casco excepcionalmente ancho y
 * con tres niveles de blindaje: placas exteriores negras, estructura intermedia de titanio y núcleo central.
 * Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a explosiones de TNT)
 * y el más resistente: mucha vida y muy lento.
 *  - Blindaje por capas (según la vida que le queda): con las placas exteriores y la estructura de titanio recibe la
 *    mitad del daño de las explosiones; al perder las placas (por debajo del 66 % de vida) recibe el 75 %; al perder
 *    también el titanio (por debajo del 33 %) queda expuesto el núcleo y recibe el daño completo. Cada capa que se
 *    pierde se ve en el modelo y suelta chispas y humo.
 *  - Cada disparo calienta los radiadores traseros, cuya luz naranja aumenta; se enfrían solos.
 *  - No cambia de color con tintes.
 */
public class BehemothTankEntity extends TankEntity {
    public static final int HEAT_MAX = 100;
    private static final int HEAT_PER_SHOT = 34;
    private static final int RELOAD_TICKS = 45;
    private static final int COOL_DELAY = 50;

    private static final EntityDataAccessor<Integer> DATA_HEAT =
            SynchedEntityData.defineId(BehemothTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(BehemothTankEntity.class, EntityDataSerializers.LONG);

    private long lastShot = 0L;
    private int prevLayer = -1;

    public BehemothTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 700.0)
                .add(Attributes.MOVEMENT_SPEED, 0.16);      // el más pesado de todos
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEAT, 0);
        builder.define(DATA_LAST_SHOT, -1000L);
    }

    /** Calor de los radiadores, de 0 a HEAT_MAX. */
    public int getHeat() {
        return this.entityData.get(DATA_HEAT);
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    /**
     * Capas de blindaje que le quedan, según la vida: 2 = placas exteriores + titanio + núcleo,
     * 1 = titanio + núcleo (sin placas exteriores), 0 = sólo el núcleo expuesto. Sirve en cliente y servidor.
     */
    public int armorLayer() {
        float ratio = this.getHealth() / this.getMaxHealth();
        if (ratio > 0.66f) {
            return 2;
        }
        return ratio > 0.33f ? 1 : 0;
    }

    // Geometría del cañón (debe coincidir con BehemothTankRenderer): eje a 1,35 de alto y 0,75 por delante del centro
    @Override
    protected double pivotY() {
        return 1.35 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.75 * SCALE;
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
        return 3.6;
    }

    @Override
    protected double crushHalfWidth() {
        return 3.0;                                    // casco de 6 de ancho
    }

    // ------------------------------------------------------------------ Cañón, calor y blindaje

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
        this.lastShot = now;
        this.cannonCooldown = RELOAD_TICKS;
        this.entityData.set(DATA_HEAT, Math.min(HEAT_MAX, this.getHeat() + HEAT_PER_SHOT));
        Vec3 flat = Vec3.directionFromRotation(0.0f, player.getYRot());
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 m = this.position().add(0.0, this.pivotY(), 0.0).add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 10, 0.3, 0.3, 0.3, 0.25);
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.8f, 0.5f);
        // fumarolas de los radiadores traseros
        Vec3 rear = this.position().add(flat.scale(-1.5 * SCALE)).add(0.0, 1.5 * SCALE, 0.0).add(side.scale(0.0));
        level.sendParticles(ParticleTypes.SMOKE, rear.x, rear.y, rear.z, 6, 1.2, 0.5, 0.2, 0.01);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        // Pérdida de capas de blindaje
        int layer = this.armorLayer();
        if (this.prevLayer >= 0 && layer < this.prevLayer) {
            this.armorBreak(level);
        }
        this.prevLayer = layer;
        // Enfriamiento de los radiadores
        if (this.getHeat() > 0 && now - this.lastShot > COOL_DELAY && this.tickCount % 2 == 0) {
            this.entityData.set(DATA_HEAT, this.getHeat() - 1);
        }
        // Radiadores muy calientes sueltan humo
        if (this.getHeat() > 70 && this.tickCount % 5 == 0) {
            Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
            Vec3 rear = this.position().add(flat.scale(-1.5 * SCALE)).add(0.0, 1.9 * SCALE, 0.0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, rear.x, rear.y, rear.z, 2, 1.2, 0.2, 0.2, 0.01);
        }
    }

    private void armorBreak(ServerLevel level) {
        Vec3 c = this.position().add(0.0, 1.0 * SCALE, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 1.4, 0.4, 1.4, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 24, 1.8, 0.5, 1.8, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 1.8, 0.6, 1.8, 0.4);
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.PLAYERS, 2.0f, 0.6f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Las capas de blindaje amortiguan el daño que sí le hace daño (explosiones de TNT)
        float factor = switch (this.armorLayer()) {
            case 2 -> 0.5f;
            case 1 -> 0.75f;
            default -> 1.0f;
        };
        return super.hurt(source, amount * factor);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Behemoth no se tiñe
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_BEHEMOTH.get();
    }
}
