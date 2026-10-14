package com.example.armasreales;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Zombi vanilla (modelo, armadura, objetos en la mano) con una piel distinta por variante. */
public class ArmedZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[ArmedZombie.VARIANTS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/zombi_" + i + ".png");
        }
    }

    public ArmedZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie zombie) {
        if (zombie instanceof ArmedZombie armed) {
            return TEXTURES[armed.getVariant()];
        }
        return super.getTextureLocation(zombie);
    }
}
