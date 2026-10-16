package com.example.armasreales;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Menú de asientos del tanque: cinco botones arriba a la derecha de la pantalla. */
public class TankSeatScreen extends Screen {
    private static final String[] NAMES = {
            "Torreta (conducir y disparar)", "Interior 1 - delante izq.", "Interior 2 - delante der.",
            "Interior 3 - detrás izq.", "Interior 4 - detrás der."};

    private final TankEntity tank;
    private final Button[] buttons = new Button[TankEntity.SEAT_COUNT];

    public TankSeatScreen(TankEntity tank) {
        super(Component.literal("Asientos"));
        this.tank = tank;
    }

    public static void open(TankEntity tank) {
        Minecraft.getInstance().setScreen(new TankSeatScreen(tank));
    }

    @Override
    protected void init() {
        int w = 190;
        int x = this.width - w - 8;
        for (int i = 0; i < TankEntity.SEAT_COUNT; i++) {
            final int seat = i;
            this.buttons[i] = Button.builder(this.label(i), b -> {
                PacketDistributor.sendToServer(new SeatPayload(this.tank.getId(), seat));
                this.onClose();
            }).bounds(x, 22 + i * 24, w, 20).build();
            this.addRenderableWidget(this.buttons[i]);
        }
    }

    private Component label(int seat) {
        boolean free = this.tank.isSeatFree(seat);
        return Component.literal((seat + 1) + ". " + NAMES[seat] + (free ? "" : " (ocupado)"));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.tank.isAlive() || this.tank.isRemoved() || !this.tank.hasFreeSeat()) {
            this.onClose();
            return;
        }
        for (int i = 0; i < TankEntity.SEAT_COUNT; i++) {
            if (this.buttons[i] != null) {
                this.buttons[i].active = this.tank.isSeatFree(i);
                this.buttons[i].setMessage(this.label(i));
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // sin fondo oscuro: se sigue viendo el tanque
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, "Elige asiento", this.width - 190 - 8, 9, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
