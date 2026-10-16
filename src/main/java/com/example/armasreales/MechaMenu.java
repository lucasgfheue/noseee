package com.example.armasreales;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Inventario del mecha: 0 = brazo derecho, 1 = brazo izquierdo, 2..33 = almacenamiento (32 huecos). */
public class MechaMenu extends AbstractContainerMenu {
    private final Container container;
    private final Player player;
    private final int mechaId;

    public MechaMenu(int id, Inventory playerInventory, Container container, int mechaId) {
        super(ModMenus.MECHA.get(), id);
        this.container = container;
        this.player = playerInventory.player;
        this.mechaId = mechaId;
        container.startOpen(playerInventory.player);

        this.addSlot(new AmmoSlot(container, 0, 152, 20));
        this.addSlot(new AmmoSlot(container, 1, 8, 20));
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 8; c++) {
                this.addSlot(new Slot(container, 2 + r * 8 + c, 16 + c * 18, 52 + r * 18));
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInventory, 9 + c + r * 9, 8 + c * 18, 144 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInventory, c, 8 + c * 18, 202));
        }
    }

    public MechaEntity mecha() {
        return this.player.level().getEntity(this.mechaId) instanceof MechaEntity m ? m : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int containerEnd = MechaEntity.INVENTORY_SIZE;
            if (index < containerEnd) {
                if (!this.moveItemStackTo(stack, containerEnd, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, containerEnd, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    /** Botón del modo automático (id 0). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        MechaEntity mecha = this.mecha();
        if (id == 0 && mecha != null) {
            mecha.toggleAuto(player);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        MechaEntity mecha = this.mecha();
        return mecha != null && mecha.isAlive() && player.isPassenger() && player.getVehicle() == mecha;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /** Hueco de un brazo: sólo acepta munición. */
    private static class AmmoSlot extends Slot {
        AmmoSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return MechaEntity.isAmmo(stack);
        }
    }
}
