package com.example.armasreales;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Tanque de cañón doble: cada click dispara una ráfaga de 2 proyectiles, uno por cañón. */
public class TwinTankEntity extends TankEntity {
    /** Separación lateral de cada cañón respecto al centro (en bloques). */
    public static final double BARREL_OFFSET = 0.28 * SCALE;
    private static final int BURST_DELAY = 4;
    private static final int BURST_COOLDOWN = 60;

    private int pendingDelay = 0;

    public TwinTankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void fireCannon(ServerPlayer player) {
        if (this.getControllingPassenger() != player || this.cannonCooldown > 0) {
            return;
        }
        if (!this.fireShell(player, BARREL_OFFSET)) {
            this.cannonCooldown = 10;
            return;
        }
        this.pendingDelay = BURST_DELAY;
        this.cannonCooldown = BURST_COOLDOWN;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.pendingDelay > 0 && --this.pendingDelay == 0
                && this.getControllingPassenger() instanceof ServerPlayer player) {
            this.fireShell(player, -BARREL_OFFSET);
        }
    }

    @Override
    protected Item getPickupItem() {
        return ModItems.TANQUE_DOBLE.get();
    }
}
