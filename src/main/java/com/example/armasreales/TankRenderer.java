package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dibuja el tanque con cajas de colores (sin archivos de modelo).
 *
 * Sistema de coordenadas del casco: X = lateral (+X = izquierda), Y = arriba, Z = hacia delante.
 * Mide 3 de largo (Z de -1.5 a 1.5), 2 de ancho (X de -1 a 1) y 2 de alto (con la antena).
 * Las ruedas giran con los ángulos que calcula TankEntity; la torreta y el cañón siguen la mira del conductor.
 */
public class TankRenderer extends EntityRenderer<TankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque.png");

    private static final int[] OLIVE = {72, 90, 52};
    private static final int[] DARK_OLIVE = {50, 64, 38};
    private static final int[] TRACK = {34, 34, 36};
    private static final int[] WHEEL = {62, 64, 68};
    private static final int[] STEEL = {96, 100, 108};
    private static final int[] BLACK = {22, 22, 24};
    private static final int[] YELLOW = {235, 190, 40};
    private static final int[] RED = {200, 40, 30};

    /** Posición (Z) de las 4 ruedas de cada lado. */
    private static final float[] WHEEL_Z = {-1.1f, -0.37f, 0.37f, 1.1f};
    private static final float WHEEL_Y = 0.34f;
    private static final float WHEEL_X = 0.8f;      // centro de cada oruga (a ambos lados)

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(1.5));
    }

    @Override
    public void render(TankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        ps.pushPose();
        // El casco mira hacia +Z local: rotar -yaw hace que +Z coincida con el frente del tanque
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL);
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR);

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);

        // Torreta y cañón: siguen hacia donde mira el conductor
        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch);

        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Piezas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int side = 1; side >= -1; side -= 2) {          // +1 = izquierda, -1 = derecha
            float x1 = side > 0 ? 0.6f : -1.0f;
            float x2 = side > 0 ? 1.0f : -0.6f;
            box(ps, vc, light, x1, 0.0f, -1.5f, x2, 0.07f, 1.5f, TRACK);    // oruga inferior
            box(ps, vc, light, x1, 0.64f, -1.5f, x2, 0.71f, 1.5f, TRACK);   // oruga superior
            box(ps, vc, light, x1 + 0.02f, 0.05f, -1.5f, x2 - 0.02f, 0.66f, -1.42f, TRACK);   // extremos
            box(ps, vc, light, x1 + 0.02f, 0.05f, 1.42f, x2 - 0.02f, 0.66f, 1.5f, TRACK);

            float angle = side > 0 ? angleL : angleR;
            for (float z : WHEEL_Z) {
                wheel(ps, vc, light, side * WHEEL_X, WHEEL_Y, z, angle, side);
            }
            // Guardabarros
            box(ps, vc, light, x1, 0.71f, -1.5f, x2, 0.79f, 1.5f, DARK_OLIVE);
        }
    }

    /** Rueda octogonal con una tuerca amarilla descentrada: se nota cuando gira. */
    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz,
                       float angle, int side) {
        final float h = 0.2f;     // medio ancho
        final float r = 0.24f;    // mitad del lado (la diagonal llega a ~0.34)
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -r, -r, h, r, r, WHEEL);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -r, -r, h, r, r, WHEEL);
        ps.popPose();
        float bx1 = side > 0 ? h : -h - 0.03f;
        float bx2 = side > 0 ? h + 0.03f : -h;
        box(ps, vc, light, bx1, 0.08f, -0.05f, bx2, 0.2f, 0.05f, YELLOW);
        ps.popPose();
    }

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.6f, 0.22f, -1.4f, 0.6f, 0.95f, 1.4f, OLIVE);              // casco
        box(ps, vc, light, -0.45f, 0.95f, -1.3f, 0.45f, 1.02f, -0.55f, STEEL);           // rejilla del motor
        box(ps, vc, light, -0.6f, 0.95f, 0.8f, 0.6f, 1.0f, 1.4f, DARK_OLIVE);            // placa delantera
        box(ps, vc, light, -0.5f, 0.62f, 1.4f, -0.34f, 0.78f, 1.44f, YELLOW);            // faros
        box(ps, vc, light, 0.34f, 0.62f, 1.4f, 0.5f, 0.78f, 1.44f, YELLOW);
        box(ps, vc, light, -0.5f, 0.62f, -1.44f, -0.34f, 0.74f, -1.4f, RED);             // luces traseras
        box(ps, vc, light, 0.34f, 0.62f, -1.44f, 0.5f, 0.74f, -1.4f, RED);
        box(ps, vc, light, 0.3f, 0.4f, -1.55f, 0.46f, 0.56f, -1.4f, STEEL);              // escape
    }

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch) {
        ps.pushPose();
        ps.translate(0.0, 0.95, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        box(ps, vc, light, -0.55f, 0.0f, -0.65f, 0.55f, 0.55f, 0.55f, OLIVE);            // torreta
        box(ps, vc, light, -0.28f, 0.14f, 0.55f, 0.28f, 0.44f, 0.72f, DARK_OLIVE);       // mantelete
        box(ps, vc, light, -0.4f, 0.1f, -0.9f, 0.4f, 0.45f, -0.65f, DARK_OLIVE);         // caja trasera
        // Escotilla (anillo): por aquí asoma el conductor
        box(ps, vc, light, -0.42f, 0.55f, 0.26f, 0.42f, 0.68f, 0.32f, DARK_OLIVE);
        box(ps, vc, light, -0.42f, 0.55f, -0.32f, 0.42f, 0.68f, -0.26f, DARK_OLIVE);
        box(ps, vc, light, 0.36f, 0.55f, -0.26f, 0.42f, 0.68f, 0.26f, DARK_OLIVE);
        box(ps, vc, light, -0.42f, 0.55f, -0.26f, -0.36f, 0.68f, 0.26f, DARK_OLIVE);
        box(ps, vc, light, 0.46f, 0.55f, -0.6f, 0.5f, 1.05f, -0.56f, BLACK);             // antena

        // Cañón: gira hacia arriba/abajo alrededor del mantelete
        ps.translate(0.0, 0.29, 0.72);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        box(ps, vc, light, -0.085f, -0.085f, 0.0f, 0.085f, 0.085f, 1.4f, STEEL);
        box(ps, vc, light, -0.125f, -0.125f, 1.1f, 0.125f, 0.125f, 1.4f, DARK_OLIVE);    // freno de boca
        box(ps, vc, light, -0.05f, -0.05f, 1.4f, 0.05f, 0.05f, 1.41f, BLACK);            // boca
        ps.popPose();
    }

    // ------------------------------------------------------------------ Geometría

    /** Caja de color liso. Las normales son las de cada cara, para que la luz del juego la sombree. */
    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, int[] c) {
        PoseStack.Pose pose = ps.last();
        quad(pose, vc, light, c, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);       // arriba
        quad(pose, vc, light, c, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);      // abajo
        quad(pose, vc, light, c, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);      // atrás
        quad(pose, vc, light, c, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);       // delante
        quad(pose, vc, light, c, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);      // derecha
        quad(pose, vc, light, c, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);       // izquierda
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, vc, light, c, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, c, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, c, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, c, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], 255)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
