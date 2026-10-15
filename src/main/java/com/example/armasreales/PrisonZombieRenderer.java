package com.example.armasreales;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Zombi vanilla con la piel del prisionero, el policía, el SWAT o el caminante. */
public class PrisonZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation[] TEXTURES = {
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/zombi_prisionero.png"),
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/zombi_policia.png"),
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/zombi_swat.png"),
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/zombi_caminante.png")};

    public PrisonZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie zombie) {
        if (zombie instanceof PrisonZombie p && p.getRole() < TEXTURES.length) {
            return TEXTURES[p.getRole()];
        }
        return super.getTextureLocation(zombie);
    }
}
