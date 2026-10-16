package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Interior común de todos los tanques: suelo, paredes, consola, cuatro asientos y el puesto elevado de la torreta.
 * Las medidas salen de TankEntity (interior*, seatLocal), así que cada tanque lo ajusta a su casco.
 * Se dibuja dentro del casco, por lo que sólo se ve desde dentro.
 */
public final class TankInterior {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_interior.png");

    /** Casillas del atlas 2x2: 0 metal oscuro, 1 panel, 2 asiento, 3 luz. */
    private static final int T_DARK = 0, T_PANEL = 1, T_SEAT = 2, T_LIGHT = 3;

    private static final int[] WHITE = {255, 255, 255};
    private static final int[] GREY = {200, 200, 205};
    private static final int[] SEAT = {170, 150, 135};

    private TankInterior() {}

    /** Se llama con la pose del casco ya girada y escalada por TankEntity.SCALE. */
    public static void draw(PoseStack ps, MultiBufferSource buffer, int light, TankEntity tank) {
        ps.pushPose();
        float inv = 1.0f / TankEntity.SCALE;
        ps.scale(inv, inv, inv);                          // a partir de aquí, unidades = bloques
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int[] acc = tank.interiorAccent();

        float hw = (float) tank.interiorHalfWidth();
        float zb = (float) tank.interiorBack();
        float zf = (float) tank.interiorFront();
        float fl = (float) tank.interiorFloor();
        float ce = (float) tank.interiorCeil();

        // Suelo y paredes
        box(ps, vc, light, -hw, fl - 0.12f, zb, hw, fl, zf, T_DARK, GREY, false);
        box(ps, vc, light, hw - 0.08f, fl, zb, hw, ce, zf, T_PANEL, GREY, false);
        box(ps, vc, light, -hw, fl, zb, -hw + 0.08f, ce, zf, T_PANEL, GREY, false);
        // Luces de suelo y de techo
        box(ps, vc, light, hw - 0.2f, fl, zb + 0.2f, hw - 0.08f, fl + 0.05f, zf - 0.2f, T_LIGHT, acc, true);
        box(ps, vc, light, -hw + 0.08f, fl, zb + 0.2f, -hw + 0.2f, fl + 0.05f, zf - 0.2f, T_LIGHT, acc, true);
        box(ps, vc, light, -0.12f, ce - 0.06f, zb + 0.3f, 0.12f, ce, zf - 0.3f, T_LIGHT, WHITE, true);
        // Costillas del casco
        for (float z = zb + 0.6f; z < zf - 0.3f; z += 1.2f) {
            box(ps, vc, light, hw - 0.14f, fl, z, hw, ce, z + 0.12f, T_DARK, GREY, false);
            box(ps, vc, light, -hw, fl, z, -hw + 0.14f, ce, z + 0.12f, T_DARK, GREY, false);
        }

        // Mamparo trasero (motor) con rendijas
        box(ps, vc, light, -hw + 0.1f, fl, zb, hw - 0.1f, ce, zb + 0.3f, T_DARK, GREY, false);
        for (int i = -1; i <= 1; i++) {
            box(ps, vc, light, i * hw * 0.5f - 0.1f, fl + 0.4f, zb + 0.3f, i * hw * 0.5f + 0.1f, ce - 0.4f,
                    zb + 0.34f, T_LIGHT, acc, true);
        }

        // Consola frontal con pantallas
        box(ps, vc, light, -hw + 0.1f, fl, zf - 0.55f, hw - 0.1f, fl + 0.85f, zf - 0.1f, T_PANEL, GREY, false);
        for (int i = -1; i <= 1; i++) {
            box(ps, vc, light, i * hw * 0.5f - hw * 0.2f, fl + 0.85f, zf - 0.5f, i * hw * 0.5f + hw * 0.2f,
                    fl + 0.92f, zf - 0.2f, T_LIGHT, acc, true);
        }

        // Cuatro asientos interiores (miran hacia delante)
        for (int seat = 1; seat < TankEntity.SEAT_COUNT; seat++) {
            Vec3 p = tank.seatLocal(seat);
            float sx = (float) p.x;
            float sz = (float) p.z;
            box(ps, vc, light, sx - 0.35f, fl, sz - 0.3f, sx + 0.35f, fl + 0.4f, sz + 0.3f, T_SEAT, SEAT, false);
            box(ps, vc, light, sx - 0.35f, fl + 0.4f, sz - 0.34f, sx + 0.35f, fl + 1.25f, sz - 0.2f, T_SEAT, SEAT, false);
            box(ps, vc, light, sx - 0.2f, fl + 1.25f, sz - 0.34f, sx + 0.2f, fl + 1.5f, sz - 0.2f, T_SEAT, SEAT, false);
            box(ps, vc, light, sx - 0.37f, fl + 0.4f, sz - 0.3f, sx - 0.3f, fl + 0.7f, sz + 0.2f, T_DARK, GREY, false);
            box(ps, vc, light, sx + 0.3f, fl + 0.4f, sz - 0.3f, sx + 0.37f, fl + 0.7f, sz + 0.2f, T_DARK, GREY, false);
            box(ps, vc, light, sx - 0.05f, fl + 0.4f, sz + 0.3f, sx + 0.05f, fl + 0.45f, sz + 0.34f, T_LIGHT, acc, true);
        }

        // Puesto elevado de la torreta: plataforma, aro luminoso, columna de acceso y asiento
        Vec3 top = tank.seatLocal(0);
        float tx = (float) top.x;
        float tz = (float) top.z;
        float feet = (float) top.y - 0.6f;
        box(ps, vc, light, tx - 0.8f, feet - 0.14f, tz - 0.8f, tx + 0.8f, feet, tz + 0.8f, T_PANEL, GREY, false);
        box(ps, vc, light, tx - 0.8f, feet - 0.02f, tz - 0.8f, tx + 0.8f, feet + 0.03f, tz - 0.74f, T_LIGHT, acc, true);
        box(ps, vc, light, tx - 0.8f, feet - 0.02f, tz + 0.74f, tx + 0.8f, feet + 0.03f, tz + 0.8f, T_LIGHT, acc, true);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(ps, vc, light, tx + sx * 0.7f - 0.05f, fl, tz + sz * 0.7f - 0.05f,
                        tx + sx * 0.7f + 0.05f, feet - 0.14f, tz + sz * 0.7f + 0.05f, T_DARK, GREY, false);
            }
        }
        for (float y = fl + 0.3f; y < feet - 0.2f; y += 0.4f) {          // peldaños
            box(ps, vc, light, tx - 0.5f, y, tz - 0.78f, tx + 0.5f, y + 0.06f, tz - 0.7f, T_LIGHT, acc, true);
        }
        box(ps, vc, light, tx - 0.3f, feet, tz - 0.55f, tx + 0.3f, feet + 0.4f, tz - 0.15f, T_SEAT, SEAT, false);
        box(ps, vc, light, tx - 0.3f, feet + 0.4f, tz - 0.6f, tx + 0.3f, feet + 1.1f, tz - 0.5f, T_SEAT, SEAT, false);
        box(ps, vc, light, tx - 0.5f, feet, tz + 0.55f, tx + 0.5f, feet + 0.55f, tz + 0.75f, T_DARK, GREY, false);   // mirilla
        box(ps, vc, light, tx - 0.35f, feet + 0.55f, tz + 0.55f, tx + 0.35f, feet + 0.62f, tz + 0.75f, T_LIGHT, acc, true);

        ps.popPose();
    }

    // ------------------------------------------------------------------ Geometría

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            int tile, int[] c, boolean glow) {
        PoseStack.Pose pose = ps.last();
        int l = glow ? LightTexture.FULL_BRIGHT : light;
        float u = (tile % 2) * 0.5f + 0.25f;
        float v = (tile / 2) * 0.5f + 0.25f;
        face(pose, vc, l, u, v, c, 1.0f, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        face(pose, vc, l, u, v, c, 0.6f, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        face(pose, vc, l, u, v, c, 0.8f, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        face(pose, vc, l, u, v, c, 0.8f, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        face(pose, vc, l, u, v, c, 0.7f, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        face(pose, vc, l, u, v, c, 0.7f, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, int light, float u, float v, int[] c,
                             float shade, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        int r = (int) (c[0] * shade);
        int g = (int) (c[1] * shade);
        int b = (int) (c[2] * shade);
        vertex(pose, vc, light, r, g, b, u, v, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, r, g, b, u, v, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, r, g, b, u, v, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, r, g, b, u, v, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int r, int g, int b,
                               float u, float v, float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
