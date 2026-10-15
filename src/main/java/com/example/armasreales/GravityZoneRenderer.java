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

/** Dibuja la zona Horizonte: anillos cian y brazos en espiral sobre el suelo, con un núcleo brillante. */
public class GravityZoneRenderer extends EntityRenderer<GravityZoneEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public GravityZoneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityZoneEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GravityZoneEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(GravityZoneEntity zone, float yaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        float time = zone.tickCount + partialTick;
        float appear = Mth.clamp(time / 8.0f, 0.0f, 1.0f);
        float fadeOut = 1.0f - Mth.clamp((time - (GravityZoneEntity.DURATION - 15)) / 15.0f, 0.0f, 1.0f);
        float f = appear * fadeOut;
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        double y0 = 0.08;

        // Anillos concéntricos (el exterior marca el límite de la zona)
        double[] radii = {GravityZoneEntity.RADIUS, GravityZoneEntity.RADIUS * 0.62, GravityZoneEntity.RADIUS * 0.3};
        for (int k = 0; k < radii.length; k++) {
            double r = radii[k] * (0.85 + 0.15 * appear);
            float alpha = (k == 0 ? 0.55f : 0.4f) * f;
            ring(vc, m, r, y0, k == 0 ? 0.05 : 0.035, 0.30f, 0.90f, 1.0f, alpha, 48, 0.0);
        }
        // Brazos en espiral que giran
        for (int arm = 0; arm < 3; arm++) {
            Vec3 prev = null;
            double base = time * 0.06 + arm * 2 * Math.PI / 3;
            for (int i = 0; i <= 24; i++) {
                double r = GravityZoneEntity.RADIUS * (1.0 - i / 24.0) * (0.85 + 0.15 * appear);
                double a = base + i * 0.22;
                Vec3 p = new Vec3(Math.cos(a) * r, y0 + 0.02, Math.sin(a) * r);
                if (prev != null) {
                    line(vc, m, prev, p, 0.03, 0.55f, 0.95f, 1.0f, 0.45f * f * (1.0f - i / 30.0f));
                }
                prev = p;
            }
        }
        // Núcleo
        Vec3 c = new Vec3(0, 1.0, 0);
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.5f);
        cube(vc, m, c, 0.10 * pulse, 1.0f, 1.0f, 1.0f, 0.95f * f);
        cube(vc, m, c, 0.22 * pulse, 0.35f, 0.95f, 1.0f, 0.5f * f);
        cube(vc, m, c, 0.40 * pulse, 0.10f, 0.55f, 1.0f, 0.2f * f);
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
