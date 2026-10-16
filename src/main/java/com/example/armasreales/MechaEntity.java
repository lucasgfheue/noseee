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

    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_RIGHT_ARM =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);

    public final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    // Teclas del piloto (las pone el cliente local)
    private boolean clientJump;
    private boolean clientSprint;
    private boolean clientJumpWas;

    // Estado del servidor
    private boolean jumpHeldServer;
    private int jumpHeldTicks;
    private int flyTicks;

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

    public int fuelSecondsLeft() {
        return this.entityData.get(DATA_FUEL) / 20;
    }

    public void setClientInput(boolean jump, boolean sprint) {
        this.clientJump = jump;
        this.clientSprint = sprint;
    }

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
        }
        this.jumpHeldTicks = this.jumpHeldServer ? this.jumpHeldTicks + 1 : 0;

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
        int fuel = this.entityData.get(DATA_FUEL) - 1;
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
        this.entityData.set(DATA_FLYING, false);
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

    // ------------------------------------------------------------------ Munición y disparo

    public static boolean isAmmo(ItemStack stack) {
        return stack.is(Items.TNT) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.ARROW)
                || stack.is(ModItems.MUNICION_PISTOLA.get()) || stack.is(ModItems.MUNICION_FUSIL.get())
                || stack.is(ModItems.MUNICION_FRANCOTIRADOR.get()) || stack.is(ModItems.MUNICION_MAGNUM.get());
    }

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        boolean first = !this.lastShotRight();
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean right = attempt == 0 ? first : !first;
            ItemStack ammo = this.inventory.getItem(right ? 0 : 1);
            if (ammo.isEmpty() ? !creative : !isAmmo(ammo)) {
                continue;
            }
            int cooldown = this.shoot(player, right, ammo.isEmpty() ? new ItemStack(Items.TNT) : ammo);
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
            return;
        }
        player.serverLevel().playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL,
                SoundSource.PLAYERS, 1.0f, 0.7f);
        this.cannonCooldown = 10;
    }

    /** Dispara un proyectil con la munición dada; devuelve la espera en ticks (0 si no pudo). */
    private int shoot(ServerPlayer player, boolean right, ItemStack ammo) {
        ServerLevel level = player.serverLevel();
        float pitch = Mth.clamp(player.getXRot(), MIN_PITCH, MAX_PITCH);
        float yaw = player.getYRot();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);                   // hacia la derecha
        double lateral = (right ? 1.25 : -1.25) * SCALE;
        Vec3 pivot = this.position().add(0.0, this.pivotY(), 0.0)
                .add(flat.scale(this.pivotForward())).add(side.scale(lateral));
        Vec3 muzzle = pivot.add(dir.scale(this.barrelLength()));

        int cooldown;
        if (ammo.is(Items.TNT)) {
            TntProjectile shell = new TntProjectile(level, player);
            shell.setPos(muzzle.x, muzzle.y - shell.getBbHeight() / 2.0, muzzle.z);
            shell.shoot(dir.x, dir.y, dir.z, 3.2f, 0.0f);
            level.addFreshEntity(shell);
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                    SoundSource.PLAYERS, 3.0f, 0.5f);
            cooldown = 16;
        } else if (ammo.is(Items.FIRE_CHARGE)) {
            LargeFireball fireball = new LargeFireball(level, player, dir, 2);
            fireball.setPos(muzzle.x, muzzle.y, muzzle.z);
            level.addFreshEntity(fireball);
            level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.6f);
            cooldown = 14;
        } else if (ammo.is(Items.ARROW)) {
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            arrow.setPos(muzzle.x, muzzle.y, muzzle.z);
            arrow.shoot(dir.x, dir.y, dir.z, 4.0f, 0.0f);
            arrow.setBaseDamage(6.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(arrow);
            level.playSound(null, this.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 2.0f, 0.7f);
            cooldown = 6;
        } else {
            float damage;
            if (ammo.is(ModItems.MUNICION_FRANCOTIRADOR.get())) {
                damage = 45.0f;
                cooldown = 30;
            } else if (ammo.is(ModItems.MUNICION_MAGNUM.get())) {
                damage = 20.0f;
                cooldown = 10;
            } else if (ammo.is(ModItems.MUNICION_FUSIL.get())) {
                damage = 10.0f;
                cooldown = 4;
            } else {
                damage = 8.0f;
                cooldown = 5;
            }
            Bullets.Hit hit = Bullets.trace(level, player, muzzle, dir, 120.0,
                    e -> e != this && e != player);
            if (hit.entity() != null) {
                hit.entity().hurt(level.damageSources().playerAttack(player), damage);
            }
            for (double dd = 2.0; dd < hit.distance(); dd += 2.0) {
                Vec3 p = muzzle.add(dir.scale(dd));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS, 2.0f, 0.8f);
        }
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.15, 0.15, 0.15, 0.02);
        return cooldown;
    }

    // ------------------------------------------------------------------ Interacción, guardado y muerte

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;                 // el mecha no se tiñe
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("MechaInv", this.inventory.createTag(this.level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.inventory.fromTag(tag.getList("MechaInv", 10), this.level().registryAccess());
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
