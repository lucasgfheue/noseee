package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tanque: casco de 6 de largo x 4 de ancho x 4 de alto (el modelo original de 3x2x2 al doble).
 *
 * - Se monta con clic derecho. W/S avanzan y retroceden, A/D giran el casco.
 * - Velocidad igual a la de un cerdo montado y sube escalones de 1 bloque.
 * - La torreta y el cañón siguen hacia donde mira el conductor; clic izquierdo dispara.
 * - Invulnerable a todo salvo a explosiones de TNT (lanzador de TNT, TNT encendido y vagoneta con TNT).
 * - Clic derecho con un tinte negro, azul, amarillo, verde o rojo le cambia el color.
 * - Shift + clic derecho con la mano vacía lo recoge.
 */
public class TankEntity extends Mob {
    /** Factor de tamaño respecto al modelo original (3x2x2). */
    public static final float SCALE = 2.0f;

    /** Giro del casco con A/D, en grados por tick. */
    private static final float TURN_SPEED = 3.0f;

    /** Límites de elevación del cañón (en grados; negativo = hacia arriba). */
    public static final float MIN_PITCH = -25.0f;
    public static final float MAX_PITCH = 8.0f;

    /** Dónde está el eje del cañón respecto al tanque (debe coincidir con TankRenderer). */
    public static final double PIVOT_Y = 1.24 * SCALE;
    public static final double PIVOT_FORWARD = 0.72 * SCALE;
    public static final double BARREL_LENGTH = 1.4 * SCALE;

    private static final float SHELL_SPEED = 3.2f;
    private static final int COOLDOWN_TICKS = 40;
    private static final float MAX_HEALTH = 200.0f;

    /** Radio de las ruedas y separación entre orugas (para animarlas), en bloques. */
    private static final float WHEEL_RADIUS = 0.34f * SCALE;
    private static final float HALF_GAUGE = 0.8f * SCALE;

    /** Skins: 0 = original (verde militar) y 1..5 = tintes. */
    public static final int SKIN_DEFAULT = 0;
    public static final int SKIN_BLACK = 1;
    public static final int SKIN_BLUE = 2;
    public static final int SKIN_YELLOW = 3;
    public static final int SKIN_GREEN = 4;
    public static final int SKIN_RED = 5;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);

    /** Asientos: 0 = arriba (torreta: conduce y dispara) y 1..4 = interior. */
    public static final int SEAT_COUNT = 5;
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_0 =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_1 =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_2 =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_3 =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_4 =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final List<EntityDataAccessor<Optional<UUID>>> DATA_SEATS =
            List.of(DATA_SEAT_0, DATA_SEAT_1, DATA_SEAT_2, DATA_SEAT_3, DATA_SEAT_4);

    // Ángulos de las ruedas (grados), lado izquierdo y derecho. Sólo se usan en el cliente.
    public float wheelAngleL, wheelAngleR, wheelAngleLO, wheelAngleRO;

    protected int cannonCooldown = 0;
    private long lastPickupClick = -1000L;
    private UUID lastPickupPlayer = null;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25)   // igual que el cerdo
                .add(Attributes.STEP_HEIGHT, 1.0)       // sube 1 bloque
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ------------------------------------------------------------------ Skins

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, SKIN_DEFAULT);
        for (EntityDataAccessor<Optional<UUID>> seat : DATA_SEATS) {
            builder.define(seat, Optional.empty());
        }
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, skin);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", this.getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkin(tag.getInt("Skin"));
    }

    private static int skinForDye(DyeColor color) {
        return switch (color) {
            case BLACK -> SKIN_BLACK;
            case BLUE -> SKIN_BLUE;
            case YELLOW -> SKIN_YELLOW;
            case GREEN -> SKIN_GREEN;
            case RED -> SKIN_RED;
            default -> -1;
        };
    }

    // ------------------------------------------------------------------ Interacción

    // ------------------------------------------------------------------ Asientos

    /** Quién ocupa el asiento (null si nadie lo reclama). */
    public UUID seatOccupantId(int seat) {
        return this.entityData.get(DATA_SEATS.get(seat)).orElse(null);
    }

    public boolean isSeatFree(int seat) {
        UUID id = this.seatOccupantId(seat);
        if (id == null) {
            return true;
        }
        for (Entity passenger : this.getPassengers()) {
            if (passenger.getUUID().equals(id)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasFreeSeat() {
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (this.isSeatFree(i)) {
                return true;
            }
        }
        return false;
    }

    /** Asiento que ocupa la entidad, o -1. */
    public int seatOf(Entity entity) {
        UUID id = entity.getUUID();
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (id.equals(this.seatOccupantId(i))) {
                return i;
            }
        }
        return -1;
    }

    private void clearSeatsOf(UUID id) {
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (id.equals(this.seatOccupantId(i))) {
                this.entityData.set(DATA_SEATS.get(i), Optional.empty());
            }
        }
    }

    private void putInSeat(Entity entity, int seat) {
        this.clearSeatsOf(entity.getUUID());
        this.entityData.set(DATA_SEATS.get(seat), Optional.of(entity.getUUID()));
    }

    /** El servidor atiende la petición del menú de asientos: sube al jugador o lo cambia de sitio. */
    public void requestSeat(ServerPlayer player, int seat) {
        if (seat < 0 || seat >= SEAT_COUNT || !this.isAlive() || player.isSpectator()
                || player.distanceToSqr(this) > 144.0) {
            return;
        }
        if (!this.isSeatFree(seat) && this.seatOf(player) != seat) {
            player.displayClientMessage(Component.literal("Ese asiento está ocupado"), true);
            return;
        }
        if (player.getVehicle() != this && !player.startRiding(this)) {
            return;
        }
        this.putInSeat(player, seat);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < SEAT_COUNT;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide && this.seatOf(passenger) < 0) {
            for (int i = 0; i < SEAT_COUNT; i++) {
                if (this.isSeatFree(i)) {
                    this.putInSeat(passenger, i);
                    break;
                }
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide) {
            this.clearSeatsOf(passenger.getUUID());
        }
    }

    /** Altura del asiento de la torreta (ya multiplicada por SCALE); coincide con passengerAttachments. */
    protected double seatTopY() {
        return 1.4 * SCALE;
    }

    /** Interior (en bloques, ejes del modelo: +X izquierda, +Z delante, medidos desde el centro del tanque). */
    public double interiorHalfWidth() {
        return Math.max(0.9, this.crushHalfWidth() * 0.42);
    }

    public double interiorBack() {
        return -this.crushHalfLength() * 0.62;
    }

    public double interiorFront() {
        return this.crushHalfLength() * 0.6;
    }

    public double interiorFloor() {
        return 0.3 * this.seatTopY();
    }

    public double interiorCeil() {
        return 0.85 * this.seatTopY();
    }

    /** Color de las luces del interior. */
    public int[] interiorAccent() {
        return new int[]{255, 170, 40};
    }

    /** Posición de los pies de cada asiento (x izquierda, y, z delante), relativa al centro del tanque. */
    public Vec3 seatLocal(int seat) {
        if (seat == 0) {
            return new Vec3(this.turretOffsetX(), this.seatTopY(), this.turretOffsetZ());
        }
        double x = this.interiorHalfWidth() * 0.5 * ((seat == 1 || seat == 3) ? 1.0 : -1.0);
        double z = (seat <= 2) ? this.interiorFront() * 0.55 : this.interiorBack() * 0.55;
        return new Vec3(x, this.interiorFloor() + 0.45, z);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int seat = this.seatOf(passenger);
        if (seat < 0) {
            super.positionRider(passenger, callback);
            return;
        }
        Vec3 local = this.seatLocal(seat);
        Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 left = new Vec3(forward.z, 0.0, -forward.x);
        double y = this.getY() + local.y;
        if (seat == 0) {
            y -= passenger.getVehicleAttachmentPoint(this).y;
        }
        callback.accept(passenger,
                this.getX() + forward.x * local.z + left.x * local.x,
                y,
                this.getZ() + forward.z * local.z + left.z * local.x);
    }

    /**
     * Controla el tanque (movimiento y cañón): el jugador del asiento de arriba; si no hay nadie arriba,
     * el único jugador del interior. Con varios jugadores y la torreta vacía, nadie lo controla.
     */
    @Override
    public LivingEntity getControllingPassenger() {
        Player only = null;
        int players = 0;
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                if (this.seatOf(player) == 0) {
                    return player;
                }
                players++;
                only = player;
            }
        }
        return players == 1 ? only : null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 1) Tinte: cambia el color
        if (held.getItem() instanceof DyeItem dye) {
            int skin = skinForDye(dye.getDyeColor());
            if (skin < 0 || skin == this.getSkin()) {
                return InteractionResult.PASS;   // tinte sin skin, o ya tiene ese color
            }
            if (!this.level().isClientSide) {
                this.setSkin(skin);
                this.level().playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH,
                        SoundSource.PLAYERS, 1.0f, 1.3f);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 2) Shift + mano vacía y cerca del tanque: lo recoge (sólo si está vacío) y el objeto va a tu inventario
        if (player.isSecondaryUseActive() && held.isEmpty() && !this.isVehicle()) {
            if (this.getBoundingBox().distanceToSqr(player.getEyePosition()) > 9.0) {
                return InteractionResult.PASS;   // demasiado lejos: no hace nada
            }
            if (!this.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    ItemStack stack = new ItemStack(this.getPickupItem());
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 3) Montar: se abre el menú de asientos (arriba a la derecha); si está lleno, no deja subir
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.hasFreeSeat()) {
            if (!this.level().isClientSide) {
                player.displayClientMessage(Component.literal("El tanque está lleno"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.level().isClientSide) {
            TankSeatScreen.open(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ Conducción

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

    // ------------------------------------------------------------------ Tick: ruedas, humo y cañón

    @Override
    public void tick() {
        super.tick();
        if (this.cannonCooldown > 0) {
            this.cannonCooldown--;
        }
        if (this.level().isClientSide) {
            this.updateWheels();
        } else if (this.level() instanceof ServerLevel level) {
            this.showDamage(level);
            if (this.getControllingPassenger() != null && this.tickCount % 2 == 0) {
                this.crushBlocks(level);
            }
        }
    }

    /** Destruye todos los bloques que se crucen con el casco, para que nunca se atasque. */
    protected void crushBlocks(ServerLevel level) {
        Vec3 delta = this.getDeltaMovement();
        boolean moving = delta.x * delta.x + delta.z * delta.z > 1.0E-4;
        boolean turning = this.getControllingPassenger() instanceof Player p && Math.abs(p.xxa) > 0.0f;
        if (!moving && !turning && !this.horizontalCollision) {
            return;
        }
        Vec3 f = Vec3.directionFromRotation(0.0f, this.getYRot());
        double rx = -f.z;
        double rz = f.x;
        int baseY = Mth.floor(this.getY() + 0.1);
        int height = this.crushHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double halfLen = this.crushHalfLength();
        double halfWidth = this.crushHalfWidth();
        for (double a = -halfLen; a <= halfLen; a += 0.5) {
            for (double b = -halfWidth; b <= halfWidth; b += 0.5) {
                int x = Mth.floor(this.getX() + f.x * a + rx * b);
                int z = Mth.floor(this.getZ() + f.z * a + rz * b);
                for (int dy = 0; dy < height; dy++) {
                    pos.set(x, baseY + dy, z);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir() || st.getDestroySpeed(level, pos) < 0.0f) {
                        continue;
                    }
                    level.destroyBlock(pos, false);
                }
            }
        }
    }

    /** Con poca vida sale humo del motor, y con muy poca, fuego. */
    private void showDamage(ServerLevel level) {
        float ratio = this.getHealth() / this.getMaxHealth();
        if (ratio >= 0.5f || this.tickCount % 4 != 0) {
            return;
        }
        Vec3 back = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(-1.2 * SCALE);
        double x = this.getX() + back.x;
        double y = this.getY() + 1.0 * SCALE;
        double z = this.getZ() + back.z;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.3, 0.2, 0.3, 0.02);
        if (ratio < 0.25f) {
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.3, 0.1, 0.3, 0.02);
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
        if (!this.fireShell(player, 0.0)) {
            this.cannonCooldown = 10;
            return;
        }
        this.cannonCooldown = COOLDOWN_TICKS;
    }

    /** Eje y largo del cañón; los tanques con otra silueta los sobreescriben. */
    protected double pivotY() {
        return PIVOT_Y;
    }

    /** Desplazamiento hacia delante del eje de la torreta respecto al centro del tanque (ya multiplicado por SCALE). */
    protected double turretOffsetZ() {
        return 0.0;
    }

    /** Desplazamiento lateral del eje de la torreta (en el eje X del modelo, ya multiplicado por SCALE; +X = izquierda). */
    protected double turretOffsetX() {
        return 0.0;
    }

    protected double pivotForward() {
        return PIVOT_FORWARD;
    }

    protected double barrelLength() {
        return BARREL_LENGTH;
    }

    /** Medio largo (en bloques) de lo que aplasta el casco al avanzar. */
    protected double crushHalfLength() {
        return 3.5;
    }

    /** Medio ancho (en bloques) de lo que aplasta el casco al avanzar. */
    protected double crushHalfWidth() {
        return 2.5;
    }

    /** Altura (en bloques) de lo que aplasta el casco al avanzar. */
    protected int crushHeight() {
        return (int) Math.ceil(2.0 * SCALE);
    }

    /** Objeto que se recoge con Shift + click derecho. */
    protected net.minecraft.world.item.Item getPickupItem() {
        return ModItems.TANQUE.get();
    }

    /**
     * Dispara un proyectil de TNT desde el cañón. lateral desplaza la boca hacia un lado (cañones dobles).
     * Devuelve false si no hay munición.
     */
    protected boolean fireShell(ServerPlayer player, double lateral) {
        ServerLevel level = player.serverLevel();
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, Items.TNT);

        if (ammo.isEmpty() && !creative) {
            level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            return false;
        }

        float pitch = Mth.clamp(player.getXRot(), MIN_PITCH, MAX_PITCH);
        float yaw = player.getYRot();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 tankFlat = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 tankSide = new Vec3(-tankFlat.z, 0.0, tankFlat.x);
        Vec3 pivot = this.position().add(0.0, this.pivotY(), 0.0).add(tankSide.scale(-this.turretOffsetX())).add(tankFlat.scale(this.turretOffsetZ()))
                .add(flat.scale(this.pivotForward())).add(side.scale(lateral));
        Vec3 muzzle = pivot.add(dir.scale(this.barrelLength()));

        TntProjectile shell = new TntProjectile(level, player);
        shell.setPos(muzzle.x, muzzle.y - shell.getBbHeight() / 2.0, muzzle.z);
        shell.shoot(dir.x, dir.y, dir.z, SHELL_SPEED, 0.0f);
        level.addFreshEntity(shell);

        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.15, 0.15, 0.15, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.2, 0.2, 0.2, 0.03);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 3.0f, 0.5f);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 3.0f, 0.4f);

        if (!creative) {
            ammo.shrink(1);
        }
        return true;
    }

    // ------------------------------------------------------------------ Daño: sólo el TNT

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        // /kill y el vacío siempre funcionan
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        // Sólo explosiones cuyo origen directo sea el lanzador de TNT (o este cañón), TNT encendido o vagoneta con TNT
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_EXPLOSION) && isTntSource(direct)) {
            // Sus propios obuses no le hacen daño
            if (direct instanceof TntProjectile shell && shell.getOwner() != null
                    && this.hasPassenger(shell.getOwner())) {
                return false;
            }
            return super.hurt(source, amount);
        }
        return false;
    }

    private static boolean isTntSource(Entity entity) {
        return entity instanceof TntProjectile || entity instanceof PrimedTnt || entity instanceof MinecartTNT;
    }

    /** Inmune a todos los efectos de poción (veneno, wither, parálisis del taser, etc.). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + SCALE, this.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + SCALE, this.getZ(),
                    40, SCALE, SCALE * 0.5, SCALE, 0.05);
        }
        super.die(source);
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
