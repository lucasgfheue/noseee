package com.example.armasreales;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Tecla U: abre el inventario del mecha. */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MechaKeys {
    public static final KeyMapping OPEN_INVENTORY = new KeyMapping(
            "key.armasreales.mecha_inventario", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U,
            "key.categories.armasreales");

    private MechaKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_INVENTORY);
    }
}
