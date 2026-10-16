package com.example.armasreales;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ferrocarril de la ciudad: dos vías (este-oeste) que cruzan todo el mapa por la fila de parcelas {@link #IZ},
 * pasos a nivel en las calles, puente sobre el río, catenaria y estaciones con andenes, marquesina y trenes parados.
 * Todo con bloques propios del mod (vía, andén, tren, señales...).
 */
final class CityRail {
    private CityRail() {}

    /** Fila de parcelas (índice de la cuadrícula en z) por la que pasa la línea. */
    static final int IZ = 3;
    /** Coordenada z del eje de las dos vías. */
    static final int TZ0 = 217;
    static final int TZ1 = 225;

    private static final Direction N = Direction.NORTH;
    private static final Direction E = Direction.EAST;
    private static final Direction S = Direction.SOUTH;
    private static final Direction W = Direction.WEST;

    static boolean isStationIx(int ix) {
        return Math.floorMod(ix, 5) == 3;
    }

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    private static void d(CityPlan.Out o, int rel, String id, Direction f) {
        CityDeco.place(o, rel, id, f, false);
    }

    private static void track(CityPlan.Out o, int z) {
        if (z == TZ0 || z == TZ1) {
            o.set(1, ModDeco.state("riel", E, false));
            d(o, 5, "cable_catenaria", E);
        }
    }

    /** Mástiles de catenaria entre las vías cada 24 bloques (brazos hacia cada vía). */
    private static void masts(int x, int z, CityPlan.Out o) {
        if (Math.floorMod(x, 24) != 8) {
            return;
        }
        if (z >= TZ0 && z <= TZ0 + 2) {
            CityDeco.part(o, 1, "poste_catenaria", W, x, TZ0 + 2, x, z);
        }
        if (z >= TZ1 - 2 && z <= TZ1) {
            CityDeco.part(o, 1, "poste_catenaria", E, x, TZ1 - 2, x, z);
        }
    }

    /** Paso a nivel: calle norte-sur cruzada por las dos vías. */
    static void crossing(int x, int z, CityPlan.Out o) {
        if (z < TZ0 - 3 || z > TZ1 + 3) {
            return;
        }
        int ix = CityPlan.lineIdx(x);
        int u = x - CityPlan.lineStart(ix);
        int w = CityPlan.lineW(ix);
        boolean roadway = u >= 2 && u <= w - 3;
        if (roadway) {
            for (int r = 1; r <= 6; r++) {
                o.up[r] = null;
            }
        }
        if (roadway || o.up[1] == null) {
            track(o, z);
        }
        if (z == TZ0 - 3 && u == 2) {
            d(o, 1, "barrera_paso", N);
        } else if (z == TZ1 + 3 && u == w - 3) {
            d(o, 1, "barrera_paso", S);
        } else if (z == TZ0 - 3 && u == 1) {
            d(o, 1, "senal_tren", N);
        } else if (z == TZ1 + 3 && u == w - 2) {
            d(o, 1, "senal_tren", S);
        }
    }

    /** Puente ferroviario sobre el río (x dentro del cauce, z fuera de las calles). */
    static void bridge(int x, int z, CityPlan.Out o) {
        if (z < TZ0 - 4 || z > TZ1 + 4) {
            return;
        }
        o.set(0, s(z == TZ0 - 4 || z == TZ1 + 4 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE));
        if (z == TZ0 - 4 || z == TZ1 + 4) {
            o.set(1, s(Blocks.IRON_BARS));
            o.set(2, s(Blocks.IRON_BARS));
        }
        if ((z == TZ0 - 3 || z == TZ1 + 3) && Math.floorMod(x - CityPlan.RIVER_X0, 12) == 6) {
            o.pillar = true;
        }
        track(o, z);
        masts(x, z, o);
    }

    // ------------------------------------------------------------------ Parcelas de la línea

    static void lot(CityPlan.Lot l, int x, int z, int lx, int lz, int px, int pz, CityPlan.Out o) {
        boolean station = l.t == CityPlan.T.STATION;
        int w = l.x1 - l.x0;
        int dd = l.z1 - l.z0;
        boolean band = z >= TZ0 - 4 && z <= TZ1 + 4;
        int xa = l.x0 + 3;
        int xb = l.x1 - 3;
        boolean plat = station && x >= xa && x < xb;
        if (station) {
            CityBuildings.paving(lx, lz, o);
            CityBuildings.building(l, px, pz, o, CityBuildings.STATION, l.floors);
        }
        if (!band) {
            if (station) {
                CityBuildings.cornerTrees(l, lx, lz, w, dd, o);
                if (z > TZ1 + 5 && (lz % 8 == 0) && lx % 12 == 5 && Math.abs(x - l.cx) > 5) {
                    d(o, 1, "bolardo", N);
                }
            } else {
                CityBuildings.treeGrid(o, lx, lz, w, dd, 8, l.seed, 60, 3);
            }
            return;
        }
        o.ground = s(Blocks.GRAVEL);
        // valla a lo largo de la línea (salvo en los andenes)
        if ((z == TZ0 - 4 || z == TZ1 + 4) && !plat) {
            o.set(1, s(Blocks.IRON_BARS));
        }
        track(o, z);
        masts(x, z, o);
        if (!station) {
            return;
        }
        if (z == (TZ0 + TZ1) / 2 && (x == xa - 1 || x == xb)) {
            d(o, 1, "senal_ferro", N);
        }
        if (plat) {
            platforms(l, x, z, xa, xb, o);
        }
        trains(l, x, z, xa, xb, o);
    }

    private static void platforms(CityPlan.Lot l, int x, int z, int xa, int xb, CityPlan.Out o) {
        boolean north = z >= TZ0 - 4 && z <= TZ0 - 2;
        boolean south = z >= TZ1 + 2 && z <= TZ1 + 4;
        if (!north && !south) {
            return;
        }
        o.ground = s(Blocks.LIGHT_GRAY_CONCRETE);
        boolean edge = north ? z == TZ0 - 2 : z == TZ1 + 2;
        o.set(1, ModDeco.state(edge ? "anden_borde" : "anden", north ? S : N, false));
        int u = x - xa;
        int far = north ? TZ0 - 4 : TZ1 + 4;
        int mid = north ? TZ0 - 3 : TZ1 + 3;
        // marquesina: columnas cada 8 y techo continuo
        if (z == mid && u % 8 == 2) {
            for (int r = 2; r <= 4; r++) {
                d(o, r, "marq_columna", N);
            }
        }
        d(o, 5, "marq_techo", N);
        if (z == far) {
            if (u % 8 == 6 && u > 3 && x < xb - 4) {
                d(o, 2, "banco", north ? S : N);
            }
            if (u % 16 == 12 && x < xb - 4) {
                d(o, 2, "papelera_calle", N);
            }
        }
        // carteles de la estación en ambos extremos
        if (z == far) {
            for (int ox : new int[] {xa + 3, xb - 4}) {
                CityDeco.part(o, 2, "cartel_estacion", north ? S : N, ox, far, x, z);
            }
        }
    }

    /** Tren parado en cada vía de la estación (algunas vías quedan vacías). */
    private static void trains(CityPlan.Lot l, int x, int z, int xa, int xb, CityPlan.Out o) {
        for (int t = 0; t < 2; t++) {
            int tz = t == 0 ? TZ0 : TZ1;
            if (z < tz - 1 || z > tz + 1) {
                continue;
            }
            int hs = CityPlan.h(l.ix, t, 91);
            if (hs % 100 >= 70) {
                continue;
            }
            int n = 20 + hs / 100 % 6;                         // vagones intermedios
            int xs = xa + 3 + hs / 1000 % Math.max(1, xb - xa - n - 8);
            int i = x - xs;
            if (i < 0 || i > n + 1) {
                continue;
            }
            String id;
            Direction f = E;
            if (i == 0) {
                id = "tren_frente";
                f = W;
            } else if (i == n + 1) {
                id = "tren_frente";
            } else {
                id = i % 8 == 3 || i % 8 == 4 ? "tren_puerta" : "tren_vagon";
            }
            int oz = f == W ? tz + 1 : tz - 1;
            CityDeco.part(o, 1, id, f, x, oz, x, z);
        }
    }
}
