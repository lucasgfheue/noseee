package com.example.armasreales;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Teclas del piloto del mecha: espacio (salto / jetpack), Ctrl (correr) y U (inventario). */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, value = Dist.CLIENT)
public final class MechaClientEvents {
    private static boolean jumpWas = false;
    private static boolean sprintWas = false;

    private MechaClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getVehicle() instanceof MechaEntity mecha)) {
            while (MechaKeys.OPEN_INVENTORY.consumeClick()) {
                // sin mecha: se descarta
            }
            if (jumpWas) {
                jumpWas = false;
                PacketDistributor.sendToServer(new MechaInputPayload(MechaInputPayload.JUMP, false));
            }
            if (sprintWas) {
                sprintWas = false;
                PacketDistributor.sendToServer(new MechaInputPayload(MechaInputPayload.SPRINT, false));
            }
            return;
        }
        boolean free = mc.screen == null && mc.mouseHandler.isMouseGrabbed();
        boolean jump = free && mc.options.keyJump.isDown();
        boolean sprint = free && mc.options.keySprint.isDown() && mc.options.keyUp.isDown();
        mecha.setClientInput(jump, sprint);
        if (jump || jumpWas) {
            PacketDistributor.sendToServer(new MechaInputPayload(MechaInputPayload.JUMP, jump));
        }
        jumpWas = jump;
        if (sprint != sprintWas) {
            PacketDistributor.sendToServer(new MechaInputPayload(MechaInputPayload.SPRINT, sprint));
            sprintWas = sprint;
        }
        while (MechaKeys.OPEN_INVENTORY.consumeClick()) {
            if (mc.screen == null) {
                PacketDistributor.sendToServer(new MechaInputPayload(MechaInputPayload.OPEN_INVENTORY, true));
            }
        }
    }
}
