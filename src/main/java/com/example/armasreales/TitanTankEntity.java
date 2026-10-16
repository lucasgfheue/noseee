package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanque «Titán Blindado» (ficticio, Proyecto X): fortaleza terrestre de proporciones monstruosas, más una
 * estructura blindada móvil que un tanque. Casco extremadamente ancho y alto, cuatro conjuntos de orugas
 * independientes (dos por lado), torreta central elevada y reactor ámbar en la parte trasera.
 * Mismas mecánicas base que los otros tanques (conducción, obús de TNT, invulnerable salvo a explosiones de TNT)
 * y el más pesado de todos: mucha vida y muy lento.
 *  - Bloques laterales reemplazables: lleva cuatro bloques de blindaje (dos por lado). Cada uno que sigue en su sitio
 *    reduce un 10 % el daño de las explosiones. Cada 60 puntos de daño recibidos arrancan uno de los bloques.
 *  - Clic derecho con un bloque de hierro repone un bloque que falte (gasta un bloque de hierro).
 *  - Cada disparo calienta el reactor trasero y sus líneas ámbar brillan más; se enfría solo.
 *  - No cambia de color con tintes.
 */
public class TitanTankEntity extends TankEntity {
    public static final int HEAT_MAX = 100;
    private static final int HEAT_PER_SHOT = 25;
    private static final int RELOAD_TICKS = 60;
    private static final int COOL_DELAY = 50;

    /** Bloques laterales: bit 0 = izquierda delante, 1 = izquierda detrás, 2 = derecha delante, 3 = derecha detrás. */
    public static final int ALL_BLOCKS = 0b1111;
    private static final float BLOCK_HP = 60.0f;

    private static final EntityDataAccessor<Integer> DATA_BLOCKS =
            SynchedEntityData.defineId(TitanTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEAT =
            SynchedEntityData.defineId(TitanTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(TitanTankEntity.class, EntityDataSerializers.LONG);

    private long lastShot = 0L;
    private float blockDamage = 0.0f;

    public TitanTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 800.0)
                .add(Attributes.MOVEMENT_SPEED, 0.15);      // el más pesado de todos
    }

    @Override
    protected double seatTopY() {
        return 2.3 * SCALE;
    }

    @Override
    public int[] interiorAccent() {
        return new int[]{255, 120, 40};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLOCKS, ALL_BLOCKS);
        builder.define(DATA_HEAT, 0);
        builder.define(DATA_LAST_SHOT, -1000L);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Blocks", this.getBlocks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_BLOCKS, tag.contains("Blocks") ? tag.getInt("Blocks") & ALL_BLOCKS : ALL_BLOCKS);
    }

    /** Máscara de bloques laterales que siguen en su sitio. */
    public int getBlocks() {
        return this.entityData.get(DATA_BLOCKS);
    }

    public boolean hasBlock(int index) {
        return (this.getBlocks() & (1 << index)) != 0;
    }

    /** Calor del reactor, de 0 a HEAT_MAX. */
    public int getHeat() {
        return this.entityData.get(DATA_HEAT);
    }

    /** Ticks (con decimales) desde el último disparo; sirve en el cliente para el retroceso y el fogonazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    // Geometría del cañón (debe coincidir con TitanTankRenderer): eje a 2,1 de alto y 1,0 por delante del centro
    @Override
    protected double pivotY() {
        return 2.1 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 1.0 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 2.6 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(2.9 * SCALE);
    }

    @Override
    protected double crushHalfLength() {
        return 4.2;
    }

    @Override
    protected double crushHalfWidth() {
        return 3.4;                                    // casco de 6,8 de ancho
    }

    // ------------------------------------------------------------------ Cañón y reactor

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
        Vec3 muzzle = this.position().add(0.0, this.pivotY(), 0.0).add(flat.scale(this.pivotForward()))
                .add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(this.barrelLength()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 10, 0.3, 0.3, 0.3, 0.25);
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.4f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (this.getHeat() > 0 && now - this.lastShot > COOL_DELAY && this.tickCount % 2 == 0) {
            this.entityData.set(DATA_HEAT, this.getHeat() - 1);
        }
        // Reactor muy caliente: sale humo por detrás
        if (this.getHeat() > 70 && this.tickCount % 5 == 0) {
            Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
            Vec3 rear = this.position().add(flat.scale(-2.2 * SCALE)).add(0.0, 1.8 * SCALE, 0.0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, rear.x, rear.y, rear.z, 2, 1.0, 0.2, 0.2, 0.01);
        }
    }

    // ------------------------------------------------------------------ Bloques laterales reemplazables

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Cada bloque lateral que sigue en su sitio amortigua un 10 % del daño que sí le hace daño (TNT)
        float factor = 1.0f - 0.1f * Integer.bitCount(this.getBlocks());
        boolean hurt = super.hurt(source, amount * factor);
        if (hurt && this.level() instanceof ServerLevel level && this.getBlocks() != 0) {
            this.blockDamage += amount;
            while (this.blockDamage >= BLOCK_HP && this.getBlocks() != 0) {
                this.blockDamage -= BLOCK_HP;
                this.tearOffBlock(level);
            }
        }
        return hurt;
    }

    private void tearOffBlock(ServerLevel level) {
        List<Integer> present = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (this.hasBlock(i)) {
                present.add(i);
            }
        }
        int index = present.get(this.random.nextInt(present.size()));
        this.entityData.set(DATA_BLOCKS, this.getBlocks() & ~(1 << index));
        Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
        double lateral = (index < 2 ? 1.0 : -1.0) * -1.6 * SCALE;     // índice 0-1 = izquierda (lado contrario a "side")
        double forward = (index % 2 == 0 ? 0.7 : -0.9) * SCALE;
        Vec3 c = this.position().add(0.0, 1.3 * SCALE, 0.0).add(side.scale(lateral)).add(flat.scale(forward));
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.5, 0.4, 0.5, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 14, 0.7, 0.5, 0.7, 0.04);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 18, 0.7, 0.5, 0.7, 0.35);
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.6f, 0.5f);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DyeItem) {
            return InteractionResult.PASS;     // el Titán no se tiñe
        }
        // Un bloque de hierro repone un bloque lateral que falte
        if (held.is(Items.IRON_BLOCK) && !player.isSecondaryUseActive() && this.getBlocks() != ALL_BLOCKS) {
            if (!this.level().isClientSide) {
                for (int i = 0; i < 4; i++) {
                    if (!this.hasBlock(i)) {
                        this.entityData.set(DATA_BLOCKS, this.getBlocks() | (1 << i));
                        break;
                    }
                }
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE,
                        SoundSource.PLAYERS, 1.0f, 0.8f);
                this.level().playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR,
                        SoundSource.PLAYERS, 1.2f, 0.8f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_TITAN.get();
    }
}
