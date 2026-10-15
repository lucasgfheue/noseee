package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dibuja el dron del ENJAMBRE: un cuerpo metálico diminuto con núcleo verde y aspas que giran. */
public class SwarmDroneRenderer extends EntityRenderer<SwarmDroneEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public SwarmDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SwarmDroneEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(SwarmDroneEntity drone, float yaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        float time = drone.tickCount + partialTick;
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        Vec3 c = new Vec3(0, 0.1, 0);
        cube(vc, m, c, 0.07, 0.30f, 0.34f, 0.38f, 1.0f);                       // chasis
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.8f);
        cube(vc, m, c, 0.045 * pulse, 0.35f, 1.0f, 0.5f, 1.0f);                 // núcleo verde
        cube(vc, m, c, 0.11 * pulse, 0.2f, 0.9f, 0.4f, 0.3f);                   // halo
        for (int i = 0; i < 4; i++) {                                           // aspas
            double a = time * 0.9 + i * Math.PI / 2;
            Vec3 p = new Vec3(Math.cos(a) * 0.14, 0.1, Math.sin(a) * 0.14);
            cube(vc, m, p, 0.025, 0.7f, 0.75f, 0.8f, 1.0f);
        }
    }

    private static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, double h, float r, float g, float b, float a) {
        Vec3 p000 = new Vec3(c.x - h, c.y - h, c.z - h), p100 = new Vec3(c.x + h, c.y - h, c.z - h);
        Vec3 p110 = new Vec3(c.x + h, c.y + h, c.z - h), p010 = new Vec3(c.x - h, c.y + h, c.z - h);
        Vec3 p001 = new Vec3(c.x - h, c.y - h, c.z + h), p101 = new Vec3(c.x + h, c.y - h, c.z + h);
        Vec3 p111 = new Vec3(c.x + h, c.y + h, c.z + h), p011 = new Vec3(c.x - h, c.y + h, c.z + h);
        quad(vc, m, p000, p100, p110, p010, r, g, b, a);
        quad(vc, m, p001, p101, p111, p011, r, g, b, a);
        quad(vc, m, p000, p001, p011, p010, r, g, b, a);
        quad(vc, m, p100, p101, p111, p110, r, g, b, a);
        quad(vc, m, p000, p100, p101, p001, r, g, b, a);
        quad(vc, m, p010, p110, p111, p011, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float r, float g, float b, float a) {
        vertex(vc, m, p0, r, g, b, a); vertex(vc, m, p1, r, g, b, a);
        vertex(vc, m, p2, r, g, b, a); vertex(vc, m, p3, r, g, b, a);
        vertex(vc, m, p3, r, g, b, a); vertex(vc, m, p2, r, g, b, a);
        vertex(vc, m, p1, r, g, b, a); vertex(vc, m, p0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
