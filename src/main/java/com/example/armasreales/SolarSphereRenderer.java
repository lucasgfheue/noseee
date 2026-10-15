package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dibuja la esfera solar: malla de anillos naranjas, núcleo brillante y el círculo del área de efecto. */
public class SolarSphereRenderer extends EntityRenderer<SolarSphereEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public SolarSphereRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SolarSphereEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(SolarSphereEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(SolarSphereEntity sun, float yaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        float time = sun.tickCount + partialTick;
        float appear = Mth.clamp(time / 10.0f, 0.0f, 1.0f);
        float fadeOut = 1.0f - Mth.clamp((time - (SolarSphereEntity.DURATION - 20)) / 20.0f, 0.0f, 1.0f);
        float f = appear * fadeOut;
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();

        // Círculo del área de efecto en el suelo
        double R = SolarSphereEntity.RADIUS * (0.8 + 0.2 * appear);
        ring(vc, m, R, 0.08, 0.05, 1.0f, 0.55f, 0.1f, 0.5f * f, 64, 0.0);
        ring(vc, m, R * 0.6, 0.08, 0.035, 1.0f, 0.7f, 0.2f, 0.35f * f, 48, time * 0.01);
        ring(vc, m, R * 0.25, 0.08, 0.03, 1.0f, 0.85f, 0.4f, 0.35f * f, 32, -time * 0.02);

        // Esfera de anillos (paralelos y meridianos que giran)
        double hy = SolarSphereEntity.HEIGHT;
        double rad = (1.0 + 0.06 * Mth.sin(time * 0.3f)) * (0.4 + 0.6 * appear);
        for (int k = -2; k <= 2; k++) {
            double yy = hy + k * rad * 0.4;
            double rr = Math.sqrt(Math.max(0.0, rad * rad - (k * rad * 0.4) * (k * rad * 0.4)));
            ring(vc, m, rr, yy, 0.03, 1.0f, 0.65f, 0.15f, 0.7f * f, 24, time * 0.04);
        }
        for (int j = 0; j < 4; j++) {
            double ang = time * 0.04 + j * Math.PI / 4;
            Vec3 prev = null;
            for (int i = 0; i <= 24; i++) {
                double t = 2 * Math.PI * i / 24;
                Vec3 p = new Vec3(Math.cos(t) * rad * Math.cos(ang), hy + Math.sin(t) * rad, Math.cos(t) * rad * Math.sin(ang));
                if (prev != null) {
                    line(vc, m, prev, p, 0.03, 1.0f, 0.75f, 0.25f, 0.6f * f);
                }
                prev = p;
            }
        }
        // Núcleo
        Vec3 c = new Vec3(0, hy, 0);
        float pulse = 0.9f + 0.1f * Mth.sin(time * 0.6f);
        cube(vc, m, c, 0.28 * pulse, 1.0f, 1.0f, 0.9f, 0.95f * f);
        cube(vc, m, c, 0.5 * pulse, 1.0f, 0.8f, 0.25f, 0.5f * f);
        cube(vc, m, c, 0.8 * pulse, 1.0f, 0.5f, 0.1f, 0.22f * f);
    }

    private static void ring(VertexConsumer vc, Matrix4f m, double r, double y, double w,
                             float cr, float cg, float cb, float ca, int seg, double rot) {
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg + rot;
            Vec3 p = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
            if (prev != null) {
                line(vc, m, prev, p, w, cr, cg, cb, ca);
            }
            prev = p;
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r,
                             float cr, float cg, float cb, float ca) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-5) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        Vec3 u = dn.cross(new Vec3(0, 1, 0)).normalize().scale(r);   // ancho horizontal
        Vec3 up = new Vec3(0, r, 0);
        quad(vc, m, a.add(u), a.subtract(u), b.subtract(u), b.add(u), cr, cg, cb, ca);
        quad(vc, m, a.add(up), a.subtract(up), b.subtract(up), b.add(up), cr, cg, cb, ca);
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
