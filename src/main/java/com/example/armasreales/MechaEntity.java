package com.example.armasreales;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Mecha de Hierro (ficticio): robot bípedo de hierro con un solo piloto en la cabeza.
 * W/S avanzan y retroceden, A/D giran las piernas, el torso, la cabeza y los brazos siguen la mirada del piloto
 * y el clic izquierdo dispara obuses de TNT desde un brazo y luego desde el otro. Sube escalones de 2 bloques,
 * aplasta lo que se cruce y sólo le hacen daño las explosiones de TNT (como a los tanques).
 */
public class MechaEntity extends TankEntity {
    private static final int RELOAD_TICKS = 16;

    private static final EntityDataAccessor<Long> DATA_LAST_SHOT =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_RIGHT_ARM =
            SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BOOLEAN);

    public MechaEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TankEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LAST_SHOT, -1000L);
        builder.define(DATA_RIGHT_ARM, true);
    }

    /** Ticks (con decimales) desde el último disparo, para el retroceso del brazo. */
    public float ticksSinceShot(float partialTick) {
        return (this.level().getGameTime() - this.entityData.get(DATA_LAST_SHOT)) + partialTick;
    }

    /** true si el último disparo salió del brazo derecho. */
    public boolean lastShotRight() {
        return this.entityData.get(DATA_RIGHT_ARM);
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
    protected double crushHalfLength() {
        return 1.7;
    }

    @Override
    protected double crushHalfWidth() {
        return 2.0;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        boolean right = !this.lastShotRight();
        if (!this.fireShell(player, (right ? 1.25 : -1.25) * SCALE)) {
            this.cannonCooldown = 10;
            return;
        }
        this.entityData.set(DATA_RIGHT_ARM, right);
        this.entityData.set(DATA_LAST_SHOT, this.level().getGameTime());
        this.cannonCooldown = RELOAD_TICKS;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof DyeItem) {
            return InteractionResult.PASS;                 // el mecha no se tiñe
        }
        if (player.isSecondaryUseActive()) {
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
    protected Item getPickupItem() {
        return ModItems.MECHA_HIERRO.get();
    }
}
