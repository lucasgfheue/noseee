package com.example.armasreales;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Pantalla del inventario del mecha (dibujada con rectángulos, sin textura). */
public class MechaScreen extends AbstractContainerScreen<MechaMenu> {
    public MechaScreen(MechaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 228;
        this.inventoryLabelY = 133;
    }

    private Button autoButton;

    @Override
    protected void init() {
        super.init();
        this.autoButton = Button.builder(Component.literal("Auto: NO"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(this.leftPos + 100, this.topPos + 3, 68, 13).build();
        this.addRenderableWidget(this.autoButton);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF22252B);
        g.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF9AA0AB);
        g.fill(x + 4, y + 16, x + this.imageWidth - 4, y + 48, 0xFF7C828E);
        for (Slot slot : this.menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF373737);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0x202020, false);
        g.drawString(this.font, "Brazo izq.", 3, 40, 0x202020, false);
        String right = "Brazo der.";
        g.drawString(this.font, right, this.imageWidth - 3 - this.font.width(right), 40, 0x202020, false);
        MechaEntity mecha = this.menu.mecha();
        int seconds = mecha == null ? 0 : mecha.fuelSecondsLeft();
        String fuel = "Combustible: " + seconds + " s";
        g.drawString(this.font, fuel, (this.imageWidth - this.font.width(fuel)) / 2, 24, 0x202020, false);
        String mun = "Munición";
        g.drawString(this.font, mun, (this.imageWidth - this.font.width(mun)) / 2, 34, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0x202020, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MechaEntity mecha = this.menu.mecha();
        if (this.autoButton != null && mecha != null) {
            this.autoButton.setMessage(Component.literal(mecha.isAuto() ? "Auto: SÍ" : "Auto: NO"));
        }
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
