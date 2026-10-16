package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
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

    /** Colores del casco y la torreta por skin: 0 original, 1 negro, 2 azul, 3 amarillo, 4 verde, 5 rojo. */
    private static final int[][] MAIN = {
            {72, 90, 52}, {44, 44, 48}, {52, 92, 170}, {226, 186, 40}, {64, 170, 70}, {190, 45, 40}};
    /** Tono más oscuro de cada skin (guardabarros, detalles). */
    private static final int[][] DARK = {
            {50, 64, 38}, {24, 24, 27}, {32, 60, 120}, {170, 135, 25}, {36, 112, 44}, {130, 28, 25}};

    /** Material: color de teñido, casilla de la textura (atlas 4x4 de 64 px) y si brilla. */
    private record Mat(int[] c, int tile, boolean glow) {
        Mat(int[] c, int tile) {
            this(c, tile, false);
        }
    }

    private static final int T_SKIN0 = 8;
    private static final int T_PLATE = 0, T_STEEL = 1, T_TRACK = 2, T_HUB = 3, T_GRILLE = 4, T_FLAT = 5, T_RUBBER = 6;

    private static final Mat TRACK = new Mat(new int[]{150, 150, 155}, T_TRACK);
    private static final Mat WHEEL = new Mat(new int[]{190, 190, 195}, T_HUB);
    private static final Mat STEEL = new Mat(new int[]{235, 238, 245}, T_STEEL);
    private static final Mat GUN = new Mat(new int[]{190, 196, 206}, T_STEEL);
    private static final Mat GRILLE = new Mat(new int[]{210, 210, 215}, T_GRILLE);
    private static final Mat BLACK = new Mat(new int[]{120, 120, 124}, T_RUBBER);
    private static final Mat YELLOW = new Mat(new int[]{255, 214, 80}, T_FLAT, true);
    private static final Mat RED = new Mat(new int[]{230, 50, 40}, T_FLAT, true);
    private static final Mat GLASS = new Mat(new int[]{70, 110, 120}, T_FLAT);
    private static final Mat CABLE = new Mat(new int[]{60, 60, 62}, T_RUBBER);

    private static final Mat BLUE = new Mat(new int[]{60, 110, 255}, T_FLAT, true);
    private static final Mat ORANGE = new Mat(new int[]{255, 150, 30}, T_FLAT, true);

    /** Skin que se está dibujando (el render siempre corre en un solo hilo). */
    private int curSkin;

    /** Posición (Z) de las 4 ruedas de cada lado. */
    private static final float[] WHEEL_Z = {-1.1f, -0.37f, 0.37f, 1.1f};
    private static final float WHEEL_Y = 0.34f;
    private static final float WHEEL_X = 0.8f;      // centro de cada oruga (a ambos lados)

    /** Si es true dibuja dos cañones paralelos. */
    private final boolean twin;

    public TankRenderer(EntityRendererProvider.Context context, boolean twin) {
        super(context);
        this.twin = twin;
        this.shadowRadius = 1.3f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(TankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        ps.pushPose();
        // El casco mira hacia +Z local: rotar -yaw hace que +Z coincida con el frente del tanque
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // El modelo está dibujado en 3x2x2; la escala lo lleva al tamaño real del tanque
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        int skin = Mth.clamp(tank.getSkin(), 0, MAIN.length - 1);
        this.curSkin = skin;
        // Cada skin tiene su propia textura (casillas 8 a 13 del atlas): el diseño cambia, no sólo el color
        Mat main = new Mat(new int[]{255, 255, 255}, T_SKIN0 + skin);
        Mat dark = new Mat(new int[]{150, 150, 156}, T_SKIN0 + skin);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL);
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR);

        drawTracks(ps, vc, light, angleL, angleR, dark);
        drawHull(ps, vc, light, main, dark);

        // Torreta y cañón: siguen hacia donde mira el conductor
        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, main, dark);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Piezas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR, Mat dark) {
        for (int side = 1; side >= -1; side -= 2) {          // +1 = izquierda, -1 = derecha
            float x1 = side > 0 ? 0.6f : -1.0f;
            float x2 = side > 0 ? 1.0f : -0.6f;
            box(ps, vc, light, x1, 0.0f, -1.5f, x2, 0.07f, 1.5f, TRACK);    // oruga inferior
            box(ps, vc, light, x1, 0.64f, -1.5f, x2, 0.71f, 1.5f, TRACK);   // oruga superior
            box(ps, vc, light, x1 + 0.02f, 0.05f, -1.5f, x2 - 0.02f, 0.66f, -1.42f, TRACK);   // extremos
            box(ps, vc, light, x1 + 0.02f, 0.05f, 1.42f, x2 - 0.02f, 0.66f, 1.5f, TRACK);
            // Zapatas de la oruga (relieve) sobre el tramo inferior y superior
            float gx1 = side > 0 ? 0.6f : -1.0f;
            float gx2 = side > 0 ? 1.0f : -0.6f;
            for (int i = 0; i < 12; i++) {
                float z = -1.45f + i * 0.25f;
                box(ps, vc, light, gx1 - 0.01f, 0.0f, z, gx2 + 0.01f, 0.03f, z + 0.12f, BLACK);
                box(ps, vc, light, gx1 - 0.01f, 0.68f, z, gx2 + 0.01f, 0.72f, z + 0.12f, BLACK);
            }
            float angle = side > 0 ? angleL : angleR;
            for (float z : WHEEL_Z) {
                wheel(ps, vc, light, side * WHEEL_X, WHEEL_Y, z, angle, side);
            }
            // Rodillos de retorno (fijos) bajo el guardabarros
            float ox = side > 0 ? 0.74f : -0.94f;
            for (float z : new float[]{-0.75f, 0.0f, 0.75f}) {
                box(ps, vc, light, ox, 0.56f, z - 0.07f, ox + 0.2f, 0.66f, z + 0.07f, STEEL);
            }
            // Guardabarros con labio exterior y aleta delantera/trasera
            box(ps, vc, light, x1, 0.71f, -1.5f, x2, 0.79f, 1.5f, dark);
            float lipX1 = side > 0 ? 0.97f : -1.0f;
            float lipX2 = side > 0 ? 1.0f : -0.97f;
            box(ps, vc, light, lipX1, 0.79f, -1.5f, lipX2, 0.86f, 1.5f, dark);
            box(ps, vc, light, x1, 0.79f, 1.3f, x2, 0.83f, 1.58f, dark);
            box(ps, vc, light, x1, 0.79f, -1.58f, x2, 0.83f, -1.3f, dark);
        }
    }

    /** Rueda octogonal con buje y tuerca descentrada: se nota cuando gira. */
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
        // Llanta de goma (anillo más oscuro y más estrecho por fuera)
        box(ps, vc, light, -h - 0.01f, -0.05f, -0.05f, h + 0.01f, 0.05f, 0.05f, STEEL);
        float bx1 = side > 0 ? h : -h - 0.03f;
        float bx2 = side > 0 ? h + 0.03f : -h;
        box(ps, vc, light, bx1, 0.08f, -0.05f, bx2, 0.2f, 0.05f, YELLOW);
        box(ps, vc, light, bx1, -0.2f, -0.05f, bx2, -0.08f, 0.05f, STEEL);
        box(ps, vc, light, bx1, -0.05f, 0.08f, bx2, 0.05f, 0.2f, STEEL);
        box(ps, vc, light, bx1, -0.05f, -0.2f, bx2, 0.05f, -0.08f, STEEL);
        ps.popPose();
    }

    private void drawHull(PoseStack ps, VertexConsumer vc, int light, Mat main, Mat dark) {
        box(ps, vc, light, -0.6f, 0.22f, -1.4f, 0.6f, 0.95f, 1.4f, main);              // casco
        // Glacis escalonado en la parte delantera
        box(ps, vc, light, -0.6f, 0.95f, 1.0f, 0.6f, 1.0f, 1.4f, dark);
        box(ps, vc, light, -0.6f, 0.62f, 1.4f, 0.6f, 0.95f, 1.46f, main);
        box(ps, vc, light, -0.6f, 0.4f, 1.46f, 0.6f, 0.62f, 1.5f, dark);
        // Cubierta del motor con rejilla
        box(ps, vc, light, -0.45f, 0.95f, -1.3f, 0.45f, 1.0f, -0.55f, GRILLE);
        box(ps, vc, light, -0.5f, 0.95f, -1.35f, -0.45f, 1.03f, -0.5f, dark);
        box(ps, vc, light, 0.45f, 0.95f, -1.35f, 0.5f, 1.03f, -0.5f, dark);
        box(ps, vc, light, -0.5f, 0.95f, -1.35f, 0.5f, 1.03f, -1.3f, dark);
        box(ps, vc, light, -0.5f, 0.95f, -0.55f, 0.5f, 1.03f, -0.5f, dark);
        // Faros con carcasa, protector y luz brillante
        for (float x : new float[]{-0.5f, 0.34f}) {
            box(ps, vc, light, x - 0.02f, 0.6f, 1.46f, x + 0.18f, 0.8f, 1.5f, STEEL);
            box(ps, vc, light, x, 0.62f, 1.5f, x + 0.16f, 0.78f, 1.52f, YELLOW);
        }
        // Luces traseras
        box(ps, vc, light, -0.5f, 0.62f, -1.44f, -0.34f, 0.74f, -1.4f, RED);
        box(ps, vc, light, 0.34f, 0.62f, -1.44f, 0.5f, 0.74f, -1.4f, RED);
        // Escapes dobles
        box(ps, vc, light, 0.3f, 0.4f, -1.55f, 0.46f, 0.56f, -1.4f, STEEL);
        box(ps, vc, light, -0.46f, 0.4f, -1.55f, -0.3f, 0.56f, -1.4f, STEEL);
        box(ps, vc, light, 0.33f, 0.43f, -1.57f, 0.43f, 0.53f, -1.55f, BLACK);
        box(ps, vc, light, -0.43f, 0.43f, -1.57f, -0.33f, 0.53f, -1.55f, BLACK);
        // Ganchos de remolque
        box(ps, vc, light, -0.35f, 0.3f, 1.5f, -0.25f, 0.4f, 1.56f, STEEL);
        box(ps, vc, light, 0.25f, 0.3f, 1.5f, 0.35f, 0.4f, 1.56f, STEEL);
        // Cable de remolque y eslabones de repuesto en el lateral del casco
        for (int side = -1; side <= 1; side += 2) {
            float xa = side > 0 ? 0.6f : -0.64f;
            float xb = side > 0 ? 0.64f : -0.6f;
            box(ps, vc, light, xa, 0.74f, -1.2f, xb, 0.78f, 1.2f, CABLE);
            box(ps, vc, light, xa, 0.46f, -1.2f, xb, 0.74f, -1.14f, CABLE);
            box(ps, vc, light, xa, 0.46f, 1.14f, xb, 0.74f, 1.2f, CABLE);
        }
        hullExtras(ps, vc, light, main, dark);
        // Cajas de almacenaje y bidones en la cubierta trasera
        box(ps, vc, light, 0.3f, 1.0f, -1.3f, 0.58f, 1.14f, -0.9f, dark);
        box(ps, vc, light, 0.3f, 1.14f, -1.28f, 0.58f, 1.16f, -0.92f, STEEL);
        box(ps, vc, light, -0.58f, 1.0f, -1.3f, -0.46f, 1.2f, -1.1f, RED);
        box(ps, vc, light, -0.58f, 1.0f, -1.08f, -0.46f, 1.2f, -0.88f, RED);
        box(ps, vc, light, -0.57f, 1.2f, -1.26f, -0.47f, 1.23f, -1.14f, BLACK);
        box(ps, vc, light, -0.57f, 1.2f, -1.04f, -0.47f, 1.23f, -0.92f, BLACK);
    }

    /** Accesorios propios de cada skin en el casco. */
    private void hullExtras(PoseStack ps, VertexConsumer vc, int light, Mat main, Mat dark) {
        switch (this.curSkin) {
            case 3 -> {      // amarillo: barra de empuje y topes de obra
                box(ps, vc, light, -0.58f, 0.3f, 1.5f, 0.58f, 0.4f, 1.62f, BLACK);
                for (int i = -2; i <= 2; i++) {
                    box(ps, vc, light, i * 0.22f - 0.04f, 0.28f, 1.62f, i * 0.22f + 0.04f, 0.42f, 1.66f, YELLOW);
                }
            }
            case 4 -> {      // verde: sacos de arena sobre el frente
                for (int i = 0; i < 4; i++) {
                    box(ps, vc, light, -0.5f + i * 0.26f, 1.0f, 1.05f, -0.28f + i * 0.26f, 1.1f, 1.3f, dark);
                }
                for (int i = 0; i < 3; i++) {
                    box(ps, vc, light, -0.37f + i * 0.26f, 1.1f, 1.08f, -0.15f + i * 0.26f, 1.18f, 1.27f, dark);
                }
            }
            case 5 -> {      // rojo: púas frontales
                for (int i = -3; i <= 3; i++) {
                    box(ps, vc, light, i * 0.16f - 0.03f, 0.45f, 1.5f, i * 0.16f + 0.03f, 0.56f, 1.72f, STEEL);
                    box(ps, vc, light, i * 0.16f - 0.015f, 0.47f, 1.72f, i * 0.16f + 0.015f, 0.54f, 1.8f, STEEL);
                }
            }
            case 1 -> {      // negro: visera de faldones laterales oscuros
                for (int side = -1; side <= 1; side += 2) {
                    float xa = side > 0 ? 1.0f : -1.04f;
                    float xb = side > 0 ? 1.04f : -1.0f;
                    box(ps, vc, light, xa, 0.4f, -1.3f, xb, 0.74f, 1.3f, BLACK);
                }
            }
            default -> { }
        }
    }

    /** Accesorios propios de cada skin en la torreta (ya dentro de su sistema de coordenadas). */
    private void turretExtras(PoseStack ps, VertexConsumer vc, int light, Mat main, Mat dark) {
        switch (this.curSkin) {
            case 1 -> {      // negro: visores rojos
                box(ps, vc, light, -0.4f, 0.28f, 0.55f, -0.12f, 0.33f, 0.58f, RED);
                box(ps, vc, light, 0.12f, 0.28f, 0.55f, 0.4f, 0.33f, 0.58f, RED);
            }
            case 2 -> {      // azul: barra de luces
                box(ps, vc, light, -0.3f, 0.55f, -0.12f, 0.3f, 0.6f, 0.0f, BLACK);
                box(ps, vc, light, -0.28f, 0.6f, -0.1f, -0.02f, 0.66f, -0.02f, RED);
                box(ps, vc, light, 0.02f, 0.6f, -0.1f, 0.28f, 0.66f, -0.02f, BLUE);
            }
            case 3 -> {      // amarillo: baliza naranja
                box(ps, vc, light, -0.08f, 0.55f, -0.5f, 0.08f, 0.6f, -0.34f, BLACK);
                box(ps, vc, light, -0.06f, 0.6f, -0.48f, 0.06f, 0.72f, -0.36f, ORANGE);
            }
            case 4 -> {      // verde: red de camuflaje sobre el techo
                box(ps, vc, light, -0.45f, 0.55f, -0.58f, 0.2f, 0.6f, -0.3f, dark);
                box(ps, vc, light, -0.3f, 0.6f, -0.5f, 0.05f, 0.64f, -0.36f, dark);
            }
            case 5 -> {      // rojo: cuernos laterales
                box(ps, vc, light, 0.55f, 0.35f, 0.3f, 0.62f, 0.5f, 0.4f, STEEL);
                box(ps, vc, light, 0.62f, 0.45f, 0.32f, 0.7f, 0.62f, 0.38f, STEEL);
                box(ps, vc, light, -0.62f, 0.35f, 0.3f, -0.55f, 0.5f, 0.4f, STEEL);
                box(ps, vc, light, -0.7f, 0.45f, 0.32f, -0.62f, 0.62f, 0.38f, STEEL);
            }
            default -> { }
        }
    }

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch,
                            Mat main, Mat dark) {
        ps.pushPose();
        ps.translate(0.0, 0.95, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        box(ps, vc, light, -0.55f, 0.0f, -0.65f, 0.55f, 0.45f, 0.4f, main);             // torreta
        box(ps, vc, light, -0.5f, 0.45f, -0.6f, 0.5f, 0.55f, 0.3f, main);               // techo
        box(ps, vc, light, -0.5f, 0.05f, 0.4f, 0.5f, 0.4f, 0.55f, main);                // frente biselado
        float mw = twin ? 0.42f : 0.28f;
        box(ps, vc, light, -mw, 0.14f, 0.55f, mw, 0.44f, 0.72f, dark);                  // mantelete
        box(ps, vc, light, -mw - 0.04f, 0.1f, 0.7f, mw + 0.04f, 0.48f, 0.75f, dark);
        box(ps, vc, light, -0.4f, 0.1f, -0.9f, 0.4f, 0.45f, -0.65f, dark);              // caja trasera
        box(ps, vc, light, -0.3f, 0.3f, -0.93f, 0.3f, 0.4f, -0.9f, STEEL);
        // Escotilla (anillo): por aquí asoma el conductor
        box(ps, vc, light, -0.42f, 0.55f, 0.26f, 0.42f, 0.68f, 0.32f, dark);
        box(ps, vc, light, -0.42f, 0.55f, -0.32f, 0.42f, 0.68f, -0.26f, dark);
        box(ps, vc, light, 0.36f, 0.55f, -0.26f, 0.42f, 0.68f, 0.26f, dark);
        box(ps, vc, light, -0.42f, 0.55f, -0.26f, -0.36f, 0.68f, 0.26f, dark);
        box(ps, vc, light, -0.36f, 0.55f, -0.26f, 0.36f, 0.58f, 0.26f, BLACK);          // interior
        // Periscopios del comandante
        for (float x : new float[]{-0.3f, 0.0f, 0.3f}) {
            box(ps, vc, light, x - 0.07f, 0.68f, 0.27f, x + 0.07f, 0.75f, 0.33f, GLASS);
        }
        // Lanzahumos a cada lado de la torreta
        for (int side = -1; side <= 1; side += 2) {
            float xa = side > 0 ? 0.55f : -0.62f;
            float xb = side > 0 ? 0.62f : -0.55f;
            for (int i = 0; i < 3; i++) {
                float z = 0.1f - i * 0.12f;
                box(ps, vc, light, xa, 0.25f, z - 0.045f, xb, 0.34f, z + 0.045f, BLACK);
                box(ps, vc, light, xa, 0.255f, z - 0.03f, xb, 0.335f, z + 0.03f, STEEL);
            }
        }
        // Ametralladora antiaérea con soporte
        box(ps, vc, light, 0.3f, 0.68f, -0.15f, 0.34f, 0.8f, -0.05f, STEEL);
        box(ps, vc, light, 0.28f, 0.8f, -0.4f, 0.36f, 0.88f, 0.2f, GUN);
        box(ps, vc, light, 0.29f, 0.81f, 0.2f, 0.35f, 0.87f, 0.34f, BLACK);
        // Antena con base y punta
        box(ps, vc, light, 0.44f, 0.55f, -0.62f, 0.52f, 0.62f, -0.54f, STEEL);
        box(ps, vc, light, 0.46f, 0.62f, -0.6f, 0.5f, 1.05f, -0.56f, BLACK);
        box(ps, vc, light, 0.465f, 1.05f, -0.595f, 0.495f, 1.1f, -0.565f, RED);

        turretExtras(ps, vc, light, main, dark);

        // Cañón (o cañones): giran hacia arriba/abajo alrededor del mantelete
        ps.translate(0.0, 0.29, 0.72);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        if (twin) {
            box(ps, vc, light, -0.3f, -0.12f, 0.0f, 0.3f, 0.12f, 0.12f, dark);       // soporte compartido
            for (int side = -1; side <= 1; side += 2) {
                float c = side * 0.14f;
                box(ps, vc, light, c - 0.09f, -0.09f, 0.12f, c + 0.09f, 0.09f, 0.35f, dark);   // culata
                box(ps, vc, light, c - 0.065f, -0.065f, 0.35f, c + 0.065f, 0.065f, 1.4f, GUN);
                box(ps, vc, light, c - 0.08f, -0.08f, 0.6f, c + 0.08f, 0.08f, 0.78f, STEEL);   // evacuador
                box(ps, vc, light, c - 0.095f, -0.095f, 1.12f, c + 0.095f, 0.095f, 1.4f, dark); // freno de boca
                box(ps, vc, light, c - 0.1f, -0.03f, 1.18f, c + 0.1f, 0.03f, 1.21f, BLACK);
                box(ps, vc, light, c - 0.1f, -0.03f, 1.28f, c + 0.1f, 0.03f, 1.31f, BLACK);
                box(ps, vc, light, c - 0.04f, -0.04f, 1.4f, c + 0.04f, 0.04f, 1.41f, BLACK);
            }
            box(ps, vc, light, -0.3f, -0.03f, 0.8f, 0.3f, 0.03f, 0.88f, STEEL);          // abrazadera
        } else {
            box(ps, vc, light, -0.11f, -0.11f, 0.0f, 0.11f, 0.11f, 0.3f, dark);        // culata
            box(ps, vc, light, -0.085f, -0.085f, 0.3f, 0.085f, 0.085f, 1.4f, GUN);
            box(ps, vc, light, -0.1f, -0.1f, 0.55f, 0.1f, 0.1f, 0.75f, STEEL);         // evacuador de humos
            box(ps, vc, light, -0.1f, -0.1f, 0.95f, 0.1f, 0.1f, 1.0f, STEEL);          // anillos
            box(ps, vc, light, -0.125f, -0.125f, 1.1f, 0.125f, 0.125f, 1.4f, dark);    // freno de boca
            box(ps, vc, light, -0.13f, -0.04f, 1.17f, 0.13f, 0.04f, 1.2f, BLACK);      // ranuras del freno
            box(ps, vc, light, -0.13f, -0.04f, 1.27f, 0.13f, 0.04f, 1.3f, BLACK);
            box(ps, vc, light, -0.05f, -0.05f, 1.4f, 0.05f, 0.05f, 1.41f, BLACK);            // boca
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ Geometría

    /** Caja texturizada. Las normales son las de cada cara, para que la luz del juego la sombree. */
    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        PoseStack.Pose pose = ps.last();
        int l = m.glow() ? LightTexture.FULL_BRIGHT : light;
        float xl = x2 - x1;
        float yl = y2 - y1;
        float zl = z2 - z1;
        quad(pose, vc, l, m, zl, xl, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);       // arriba
        quad(pose, vc, l, m, xl, zl, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);      // abajo
        quad(pose, vc, l, m, yl, xl, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);      // atrás
        quad(pose, vc, l, m, xl, yl, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);       // delante
        quad(pose, vc, l, m, zl, yl, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);      // derecha
        quad(pose, vc, l, m, yl, zl, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);       // izquierda
    }

    /** spanU/spanV: tamaño de la cara en unidades del modelo; la textura se recorta por el centro (1 unidad = casilla). */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, Mat m, float spanU, float spanV,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float tu = (m.tile() % 4) * 0.25f;
        float tv = (m.tile() / 4) * 0.25f;
        float fu = Math.min(spanU, 1.0f);
        float fv = Math.min(spanV, 1.0f);
        float u0 = tu + 0.25f * (1.0f - fu) * 0.5f;
        float u1 = tu + 0.25f - 0.25f * (1.0f - fu) * 0.5f;
        float v0 = tv + 0.25f * (1.0f - fv) * 0.5f;
        float v1 = tv + 0.25f - 0.25f * (1.0f - fv) * 0.5f;
        vertex(pose, vc, light, m.c(), u0, v1, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, m.c(), u1, v1, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, m.c(), u1, v0, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, m.c(), u0, v0, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c, float u, float v,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
