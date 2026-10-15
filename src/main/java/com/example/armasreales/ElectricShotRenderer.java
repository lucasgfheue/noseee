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
        Vec3 v = shot.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 dir = v.normalize();
        int kind = shot.getKind();
        double half = kind == ElectricShot.SPARK ? 0.25 : 0.9;
        float[] core, mid, halo;
        if (kind == ElectricShot.TRACER) {          // naranja brillante
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
