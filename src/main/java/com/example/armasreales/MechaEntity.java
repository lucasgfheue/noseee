package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mecha de Hierro (ficticio): robot bípedo de hierro con un solo piloto en la cabeza.
 *  - W/S avanzan, A/D giran. Ctrl + W corre. Espacio salta (hasta 12 bloques).
 *  - Manteniendo Espacio 3 s se activa el jetpack (necesita combustible en el almacenamiento): sigue
 *    pulsado para subir y suéltalo para planear hacia abajo.
 *  - Tecla U: abre el inventario (un hueco de munición por brazo y 32 de almacenamiento).
 *  - Clic izquierdo: dispara con el brazo que toca (alternando) la munición de ese brazo.
 *  - Destruye al instante los bloques a menos de 4 bloques mientras se mueve.
 */
public class MechaEntity extends TankEntity {
    public static final int INVENTORY_SIZE = 34;          // 0 = brazo derecho, 1 = brazo izquierdo, 2..33 = almacenamiento
    private static final double JUMP_VELOCITY = 1.5;       // unos 12 bloques de altura
    private static final int FLIGHT_HOLD_TICKS = 60;       // 3 s con Espacio pulsado
    private static final double RADIUS = 4.0;
    private static final float AIM_LIMIT = 80.0f;             // los brazos apuntan casi a cualquier altura
    private static final int BOOST_FUEL_FACTOR = 3;           // los propulsores gastan el triple

    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_RIGHT_ARM =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BOOST =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_AUTO =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_AUTO_TARGET =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_AIM_YAW =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_PITCH =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);

    public final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    // Teclas del piloto (las pone el cliente local)
    private boolean clientJump;
    private boolean clientSprint;
    private boolean clientJumpWas;

    // Estado del servidor
    private boolean jumpHeldServer;
    private boolean sprintHeldServer;
    private int jumpHeldTicks;
    private int flyTicks;

    // Modo automático (servidor): quién nos atacó y cuándo deja de contar
    private final Map<UUID, Long> attackers = new HashMap<>();

    // Animación de la marcha (cliente)
    public float walkPhase, walkPhaseO, walkAmp, walkAmpO;

    public MechaEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LAST_SHOT, -1000L);
        builder.define(DATA_RIGHT_ARM, true);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_BOOST, false);
        builder.define(DATA_AUTO, false);
        builder.define(DATA_AUTO_TARGET, false);
        builder.define(DATA_AIM_YAW, 0.0f);
        builder.define(DATA_AIM_PITCH, 0.0f);
    }

    /** Ticks (con decimales) desde el último disparo, para el retroceso del brazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    /** true si el último disparo salió del brazo derecho. */
    public boolean lastShotRight() {
        return this.entityData.get(DATA_RIGHT_ARM);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public boolean isAuto() {
        return this.entityData.get(DATA_AUTO);
    }

    /** true si el modo automático está apuntando a un objetivo (los brazos giran hacia él). */
    public boolean hasAutoTarget() {
        return this.entityData.get(DATA_AUTO_TARGET);
    }

    public float autoAimYaw() {
        return this.entityData.get(DATA_AIM_YAW);
    }

    public float autoAimPitch() {
        return this.entityData.get(DATA_AIM_PITCH);
    }

    public void toggleAuto(Player player) {
        boolean on = !this.isAuto();
        this.entityData.set(DATA_AUTO, on);
        if (!on) {
            this.entityData.set(DATA_AUTO_TARGET, false);
        }
        player.displayClientMessage(Component.literal(on ? "Modo automático: ACTIVADO" : "Modo automático: desactivado"), true);
    }

    /** Anota a quien nos ha hecho daño (a nosotros o al piloto) para que el modo automático le responda. */
    public void noteAttacker(LivingEntity attacker) {
        if (attacker != this && !this.hasPassenger(attacker)) {
            this.attackers.put(attacker.getUUID(), this.level().getGameTime() + 600L);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            this.noteAttacker(attacker);
        }
        return super.hurt(source, amount);
    }

    /** true mientras vuela con los propulsores a tope (Ctrl + W). */
    public boolean isBoosting() {
        return this.entityData.get(DATA_BOOST);
    }

    public int fuelSecondsLeft() {
        return this.entityData.get(DATA_FUEL) / 20;
    }

    public void setClientInput(boolean jump, boolean sprint) {
        this.clientJump = jump;
        this.clientSprint = sprint;
    }

    /** Límite de elevación de los brazos (también lo usa el renderizador). */
    public static final float ARM_AIM_LIMIT = AIM_LIMIT;

    // Geometría (debe coincidir con MechaRenderer): hombros a 2,4 del modelo y brazos a 1,25 a cada lado
    @Override
    protected double seatTopY() {
        return 2.4 * SCALE;
    }

    @Override
    protected double pivotY() {
        return 2.15 * SCALE;
    }

    @Override
    protected double pivotForward() {
        return 0.3 * SCALE;
    }

    @Override
    protected double barrelLength() {
        return 1.6 * SCALE;
    }

    @Override
    protected int crushHeight() {
        return (int) Math.ceil(3.2 * SCALE);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    // ------------------------------------------------------------------ Movimiento (lo calcula el cliente del piloto)

    @Override
    protected float getRiddenSpeed(Player player) {
        float speed = super.getRiddenSpeed(player);
        return (this.clientSprint && player.zza > 0.0f) ? speed * 1.9f : speed;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        boolean flying = this.isFlying();
        Vec3 dm = this.getDeltaMovement();
        if (flying) {
            double vy = dm.y;
            if (this.clientJump) {
                vy = Math.min(vy + 0.16, 0.55);               // empuje del jetpack
            } else {
                vy = Math.max(vy, -0.25);                      // planeo suave
            }
            this.setDeltaMovement(dm.x, vy, dm.z);
        } else if (this.clientJump && !this.clientJumpWas && this.onGround()) {
            this.setDeltaMovement(dm.x, JUMP_VELOCITY, dm.z);
        }
        this.clientJumpWas = this.clientJump;
        if (!this.onGround()) {
            // Control en el aire (salto y jetpack)
            Vec3 f = Vec3.directionFromRotation(0.0f, this.getYRot());
            double target = 2.2 * this.getRiddenSpeed(player) * (flying ? 2.0 : 1.0) * Math.max(0.0f, player.zza);
            if (flying && this.clientSprint) {
                target *= 1.7;                                 // propulsores
            }
            Vec3 now = this.getDeltaMovement();
            this.setDeltaMovement(now.x + (f.x * target - now.x) * 0.2, now.y, now.z + (f.z * target - now.z) * 0.2);
        }
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.updateWalk();
        } else if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        }
    }

    private void updateWalk() {
        this.walkPhaseO = this.walkPhase;
        this.walkAmpO = this.walkAmp;
        double yawRad = Math.toRadians(this.getYRot());
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double forward = dx * -Math.sin(yawRad) + dz * Math.cos(yawRad);
        double dist = Math.sqrt(dx * dx + dz * dz);
        float turn = Math.abs(Mth.wrapDegrees(this.getYRot() - this.yRotO));
        this.walkPhase += (float) (forward * 85.0);             // un ciclo completo cada ~4 bloques
        if (this.onGround() && turn > 0.5f && dist < 0.02) {
            this.walkPhase += 9.0f;                              // pasos al girar en el sitio
        }
        float target = Mth.clamp((float) (dist / 0.12) + (turn > 0.5f ? 0.5f : 0.0f), 0.0f, 1.0f);
        this.walkAmp += (target - this.walkAmp) * 0.35f;
    }

    private void serverTick(ServerLevel level) {
        Player pilot = this.getControllingPassenger() instanceof Player p ? p : null;
        if (pilot == null) {
            this.jumpHeldServer = false;
            this.sprintHeldServer = false;
        }
        this.jumpHeldTicks = this.jumpHeldServer ? this.jumpHeldTicks + 1 : 0;
        this.autoTick(level, pilot);

        boolean boost = this.isFlying() && this.sprintHeldServer;
        if (boost != this.isBoosting()) {
            this.entityData.set(DATA_BOOST, boost);
        }
        if (!this.isFlying()) {
            if (pilot != null && this.jumpHeldTicks >= FLIGHT_HOLD_TICKS) {
                if (this.entityData.get(DATA_FUEL) > 0 || this.consumeFuel()) {
                    this.entityData.set(DATA_FLYING, true);
                    this.flyTicks = 0;
                    level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                            SoundSource.PLAYERS, 2.0f, 0.6f);
                } else if (this.jumpHeldTicks == FLIGHT_HOLD_TICKS) {
                    pilot.displayClientMessage(Component.literal("Sin combustible: pon carbón, madera o lava en el almacenamiento (U)"), true);
                }
            }
            return;
        }

        // Jetpack encendido
        this.flyTicks++;
        int fuel = this.entityData.get(DATA_FUEL) - (boost ? BOOST_FUEL_FACTOR : 1);
        this.entityData.set(DATA_FUEL, Math.max(fuel, 0));
        boolean out = fuel <= 0 && !this.consumeFuel();
        if (pilot == null || out || (this.flyTicks > 10 && this.onGround())) {
            this.entityData.set(DATA_FLYING, false);
            if (out && pilot != null) {
                pilot.displayClientMessage(Component.literal("Jetpack sin combustible"), true);
            }
            return;
        }
        if (this.tickCount % 2 == 0) {
            Vec3 flat = Vec3.directionFromRotation(0.0f, this.getYRot());
            Vec3 left = new Vec3(flat.z, 0.0, -flat.x);
            Vec3 base = this.position().add(0.0, 1.9 * SCALE, 0.0).add(flat.scale(-0.8 * SCALE));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = base.add(left.scale(s * 0.35 * SCALE));
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.1, 0.05, 0.1, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y - 0.2, p.z, 1, 0.1, 0.05, 0.1, 0.02);
            }
            if (boost) {
                Vec3 pods = this.position().add(0.0, 1.5 * SCALE, 0.0).add(flat.scale(-1.1 * SCALE));
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = pods.add(left.scale(s * 0.75 * SCALE));
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.15, 0.1, 0.15, 0.05);
                    level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.2, 0.1, 0.2, 0.03);
                }
            }
        }
        if (this.tickCount % 8 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ Combustible

    private static int fuelValue(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 400;
        }
        if (stack.is(Items.COAL_BLOCK)) {
            return 4000;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return 2400;
        }
        if (stack.is(ItemTags.LOGS)) {
            return 150;
        }
        if (stack.is(ItemTags.PLANKS)) {
            return 100;
        }
        return 0;
    }

    /** Gasta una unidad de combustible del almacenamiento; false si no hay. */
    private boolean consumeFuel() {
        for (int i = 2; i < INVENTORY_SIZE; i++) {
            ItemStack stack = this.inventory.getItem(i);
            int value = fuelValue(stack);
            if (value <= 0) {
                continue;
            }
            if (stack.is(Items.LAVA_BUCKET)) {
                this.inventory.setItem(i, new ItemStack(Items.BUCKET));
            } else {
                stack.shrink(1);
                this.inventory.setChanged();
            }
            this.entityData.set(DATA_FUEL, value);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Entradas del cliente

    public void handleInput(ServerPlayer player, int action, boolean value) {
        if (this.getFirstPassenger() != player) {
            return;
        }
        if (action == MechaInputPayload.JUMP) {
            this.jumpHeldServer = value;
        } else if (action == MechaInputPayload.SPRINT) {
            this.sprintHeldServer = value;
        } else if (action == MechaInputPayload.OPEN_INVENTORY && value) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MechaMenu(id, inv, this.inventory, this.getId()),
                    Component.literal("Mecha de Hierro")), buf -> buf.writeVarInt(this.getId()));
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.jumpHeldServer = false;
        this.sprintHeldServer = false;
        this.entityData.set(DATA_FLYING, false);
        this.entityData.set(DATA_BOOST, false);
    }

    // ------------------------------------------------------------------ Destrucción alrededor

    @Override
    protected void crushBlocks(ServerLevel level) {
        Vec3 d = this.getDeltaMovement();
        boolean moving = d.lengthSqr() > 1.0E-4;
        boolean turning = this.getControllingPassenger() instanceof Player p && Math.abs(p.xxa) > 0.0f;
        if (!moving && !turning && !this.horizontalCollision) {
            return;
        }
        int cx = Mth.floor(this.getX());
        int cz = Mth.floor(this.getZ());
        int baseY = Mth.floor(this.getY() + 0.1);
        int height = this.crushHeight();
        int r = (int) Math.ceil(RADIUS);
        int destroyed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double ddx = cx + dx + 0.5 - this.getX();
                double ddz = cz + dz + 0.5 - this.getZ();
                if (ddx * ddx + ddz * ddz > RADIUS * RADIUS) {
                    continue;
                }
                for (int dy = 0; dy < height; dy++) {
                    pos.set(cx + dx, baseY + dy, cz + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (destroyed++ < 30) {
                        level.destroyBlock(pos, false);
                    } else {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Modo automático

    private boolean isThreat(LivingEntity e, Player pilot, long now) {
        if (e == this || e == pilot || !e.isAlive() || this.hasPassenger(e) || e instanceof TankEntity) {
            return false;
        }
        Long until = this.attackers.get(e.getUUID());
        if (until != null && until > now) {
            return true;                                        // nos atacó a nosotros o al piloto
        }
        if (e instanceof Mob mob && (mob.getTarget() == pilot || mob.getTarget() == this)) {
            return true;                                        // quiere atacarnos
        }
        return e instanceof Enemy;                              // cualquier entidad hostil
    }

    private void autoTick(ServerLevel level, Player pilot) {
        if (!this.isAuto() || !(pilot instanceof ServerPlayer sp)) {
            if (this.hasAutoTarget()) {
                this.entityData.set(DATA_AUTO_TARGET, false);
            }
            return;
        }
        long now = level.getGameTime();
        if (this.tickCount % 20 == 0) {
            this.attackers.values().removeIf(t -> t <= now);
        }
        if (this.tickCount % 4 != 0) {
            return;
        }
        Vec3 from = this.position().add(0.0, this.pivotY(), 0.0);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(48.0),
                x -> this.isThreat(x, sp, now))) {
            if (!this.hasLineOfSight(e)) {
                continue;
            }
            double d = e.distanceToSqr(this);
            Long until = this.attackers.get(e.getUUID());
            boolean urgent = (until != null && until > now) || (e instanceof Mob m && (m.getTarget() == sp || m.getTarget() == this));
            double score = urgent ? d * 0.25 : d;               // primero los que nos atacan
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) {
            if (this.hasAutoTarget()) {
                this.entityData.set(DATA_AUTO_TARGET, false);
            }
            return;
        }
        Vec3 d = best.getBoundingBox().getCenter().subtract(from);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
        float pitch = (float) (-Math.toDegrees(Mth.atan2(d.y, horizontal)));
        if (!this.hasAutoTarget()) {
            this.entityData.set(DATA_AUTO_TARGET, true);
        }
        if (Math.abs(Mth.wrapDegrees(yaw - this.autoAimYaw())) > 1.0f) {
            this.entityData.set(DATA_AIM_YAW, yaw);
        }
        if (Math.abs(pitch - this.autoAimPitch()) > 1.0f) {
            this.entityData.set(DATA_AIM_PITCH, pitch);
        }
        if (this.cannonCooldown <= 0 && !this.tryFire(sp, yaw, pitch)) {
            this.cannonCooldown = 20;                            // sin munición: reintenta más tarde
        }
    }

    // ------------------------------------------------------------------ Munición y disparo

    public static boolean isAmmo(ItemStack stack) {
        return MechaAmmo.isAmmo(stack);
    }

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        if (!this.tryFire(player, player.getYRot(), player.getXRot())) {
            player.serverLevel().playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            this.cannonCooldown = 10;
        }
    }

    /** Dispara con el brazo que toca (o el otro si ese no tiene munición) hacia yaw/pitch; false si no pudo. */
    private boolean tryFire(ServerPlayer player, float yaw, float pitch) {
        boolean creative = player.getAbilities().instabuild;
        boolean first = !this.lastShotRight();
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean right = attempt == 0 ? first : !first;
            ItemStack ammo = this.inventory.getItem(right ? 0 : 1);
            if (ammo.isEmpty() ? !creative : !isAmmo(ammo)) {
                continue;
            }
            int cooldown = this.shoot(player, right, ammo.isEmpty() ? new ItemStack(Items.TNT) : ammo, yaw, pitch);
            if (cooldown <= 0) {
                continue;
            }
            if (!creative && !ammo.isEmpty()) {
                ammo.shrink(1);
                this.inventory.setChanged();
            }
            this.entityData.set(DATA_RIGHT_ARM, right);
            this.entityData.set(DATA_LAST_SHOT, this.level().getGameTime());
            this.cannonCooldown = cooldown;
            return true;
        }
        return false;
    }

    /** Dispara un proyectil con la munición dada; devuelve la espera en ticks (0 si no pudo). */
    private int shoot(ServerPlayer player, boolean right, ItemStack ammo, float yawIn, float pitchIn) {
        ServerLevel level = player.serverLevel();
        float pitch = Mth.clamp(pitchIn, -AIM_LIMIT, AIM_LIMIT);
        float yaw = yawIn;
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);                   // hacia la derecha
        double lateral = (right ? 1.25 : -1.25) * SCALE;
        Vec3 pivot = this.position().add(0.0, this.pivotY(), 0.0)
                .add(flat.scale(this.pivotForward())).add(side.scale(lateral));
        Vec3 muzzle = pivot.add(dir.scale(this.barrelLength()));

        return MechaAmmo.fire(this, player, level, muzzle, dir, ammo);
    }

    // ------------------------------------------------------------------ Interacción, guardado y muerte

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DyeItem dye) {      // cada tinte cambia el diseño y el modelo
            int skin = dye.getDyeColor().getId() + 1;
            if (skin == this.getSkin()) {
                return InteractionResult.PASS;
            }
            return this.applySkin(player, held, skin);
        }
        if (held.is(Items.IRON_INGOT) && this.getSkin() != SKIN_DEFAULT) {
            return this.applySkin(player, held, SKIN_DEFAULT);     // un lingote de hierro devuelve el diseño original
        }
        if (player.isSecondaryUseActive()) {
            if (player.getItemInHand(hand).isEmpty() && !this.inventory.isEmpty()) {
                if (!this.level().isClientSide) {
                    player.displayClientMessage(Component.literal("Vacía su inventario antes de recogerlo"), true);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return super.mobInteract(player, hand);        // Shift + mano vacía: lo recoge
        }
        if (this.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private InteractionResult applySkin(Player player, ItemStack held, int skin) {
        if (!this.level().isClientSide) {
            this.setSkin(skin);
            this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("MechaInv", this.inventory.createTag(this.level().registryAccess()));
        tag.putBoolean("Auto", this.isAuto());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.inventory.fromTag(tag.getList("MechaInv", 10), this.level().registryAccess());
        this.entityData.set(DATA_AUTO, tag.getBoolean("Auto"));
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this.blockPosition(), this.inventory);
        }
        super.die(source);
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.MECHA_HIERRO.get();
    }
}
