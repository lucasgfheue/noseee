package com.example.armasreales;

import net.minecraft.core.Direction;

/** Coloca muebles (de uno o varios bloques) dentro de una columna de la ciudad. */
final class CityDeco {
    private CityDeco() {}

    /** Mueble cuya base es 1x1 (si es alto, se apilan todas sus partes en la columna). */
    static void place(CityPlan.Out o, int rel, String id, Direction f, boolean lit) {
        int[] dm = DecoDims.M.get(id);
        if (dm == null) {
            o.set(rel, ModDeco.state(id, f, lit));
            return;
        }
        int w = dm[0];
        int h = dm[1];
        int dd = dm[2];
        if (w == 1 && dd == 1) {
            for (int ly = 0; ly < h; ly++) {
                if ((dm[3] & (1 << ly)) != 0) {
                    o.set(rel + ly, ModDeco.part(id, f, false, ly));
                }
            }
        } else {
            o.set(rel, ModDeco.part(id, f, false, 0));
        }
    }

    /**
     * Mueble de varias celdas con origen (ox, oz) y orientación f: si la columna (px, pz) cae dentro de su huella
     * coloca las partes y devuelve true.
     */
    static boolean part(CityPlan.Out o, int rel, String id, Direction f, int ox, int oz, int px, int pz) {
        int[] dm = DecoDims.M.get(id);
        int dx = px - ox;
        int dz = pz - oz;
        int lx;
        int lz;
        switch (f) {
            case EAST -> {
                lx = dz;
                lz = -dx;
            }
            case SOUTH -> {
                lx = -dx;
                lz = -dz;
            }
            case WEST -> {
                lx = -dz;
                lz = dx;
            }
            default -> {
                lx = dx;
                lz = dz;
            }
        }
        if (lx < 0 || lz < 0 || lx >= dm[0] || lz >= dm[2]) {
            return false;
        }
        for (int ly = 0; ly < dm[1]; ly++) {
            int idx = lx + dm[0] * (ly + dm[1] * lz);
            if ((dm[3] & (1 << idx)) != 0) {
                o.set(rel + ly, ModDeco.part(id, f, false, idx));
            }
        }
        return true;
    }
}
