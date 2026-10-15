package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
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
 * Dibuja el tanque «Behemoth»: fortaleza industrial sobre orugas con casco excepcionalmente ancho y tres niveles
 * de blindaje (placas exteriores negras, estructura intermedia de titanio y núcleo central). Las placas exteriores
 * desaparecen por debajo del 66 % de vida y el titanio por debajo del 33 %. Torreta baja y rectangular, integrada en
 * el casco, enormes módulos blindados a ambos lados que recuerdan a contrafuertes, radiadores verticales con luz
 * naranja atrás y, delante, una placa central con el emblema del Proyecto X grabado en metal.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -1.6 a 1.74 y X ±1.5.
 * El eje del cañón (pivotY 1.35, pivotForward 0.75, largo 2.2) debe coincidir con BehemothTankEntity.
 */
public class BehemothTankRenderer extends EntityRenderer<BehemothTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_behemoth.png");

    private static final int T_PLATE = 0, T_TITAN = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_DENT = 8, T_EMBLEM = 9, T_CORNER = 10;

    private record Mat(int[] c, int tile, boolean glow) {
        Mat(int[] c, int tile) {
            this(c, tile, false);
        }
    }

    private static final int[] W = {255, 255, 255};
    private static final Mat PLATE = new Mat(W, T_PLATE);
    private static final Mat TITAN = new Mat(W, T_TITAN);
    private static final Mat TRACK = new Mat(W, T_TRACK);
    private static final Mat HUB = new Mat(W, T_HUB);
    private static final Mat VENT = new Mat(W, T_VENT);
    private static final Mat PANEL = new Mat(W, T_PANEL);
    private static final Mat DENT = new Mat(W, T_DENT);
    private static final Mat EMBLEM = new Mat(W, T_EMBLEM);
    private static final Mat CORNER = new Mat(W, T_CORNER);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 132}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{12, 12, 15}, T_FLAT);

    private static final int WHEELS = 6;
    private static final float WHEEL_Y = 0.34f;
    private static final float WHEEL_X = 1.26f;
    private static final float WHEEL_SPIN = 0.34f / 0.24f;

    private static final float DECK = 1.05f;

    private float time;
    private Mat orange = BLACK;

    public BehemothTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.0f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(BehemothTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(BehemothTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(5.0, 3.0, 5.0));
    }

    @Override
    public void render(BehemothTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        int layer = tank.armorLayer();
        float heatK = Mth.clamp(tank.getHeat() / (float) BehemothTankEntity.HEAT_MAX, 0.0f, 1.0f);
        float since = tank.ticksSinceShot(partialTick);
        float recoil = since < 14.0f ? 0.3f * (since < 2.5f ? since / 2.5f : 1.0f - (since - 2.5f) / 11.5f) : 0.0f;
        float flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;

        float k = 0.4f + 0.5f * heatK + (layer == 0 ? 0.1f : 0.0f) + 0.04f * Mth.sin(this.time * 0.1f);
        this.orange = orangeMat(k);

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light, layer);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash);

        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            mbox(ps, vc, light, s, 1.02f, 0.0f, -1.3f, 1.5f, 0.07f, 1.3f, TRACK);
            mbox(ps, vc, light, s, 1.02f, 0.62f, -1.25f, 1.5f, 0.69f, 1.25f, TRACK);
            float xc = s * WHEEL_X;
            slab(ps, vc, light, xc, 0.655f, 1.25f, 40.0f, 0.24f, 0.07f, 0.0f, 0.4f, TRACK);
            slab(ps, vc, light, xc, 0.398f, 1.556f, 130.0f, 0.24f, 0.07f, 0.0f, 0.46f, TRACK);
            slab(ps, vc, light, xc, 0.655f, -1.25f, -40.0f, 0.24f, 0.07f, -0.4f, 0.0f, TRACK);
            slab(ps, vc, light, xc, 0.398f, -1.556f, -130.0f, 0.24f, 0.07f, -0.46f, 0.0f, TRACK);
            for (int i = 0; i < 12; i++) {
                float z = -1.28f + i * 0.22f;
                mbox(ps, vc, light, s, 1.0f, -0.012f, z, 1.52f, 0.05f, z + 0.12f, CORNER);
            }
            float angle = s > 0 ? angleL : angleR;
            for (int i = 0; i < WHEELS; i++) {
                float z = -1.3f + i * 0.52f;
                wheel(ps, vc, light, xc, WHEEL_Y, z, angle);
            }
        }
    }

    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.2f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -0.24f, -0.24f, h, 0.24f, 0.24f, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -0.17f, -0.17f, h, 0.17f, 0.17f, HUB);
        ps.popPose();
        box(ps, vc, light, -h - 0.02f, -0.07f, -0.07f, h + 0.02f, 0.07f, 0.07f, BOLT);
        ps.popPose();
    }

    // ------------------------------------------------------------------ Casco: núcleo, titanio y placas exteriores

    private void drawHull(PoseStack ps, VertexConsumer vc, int light, int layer) {
        // Nivel 3: núcleo central protegido (siempre presente)
        box(ps, vc, light, -1.0f, 0.15f, -1.6f, 1.0f, DECK - 0.01f, 1.6f, CORNER);
        box(ps, vc, light, -0.95f, 0.08f, -1.5f, 0.95f, 0.15f, 1.5f, BLACK);

        if (layer >= 1) {
            drawTitanium(ps, vc, light);
        } else {
            drawSkeleton(ps, vc, light);
        }
        if (layer >= 2) {
            drawOuterPlates(ps, vc, light);
        }
        drawCoreHousing(ps, vc, light, layer);
        drawRadiators(ps, vc, light);
    }

    /** Nivel 2: estructura intermedia de titanio (cubierta, glacis, placa central con emblema y módulos laterales). */
    private void drawTitanium(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.4f, 0.72f, -1.55f, 1.4f, DECK, 1.3f, TITAN);
        box(ps, vc, light, -1.4f, 0.2f, -1.6f, 1.4f, DECK, -1.55f, TITAN);
        grilleZ(ps, vc, light, -0.9f, 0.9f, 0.4f, 0.9f, -1.6f, -1, this.orange, 5);
        // Glacis y proa
        ps.pushPose();
        ps.translate(0.0, DECK, 1.3);
        ps.mulPose(Axis.XP.rotationDegrees(40.0f));
        box(ps, vc, light, -1.4f, -0.13f, 0.0f, 1.4f, 0.0f, 0.5f, TITAN);
        box(ps, vc, light, -1.2f, 0.0f, 0.08f, -0.5f, 0.02f, 0.42f, PANEL);
        box(ps, vc, light, 0.5f, 0.0f, 0.08f, 1.2f, 0.02f, 0.42f, PANEL);
        ps.popPose();
        box(ps, vc, light, -1.3f, 0.2f, 1.55f, 1.3f, 0.75f, 1.7f, TITAN);
        // Placa central de proa con el emblema del Proyecto X grabado en el metal
        box(ps, vc, light, -0.42f, 0.2f, 1.7f, 0.42f, 0.75f, 1.74f, TITAN);
        boxFull(ps, vc, light, -0.25f, 0.225f, 1.74f, 0.25f, 0.725f, 1.752f, EMBLEM);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.45f, 0.24f, 1.7f, 1.25f, 0.7f, 1.715f, DENT);
            // módulos blindados laterales (contrafuertes de fortaleza), escalonados
            mbox(ps, vc, light, s, 0.95f, DECK, -0.85f, 1.4f, 1.3f, 1.0f, TITAN);
            mbox(ps, vc, light, s, 0.95f, 1.3f, -0.75f, 1.3f, 1.55f, 0.9f, TITAN);
            mbox(ps, vc, light, s, 0.95f, 1.55f, -0.6f, 1.15f, 1.75f, 0.75f, TITAN);
            mbox(ps, vc, light, s, 0.95f, 1.75f, -0.6f, 1.15f, 1.79f, 0.75f, CORNER);
            float[] ribs = {-0.85f, -0.3f, 0.25f, 0.9f};
            for (float z : ribs) {
                mbox(ps, vc, light, s, 1.4f, DECK, z, 1.5f, 1.3f, z + 0.1f, CORNER);
            }
            mbox(ps, vc, light, s, 1.3f, 1.3f, -0.75f, 1.36f, 1.55f, -0.65f, CORNER);
            mbox(ps, vc, light, s, 1.3f, 1.3f, 0.8f, 1.36f, 1.55f, 0.9f, CORNER);
            // ranuras naranjas en la cara delantera del módulo
            grilleZ(ps, vc, light, s > 0 ? 1.0f : -1.35f, s > 0 ? 1.35f : -1.0f, 1.1f, 1.25f, 1.0f, 1, this.orange, 2);
        }
    }

    /** Sin la estructura de titanio: sólo quedan costillas retorcidas de los marcos. */
    private void drawSkeleton(PoseStack ps, VertexConsumer vc, int light) {
        float[] zs = {-1.5f, -0.9f, -0.3f, 0.3f, 0.9f, 1.25f};
        for (int i = 0; i < zs.length; i++) {
            float z = zs[i];
            float xa = (i % 2 == 0) ? -1.4f : -0.6f;
            float xb = (i % 3 == 1) ? 0.6f : 1.4f;
            box(ps, vc, light, xa, 0.92f, z, xb, DECK, z + 0.06f, TITAN);
            box(ps, vc, light, 1.34f, 0.72f, z, 1.4f, 0.92f, z + 0.06f, TITAN);
            if (i % 2 == 1) {
                box(ps, vc, light, -1.4f, 0.72f, z, -1.34f, 0.92f, z + 0.06f, TITAN);
            }
        }
        // vigas retorcidas de los antiguos módulos laterales
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 1.0f, DECK, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -18.0f));
            box(ps, vc, light, -0.04f, 0.0f, -0.3f, 0.04f, 0.32f, -0.24f, TITAN);
            box(ps, vc, light, -0.04f, 0.0f, 0.4f, 0.04f, 0.22f, 0.46f, TITAN);
            ps.popPose();
        }
    }

    /** Nivel 1: placas exteriores negras con marcas de impacto. */
    private void drawOuterPlates(PoseStack ps, VertexConsumer vc, int light) {
        float[][] zr = {{-1.55f, -0.55f}, {-0.5f, 0.5f}, {0.55f, 1.3f}};
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < zr.length; i++) {
                float z0 = zr[i][0];
                float z1 = zr[i][1];
                mbox(ps, vc, light, s, 1.4f, 0.72f, z0, 1.5f, DECK, z1, PLATE);
                if (i != 1) {
                    mbox(ps, vc, light, s, 1.5f, 0.78f, z0 + 0.1f, 1.512f, 1.0f, z1 - 0.1f, DENT);
                }
                mbox(ps, vc, light, s, 1.39f, 0.7f, z0, 1.52f, 1.07f, z0 + 0.05f, CORNER);
                mbox(ps, vc, light, s, 1.39f, 0.7f, z1 - 0.05f, 1.52f, 1.07f, z1, CORNER);
            }
            // placas sobre la cubierta delante y detrás
            mbox(ps, vc, light, s, 0.1f, DECK, 1.02f, 1.35f, DECK + 0.06f, 1.28f, PLATE);
            mbox(ps, vc, light, s, 0.55f, DECK, -1.3f, 1.35f, DECK + 0.06f, -0.95f, PLATE);
            mbox(ps, vc, light, s, 0.62f, DECK + 0.06f, -1.25f, 1.28f, DECK + 0.075f, -1.0f, DENT);
            // revestimiento de los módulos laterales
            float[][] mz = {{-0.75f, -0.4f}, {-0.2f, 0.15f}, {0.35f, 0.8f}};
            for (float[] m : mz) {
                mbox(ps, vc, light, s, 1.4f, 1.08f, m[0], 1.48f, 1.28f, m[1], PLATE);
            }
            mbox(ps, vc, light, s, 1.3f, 1.3f, -0.65f, 1.38f, 1.55f, 0.8f, PLATE);
            mbox(ps, vc, light, s, 1.38f, 1.34f, -0.5f, 1.392f, 1.5f, 0.65f, DENT);
            mbox(ps, vc, light, s, 1.15f, 1.55f, -0.5f, 1.23f, 1.75f, 0.65f, PLATE);
            // glacis exterior (dos placas negras sobre el titanio)
            ps.pushPose();
            ps.translate(s * 0.7f, DECK, 1.3);
            ps.mulPose(Axis.XP.rotationDegrees(40.0f));
            box(ps, vc, light, -0.55f, 0.0f, 0.04f, 0.55f, 0.06f, 0.46f, PLATE);
            box(ps, vc, light, -0.4f, 0.06f, 0.1f, 0.4f, 0.075f, 0.4f, DENT);
            ps.popPose();
            // placas de proa laterales
            mbox(ps, vc, light, s, 0.45f, 0.2f, 1.7f, 1.3f, 0.75f, 1.74f, PLATE);
        }
    }

    /** Núcleo central: caja con ranuras naranjas; su tapa depende de las capas que quedan. */
    private void drawCoreHousing(PoseStack ps, VertexConsumer vc, int light, int layer) {
        box(ps, vc, light, -0.5f, DECK, -1.3f, 0.5f, 1.3f, -1.1f, CORNER);
        grilleZ(ps, vc, light, -0.4f, 0.4f, 1.12f, 1.24f, -1.1f, 1, this.orange, 2);
        if (layer >= 2) {
            box(ps, vc, light, -0.54f, 1.3f, -1.34f, 0.54f, 1.38f, -1.08f, PLATE);
            box(ps, vc, light, -0.5f, 1.38f, -1.3f, 0.5f, 1.395f, -1.12f, DENT);
        } else if (layer == 1) {
            box(ps, vc, light, -0.5f, 1.3f, -1.3f, 0.5f, 1.34f, -1.1f, TITAN);
        } else {
            // núcleo expuesto y brillante
            box(ps, vc, light, -0.3f, 1.3f, -1.26f, 0.3f, 1.34f, -1.14f, this.orange);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float cx = sx * 0.4f;
                    float cz = -1.2f + sz * 0.1f;
                    box(ps, vc, light, cx - 0.03f, 1.3f, cz - 0.03f, cx + 0.03f, 1.42f, cz + 0.03f, TITAN);
                }
            }
        }
    }

    /** Cuatro radiadores verticales con las ranuras iluminadas en naranja, sobresaliendo por detrás. */
    private void drawRadiators(PoseStack ps, VertexConsumer vc, int light) {
        float[] xs = {-0.95f, -0.32f, 0.32f, 0.95f};
        for (float x : xs) {
            box(ps, vc, light, x - 0.25f, DECK, -1.58f, x + 0.25f, DECK + 0.07f, -1.34f, PLATE);
            box(ps, vc, light, x - 0.22f, 1.12f, -1.5f, x + 0.22f, 1.92f, -1.49f, this.orange);
            for (int i = 0; i < 7; i++) {
                float fx = x - 0.21f + i * 0.07f;
                box(ps, vc, light, fx, 1.12f, -1.56f, fx + 0.035f, 1.92f, -1.44f, VENT);
            }
            box(ps, vc, light, x - 0.25f, 1.12f, -1.57f, x - 0.21f, 1.96f, -1.43f, CORNER);
            box(ps, vc, light, x + 0.21f, 1.12f, -1.57f, x + 0.25f, 1.96f, -1.43f, CORNER);
            box(ps, vc, light, x - 0.25f, 1.92f, -1.57f, x + 0.25f, 1.98f, -1.43f, CORNER);
        }
        // colector horizontal que une los radiadores
        box(ps, vc, light, -1.2f, 1.5f, -1.62f, 1.2f, 1.56f, -1.56f, CORNER);
    }

    // ------------------------------------------------------------------ Torreta baja e integrada

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash) {
        ps.pushPose();
        ps.translate(0.0, DECK, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        box(ps, vc, light, -0.8f, 0.0f, -0.8f, 0.8f, 0.05f, 0.8f, BLACK);
        // Cuerpo bajo y rectangular con la escotilla del conductor
        box(ps, vc, light, -0.85f, 0.05f, -0.95f, 0.85f, 0.1f, 0.8f, PLATE);
        box(ps, vc, light, 0.35f, 0.1f, -0.95f, 0.85f, 0.45f, 0.8f, PLATE);
        box(ps, vc, light, -0.85f, 0.1f, -0.95f, -0.35f, 0.45f, 0.8f, PLATE);
        box(ps, vc, light, -0.35f, 0.1f, -0.95f, 0.35f, 0.45f, -0.5f, PLATE);
        box(ps, vc, light, -0.35f, 0.1f, 0.15f, 0.35f, 0.45f, 0.8f, PLATE);
        box(ps, vc, light, -0.37f, 0.45f, -0.52f, 0.37f, 0.52f, -0.45f, TITAN);
        box(ps, vc, light, -0.37f, 0.45f, 0.1f, 0.37f, 0.52f, 0.17f, TITAN);
        box(ps, vc, light, 0.3f, 0.45f, -0.52f, 0.37f, 0.52f, 0.17f, TITAN);
        box(ps, vc, light, -0.37f, 0.45f, -0.52f, -0.3f, 0.52f, 0.17f, TITAN);
        // Planchas superiores y refuerzos
        box(ps, vc, light, 0.42f, 0.45f, -0.9f, 0.82f, 0.47f, 0.7f, DENT);
        box(ps, vc, light, -0.82f, 0.45f, -0.9f, -0.42f, 0.47f, 0.7f, DENT);
        box(ps, vc, light, -0.3f, 0.45f, -0.92f, 0.3f, 0.47f, -0.55f, PANEL);
        box(ps, vc, light, -0.87f, 0.4f, -0.97f, 0.87f, 0.46f, -0.9f, TITAN);
        // Frontal inclinado integrado en el casco
        ps.pushPose();
        ps.translate(0.0, 0.45, 0.5);
        ps.mulPose(Axis.XP.rotationDegrees(35.0f));
        box(ps, vc, light, -0.85f, -0.14f, 0.0f, 0.85f, 0.0f, 0.55f, TITAN);
        box(ps, vc, light, -0.7f, 0.0f, 0.08f, -0.32f, 0.02f, 0.45f, DENT);
        box(ps, vc, light, 0.32f, 0.0f, 0.08f, 0.7f, 0.02f, 0.45f, DENT);
        ps.popPose();
        // Faldón trasero de la torreta con una rejilla naranja
        box(ps, vc, light, -0.8f, 0.1f, -1.05f, 0.8f, 0.4f, -0.95f, PLATE);
        grilleZ(ps, vc, light, -0.55f, 0.55f, 0.16f, 0.34f, -1.05f, -1, this.orange, 3);

        // Cañón pesado: eje a (0, 0.3, 0.75) de la torreta
        ps.translate(0.0, 0.3, 0.75);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0.0, 0.0, -recoil);
        drawCannon(ps, vc, light, flash);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash) {
        box(ps, vc, light, -0.24f, -0.24f, -0.45f, 0.24f, 0.24f, 0.1f, CORNER);
        box(ps, vc, light, -0.2f, -0.2f, 0.1f, 0.2f, 0.2f, 0.7f, TITAN);
        box(ps, vc, light, -0.11f, -0.11f, 0.1f, 0.11f, 0.11f, 2.15f, BLACK);
        // Envolvente gruesa y segmentos
        box(ps, vc, light, -0.19f, -0.19f, 0.7f, 0.19f, 0.19f, 1.3f, PLATE);
        box(ps, vc, light, -0.21f, -0.21f, 0.68f, 0.21f, 0.21f, 0.74f, CORNER);
        box(ps, vc, light, -0.21f, -0.21f, 1.26f, 0.21f, 0.21f, 1.32f, CORNER);
        box(ps, vc, light, -0.15f, -0.15f, 1.32f, 0.15f, 0.15f, 1.85f, TITAN);
        box(ps, vc, light, -0.13f, 0.15f, 1.4f, 0.13f, 0.17f, 1.8f, DENT);
        // Freno de boca con ranuras naranjas
        box(ps, vc, light, -0.2f, -0.2f, 1.9f, 0.2f, 0.2f, 2.2f, PLATE);
        box(ps, vc, light, -0.22f, -0.22f, 1.88f, 0.22f, 0.22f, 1.94f, CORNER);
        box(ps, vc, light, -0.13f, 0.2f, 1.98f, 0.13f, 0.208f, 2.14f, this.orange);
        box(ps, vc, light, -0.13f, -0.208f, 1.98f, 0.13f, -0.2f, 2.14f, this.orange);
        box(ps, vc, light, 0.2f, -0.1f, 1.98f, 0.208f, 0.1f, 2.14f, this.orange);
        box(ps, vc, light, -0.208f, -0.1f, 1.98f, -0.2f, 0.1f, 2.14f, this.orange);
        if (flash > 0.0f) {
            float f = 0.1f + 0.18f * flash;
            box(ps, vc, light, -f, -f, 2.2f, f, f, 2.2f + 0.25f * flash, new Mat(new int[]{255, 220, 140}, T_FLAT, true));
        }
    }

    // ------------------------------------------------------------------ Luz naranja

    private static Mat orangeMat(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{255, (int) (70 + 120 * k), (int) (10 + 50 * k * k)}, T_FLAT, true);
    }

    // ------------------------------------------------------------------ Geometría

    /** Rejilla en una cara perpendicular a Z: fondo luminoso y láminas oscuras delante. dir = +1 mira a +Z. */
    private static void grilleZ(PoseStack ps, VertexConsumer vc, int light, float x1, float x2, float y1, float y2,
                                float z, int dir, Mat glow, int slats) {
        float d = 0.012f;
        float ga = dir > 0 ? z : z - d;
        float gb = dir > 0 ? z + d : z;
        box(ps, vc, light, x1, y1, ga, x2, y2, gb, glow);
        float ba = dir > 0 ? gb : ga - 0.03f;
        float bb = dir > 0 ? gb + 0.03f : ga;
        float h = (y2 - y1) / (slats * 2 + 1);
        for (int i = 0; i < slats; i++) {
            float ya = y1 + h * (2 * i + 1);
            box(ps, vc, light, x1, ya, ba, x2, ya + h, bb, BLACK);
        }
        box(ps, vc, light, x1 - 0.02f, y1 - 0.02f, ba, x1 + 0.02f, y2 + 0.02f, bb, CORNER);
        box(ps, vc, light, x2 - 0.02f, y1 - 0.02f, ba, x2 + 0.02f, y2 + 0.02f, bb, CORNER);
    }

    /** Caja espejada: s > 0 la dibuja tal cual; s < 0 la refleja respecto al plano X = 0. */
    private static void mbox(PoseStack ps, VertexConsumer vc, int light, int s,
                             float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        if (s > 0) {
            box(ps, vc, light, x1, y1, z1, x2, y2, z2, m);
        } else {
            box(ps, vc, light, -x2, y1, z1, -x1, y2, z2, m);
        }
    }

    /** Placa inclinada alrededor del eje X, con el pivote en (cx, cy, cz). */
    private static void slab(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle,
                             float halfX, float thick, float z1, float z2, Mat m) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -halfX, -thick * 0.5f, z1, halfX, thick * 0.5f, z2, m);
        ps.popPose();
    }

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, false);
    }

    /** Igual que box, pero cada cara usa la casilla de textura completa (para el emblema). */
    private static void boxFull(PoseStack ps, VertexConsumer vc, int light,
                                float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, true);
    }

    private static void boxImpl(PoseStack ps, VertexConsumer vc, int light,
                                float x1, float y1, float z1, float x2, float y2, float z2, Mat m, boolean full) {
        PoseStack.Pose pose = ps.last();
        int l = m.glow() ? LightTexture.FULL_BRIGHT : light;
        float xl = full ? 1.0f : x2 - x1;
        float yl = full ? 1.0f : y2 - y1;
        float zl = full ? 1.0f : z2 - z1;
        quad(pose, vc, l, m, zl, xl, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(pose, vc, l, m, xl, zl, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(pose, vc, l, m, yl, xl, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(pose, vc, l, m, xl, yl, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(pose, vc, l, m, zl, yl, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(pose, vc, l, m, yl, zl, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

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
