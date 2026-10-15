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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dibuja el escudo del Fusil Anillo: un casquete de esfera celeste translúcido con rejilla hexagonal brillante. */
public class ShieldRenderer extends EntityRenderer<ShieldEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    private static final int YAW_STEPS = 14;
    private static final int PITCH_STEPS = 9;
    private static final double YAW_SPAN = Math.toRadians(110);    // ancho total
    private static final double PITCH_SPAN = Math.toRadians(90);   // alto total

    public ShieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShieldEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ShieldEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ShieldEntity shield, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        Entity owner = shield.level().getEntity(shield.getOwnerId());
        if (owner == null) {
            return;
        }
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 up0 = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up0);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = shield.getPosition(partialTick);

        float time = shield.tickCount + partialTick;
        float life = 1.0f - Mth.clamp((time - (ShieldEntity.DURATION - 12)) / 12.0f, 0.0f, 1.0f);   // se apaga al final
        float grow = Mth.clamp(time / 4.0f, 0.0f, 1.0f);                                          // aparece
        float pulse = 0.9f + 0.1f * Mth.sin(time * 0.35f);
        float fade = life * grow * pulse;
        double r = ShieldEntity.RADIUS * (0.6 + 0.4 * grow);
        Vec3 center = eye.add(look.scale(ShieldEntity.DISTANCE));

        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();

        Vec3[][] pts = new Vec3[YAW_STEPS + 1][PITCH_STEPS + 1];
        for (int i = 0; i <= YAW_STEPS; i++) {
            double a = (i / (double) YAW_STEPS - 0.5) * YAW_SPAN;
            for (int j = 0; j <= PITCH_STEPS; j++) {
                double b = (j / (double) PITCH_STEPS - 0.5) * PITCH_SPAN;
                // Punto sobre la esfera: el "centro" apunta al jugador, la cúpula se curva hacia él
                Vec3 dir = look.scale(-Math.cos(a) * Math.cos(b))
                        .add(right.scale(Math.sin(a) * Math.cos(b)))
                        .add(up.scale(Math.sin(b)));
                Vec3 world = center.add(dir.scale(r));
                pts[i][j] = world.subtract(origin);
            }
        }

        // Cristal translúcido
        for (int i = 0; i < YAW_STEPS; i++) {
            for (int j = 0; j < PITCH_STEPS; j++) {
                float edge = 1.0f - (float) Math.abs((i + 0.5) / YAW_STEPS - 0.5) * 0.9f;
                float alpha = (0.13f + 0.07f * ((i + j) % 2)) * fade * edge;
                quad(vc, m, pts[i][j], pts[i + 1][j], pts[i + 1][j + 1], pts[i][j + 1], 0.30f, 0.80f, 1.0f, alpha);
            }
        }
        // Rejilla brillante
        float lineA = 0.75f * fade;
        for (int i = 0; i <= YAW_STEPS; i += 2) {
            for (int j = 0; j < PITCH_STEPS; j++) {
                line(vc, m, pts[i][j], pts[i][j + 1], 0.012, 0.55f, 0.92f, 1.0f, lineA);
            }
        }
        for (int j = 0; j <= PITCH_STEPS; j += 2) {
            for (int i = 0; i < YAW_STEPS; i++) {
                line(vc, m, pts[i][j], pts[i + 1][j], 0.012, 0.55f, 0.92f, 1.0f, lineA);
            }
        }
        // Borde más intenso
        for (int i = 0; i < YAW_STEPS; i++) {
            line(vc, m, pts[i][0], pts[i + 1][0], 0.022, 0.85f, 1.0f, 1.0f, 0.9f * fade);
            line(vc, m, pts[i][PITCH_STEPS], pts[i + 1][PITCH_STEPS], 0.022, 0.85f, 1.0f, 1.0f, 0.9f * fade);
        }
        for (int j = 0; j < PITCH_STEPS; j++) {
            line(vc, m, pts[0][j], pts[0][j + 1], 0.022, 0.85f, 1.0f, 1.0f, 0.9f * fade);
            line(vc, m, pts[YAW_STEPS][j], pts[YAW_STEPS][j + 1], 0.022, 0.85f, 1.0f, 1.0f, 0.9f * fade);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r,
                             float cr, float cg, float cb, float ca) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dn.cross(ref).normalize().scale(r);
        Vec3 v = dn.cross(u).normalize().scale(r);
        Vec3 a1 = a.add(u), a2 = a.subtract(u), b1 = b.add(u), b2 = b.subtract(u);
        quad(vc, m, a1, a2, b2, b1, cr, cg, cb, ca);
        Vec3 c1 = a.add(v), c2 = a.subtract(v), d1 = b.add(v), d2 = b.subtract(v);
        quad(vc, m, c1, c2, d2, d1, cr, cg, cb, ca);
    }

    /** Cuadrilátero visible por las dos caras. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float r, float g, float b, float a) {
        vertex(vc, m, p0, r, g, b, a);
        vertex(vc, m, p1, r, g, b, a);
        vertex(vc, m, p2, r, g, b, a);
        vertex(vc, m, p3, r, g, b, a);
        vertex(vc, m, p3, r, g, b, a);
        vertex(vc, m, p2, r, g, b, a);
        vertex(vc, m, p1, r, g, b, a);
        vertex(vc, m, p0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
