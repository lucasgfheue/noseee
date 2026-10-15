package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dibuja los disparos como líneas brillantes cortas (celeste, naranja o chispa) en la dirección de vuelo. */
public class ElectricShotRenderer extends EntityRenderer<ElectricShot> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public ElectricShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ElectricShot entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ElectricShot entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ElectricShot shot, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        if (shot.getKind() == ElectricShot.ORB) {
            renderOrb(shot, partialTick, poseStack, buffer);
            return;
        }
        Vec3 v = shot.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 dir = v.normalize();
        int kind = shot.getKind();
        double half = kind == ElectricShot.SPARK ? 0.25 : 0.9;
        float[] core, mid, halo;
        if (kind == ElectricShot.BIO) {              // magenta orgánico
            core = new float[]{1.0f, 0.85f, 1.0f, 0.95f};
            mid = new float[]{0.95f, 0.25f, 0.85f, 0.6f};
            halo = new float[]{0.65f, 0.05f, 0.65f, 0.25f};
        } else if (kind == ElectricShot.TRACER) {          // naranja brillante
            core = new float[]{1.0f, 0.95f, 0.75f, 0.95f};
            mid = new float[]{1.0f, 0.55f, 0.08f, 0.55f};
            halo = new float[]{1.0f, 0.35f, 0.0f, 0.22f};
        } else if (kind == ElectricShot.SPARK) {     // chispa de fuego
            core = new float[]{1.0f, 0.95f, 0.6f, 0.95f};
            mid = new float[]{1.0f, 0.5f, 0.05f, 0.6f};
            halo = new float[]{0.9f, 0.2f, 0.0f, 0.25f};
        } else {                                      // celeste
            core = new float[]{0.92f, 1.0f, 1.0f, 0.95f};
            mid = new float[]{0.35f, 0.85f, 1.0f, 0.55f};
            halo = new float[]{0.15f, 0.55f, 1.0f, 0.22f};
        }
        Vec3 a = dir.scale(-half);
        Vec3 b = dir.scale(half);
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        double k = kind == ElectricShot.SPARK ? 0.6 : 1.0;
        tube(vc, m, a, b, 0.012 * k, core);
        tube(vc, m, a, b, 0.03 * k, mid);
        tube(vc, m, a, b, 0.065 * k, halo);
    }

    /** Esfera de energía gravitatoria: núcleo blanco, halo cian y un aro que gira. */
    private void renderOrb(ElectricShot shot, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        float t = (shot.tickCount + partialTick) * 0.5f;
        Vec3 c = new Vec3(0, shot.getBbHeight() / 2.0, 0);
        cube(vc, m, c, 0.07, new float[]{1.0f, 1.0f, 1.0f, 0.98f});
        cube(vc, m, c, 0.13, new float[]{0.35f, 0.95f, 1.0f, 0.60f});
        cube(vc, m, c, 0.22, new float[]{0.10f, 0.60f, 1.0f, 0.25f});
        int seg = 12;
        double rr = 0.34;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg + t;
            Vec3 p = c.add(Math.cos(a) * rr, Math.sin(a) * rr * 0.35, Math.sin(a) * rr);
            if (prev != null) {
                tube(vc, m, prev, p, 0.018, new float[]{0.6f, 1.0f, 1.0f, 0.9f});
            }
            prev = p;
        }
    }

    private static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, double h, float[] col) {
        Vec3 p000 = new Vec3(c.x - h, c.y - h, c.z - h), p100 = new Vec3(c.x + h, c.y - h, c.z - h);
        Vec3 p110 = new Vec3(c.x + h, c.y + h, c.z - h), p010 = new Vec3(c.x - h, c.y + h, c.z - h);
        Vec3 p001 = new Vec3(c.x - h, c.y - h, c.z + h), p101 = new Vec3(c.x + h, c.y - h, c.z + h);
        Vec3 p111 = new Vec3(c.x + h, c.y + h, c.z + h), p011 = new Vec3(c.x - h, c.y + h, c.z + h);
        quad(vc, m, p000, p100, p110, p010, col);
        quad(vc, m, p001, p101, p111, p011, col);
        quad(vc, m, p000, p001, p011, p010, col);
        quad(vc, m, p100, p101, p111, p110, col);
        quad(vc, m, p000, p100, p101, p001, col);
        quad(vc, m, p010, p110, p111, p011, col);
    }

    private static void tube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r, float[] c) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 w = dn.cross(u);
        Vec3[] k = {
                u.scale(r).add(w.scale(r)),
                u.scale(-r).add(w.scale(r)),
                u.scale(-r).add(w.scale(-r)),
                u.scale(r).add(w.scale(-r))
        };
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = k[i];
            Vec3 c1 = k[(i + 1) % 4];
            quad(vc, m, a.add(c0), a.add(c1), b.add(c1), b.add(c0), c);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] c) {
        vertex(vc, m, p0, c); vertex(vc, m, p1, c); vertex(vc, m, p2, c); vertex(vc, m, p3, c);
        vertex(vc, m, p3, c); vertex(vc, m, p2, c); vertex(vc, m, p1, c); vertex(vc, m, p0, c);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float[] c) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(c[0], c[1], c[2], c[3]);
    }
}
