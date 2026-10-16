package com.example.armasreales;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Plano de la ciudad (2000 x 2000 bloques, de -1000 a 999 en X y Z). Todo se calcula con funciones puras de las
 * coordenadas, así que cada chunk se genera por separado y siempre sale igual.
 *
 *  - Cuadrícula de calles cada 64 bloques (cada 4.ª es una avenida de 16; las demás miden 10).
 *  - Centro (r &lt;= 192): rascacielos. Hasta 384: oficinas. Hasta 704: bloques de viviendas y comercios. Más allá: casas.
 *  - Sur: puerto (contenedores, grúas, almacenes), zona industrial al este y base militar al oeste, junto al mar.
 *  - Norte: campos, huertos, una aldea y un molino. Un río con puentes cruza la ciudad de norte a sur.
 */
public final class CityPlan {
    public static final int HALF = 1000;
    public static final int GROUND = 64;
    public static final int SEA = 62;
    static final int PITCH = 64;
    static final int OFF = 5;
    static final int RIVER_X0 = 581;
    static final int RIVER_X1 = 635;
    static final int PORT_Z = 560;
    static final int RURAL_Z = -560;
    static final int RIVER_FLOOR = 50;
    static final int SEA_FLOOR = 40;

    private CityPlan() {}

    // ------------------------------------------------------------------ Utilidades

    static BlockState s(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    public static int h(int a, int b, int salt) {
        long v = a * 0x9E3779B97F4A7C15L ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        v ^= v >>> 29;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 32;
        return (int) (v & 0x7fffffffL);
    }

    // ------------------------------------------------------------------ Salida de una columna

    /** Lo que hay en una columna: terreno, agua y bloques sobre el suelo (índice 0 = el bloque del suelo, y = 64). */
    public static final class Out {
        public BlockState ground = s(Blocks.GRASS_BLOCK);
        public int floorY = GROUND;
        public int waterTop = -1000;
        public boolean pillar = false;
        public BlockState pillarBlk = s(Blocks.GRAY_CONCRETE);
        public final BlockState[] up = new BlockState[256];
        public int max = -1;

        public void set(int rel, BlockState st) {
            if (rel >= 0 && rel < up.length) {
                up[rel] = st;
                if (rel > max) {
                    max = rel;
                }
            }
        }

        public void reset() {
            for (int i = 0; i <= max; i++) {
                up[i] = null;
            }
            max = -1;
            ground = s(Blocks.GRASS_BLOCK);
            floorY = GROUND;
            waterTop = -1000;
            pillar = false;
            pillarBlk = s(Blocks.GRAY_CONCRETE);
        }
    }

    // ------------------------------------------------------------------ Cuadrícula

    static int lineIdx(int c) {
        return Math.floorDiv(c + OFF, PITCH);
    }

    static int lineW(int idx) {
        return Math.floorMod(idx, 4) == 0 ? 16 : 10;
    }

    static int lineStart(int idx) {
        return idx * PITCH - OFF;
    }

    static boolean isRoadAxis(int c) {
        int idx = lineIdx(c);
        return c - lineStart(idx) < lineW(idx);
    }

    static int coastOfIdx(int idx) {
        return 800 + 8 * (h(idx, 0, 11) % 4);
    }

    static int coast(int x) {
        return coastOfIdx(lineIdx(x));
    }

    // ------------------------------------------------------------------ Parcelas

    enum T {
        SKY, OFFICE, APART, SHOPS, HOUSE, PARK, PLAZA, WAREHOUSE, HANGAR, CONTAINERS, FUEL, BARRACKS, MOTOR, RADAR,
        FACTORY, FIELD, ORCHARD, MEADOW, FARM, BARN, VILLAGE, WELL, WINDMILL, RIVER, EMPTY
    }

    static final class Lot {
        int ix, iz, x0, x1, z0, z1, cx, cz, seed;
        T t = T.EMPTY;
        // edificio
        int fx0, fz0, fw, fd, floors, fh = 4;
        int palette;
    }

    static Lot lot(int ix, int iz, Map<Long, Lot> cache) {
        long key = ((long) ix << 32) ^ (iz & 0xffffffffL);
        Lot l = cache.get(key);
        if (l == null) {
            l = makeLot(ix, iz);
            cache.put(key, l);
        }
        return l;
    }

    private static Lot makeLot(int ix, int iz) {
        Lot l = new Lot();
        l.ix = ix;
        l.iz = iz;
        l.x0 = lineStart(ix) + lineW(ix);
        l.x1 = lineStart(ix + 1);
        l.z0 = lineStart(iz) + lineW(iz);
        l.z1 = lineStart(iz + 1);
        l.cx = (l.x0 + l.x1) / 2;
        l.cz = (l.z0 + l.z1) / 2;
        l.seed = h(ix, iz, 5);
        l.palette = h(ix, iz, 6);
        int r = h(ix, iz, 1) % 100;
        int dist = Math.max(Math.abs(l.cx), Math.abs(l.cz));

        if (ix == 9) {
            l.t = T.RIVER;
            return l;
        }
        if (l.z0 >= PORT_Z) {                                   // puerto, zona industrial y base militar
            l.z1 = Math.min(l.z1, coast(l.x0) - 13);
            if (l.z1 - l.z0 < 14) {
                l.t = T.EMPTY;
                return l;
            }
            if (l.cx < -620) {
                l.t = r < 25 ? T.HANGAR : r < 55 ? T.BARRACKS : r < 65 ? T.RADAR : r < 90 ? T.MOTOR : T.EMPTY;
            } else if (l.cx > 640) {
                l.t = r < 40 ? T.WAREHOUSE : r < 65 ? T.FUEL : T.FACTORY;
            } else {
                l.t = r < 45 ? T.CONTAINERS : r < 70 ? T.WAREHOUSE : r < 85 ? T.FUEL : r < 95 ? T.HANGAR : T.EMPTY;
            }
        } else if (l.cz < RURAL_Z) {                            // campo y aldea
            if (Math.abs(l.cx) <= 250 && l.cz > -930 && l.cz < -620) {
                l.t = r < 70 ? T.VILLAGE : r < 85 ? T.WELL : T.BARN;
            } else if (ix == lineIdx(-450) && iz == lineIdx(-800)) {
                l.t = T.WINDMILL;
            } else {
                l.t = r < 55 ? T.FIELD : r < 70 ? T.ORCHARD : r < 85 ? T.MEADOW : r < 95 ? T.FARM : T.BARN;
            }
        } else if (dist <= 192) {
            l.t = r < 70 ? T.SKY : r < 80 ? T.PLAZA : r < 90 ? T.PARK : T.OFFICE;
        } else if (dist <= 384) {
            l.t = r < 55 ? T.OFFICE : r < 65 ? T.SKY : r < 72 ? T.PLAZA : r < 80 ? T.PARK : T.APART;
        } else if (dist <= 704) {
            l.t = r < 50 ? T.APART : r < 75 ? T.SHOPS : r < 80 ? T.OFFICE : r < 90 ? T.PARK : T.HOUSE;
        } else {
            l.t = r < 70 ? T.HOUSE : r < 80 ? T.PARK : r < 90 ? T.APART : T.SHOPS;
        }

        int lw = l.x1 - l.x0;
        int ld = l.z1 - l.z0;
        int hh = h(ix, iz, 9);
        switch (l.t) {
            case SKY -> {
                int m = 3 + hh % 3;
                l.fx0 = l.x0 + m;
                l.fz0 = l.z0 + m;
                l.fw = lw - 2 * m;
                l.fd = ld - 2 * m;
                l.floors = Math.min(46, 44 - dist / 8 + hh / 7 % 7);
                l.floors = Math.max(l.floors, 16);
            }
            case OFFICE -> {
                int m = 2 + hh % 3;
                l.fx0 = l.x0 + m;
                l.fz0 = l.z0 + m;
                l.fw = lw - 2 * m;
                l.fd = ld - 2 * m;
                l.floors = Math.max(6, 16 - dist / 60 + hh / 7 % 3);
            }
            case APART -> {
                int m = 2 + hh % 2;
                l.fx0 = l.x0 + m;
                l.fz0 = l.z0 + m;
                l.fw = lw - 2 * m;
                l.fd = ld - 2 * m;
                l.floors = 4 + hh / 5 % 6;
            }
            case SHOPS -> {
                l.fx0 = l.x0 + 1;
                l.fz0 = l.z0 + 4;
                l.fw = lw - 2;
                l.fd = ld - 6;
                l.floors = 1 + hh / 5 % 3;
            }
            case HOUSE, VILLAGE, FARM -> {
                l.fw = 12 + hh % 5;
                l.fd = 10 + hh / 5 % 5;
                l.fx0 = l.x0 + 4 + hh / 3 % Math.max(1, lw - l.fw - 8);
                l.fz0 = l.z0 + 6;
                l.floors = 1 + hh / 11 % 2;
            }
            case WAREHOUSE -> {
                l.fx0 = l.x0 + 4;
                l.fz0 = l.z0 + 4;
                l.fw = lw - 8;
                l.fd = Math.min(ld - 8, 30);
                l.floors = 2;
            }
            case HANGAR -> {
                l.fw = Math.min(lw - 6, 40);
                l.fd = Math.min(ld - 6, 28);
                l.fx0 = l.cx - l.fw / 2;
                l.fz0 = l.z0 + 3;
                l.floors = 4;
            }
            case BARRACKS -> {
                l.fx0 = l.x0 + 6;
                l.fz0 = l.z0 + 8;
                l.fw = lw - 12;
                l.fd = 12;
                l.floors = 2;
            }
            case FACTORY -> {
                l.fx0 = l.x0 + 4;
                l.fz0 = l.z0 + 4;
                l.fw = lw - 8;
                l.fd = Math.min(ld - 8, 26);
                l.floors = 2;
            }
            case BARN -> {
                l.fw = 20;
                l.fd = 14;
                l.fx0 = l.cx - 10;
                l.fz0 = l.z0 + 6;
                l.floors = 2;
            }
            default -> { }
        }
        if (l.fw < 8 || l.fd < 8) {
            if (l.t == T.SKY || l.t == T.OFFICE || l.t == T.APART || l.t == T.SHOPS || l.t == T.WAREHOUSE
                    || l.t == T.HANGAR || l.t == T.BARRACKS || l.t == T.FACTORY) {
                l.t = T.EMPTY;
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ Columna

    public static void column(int x, int z, Out o, Map<Long, Lot> cache) {
        o.reset();
        int dx = Math.max(Math.max(-HALF - x, x - (HALF - 1)), 0);
        int dz = Math.max(Math.max(-HALF - z, z - (HALF - 1)), 0);
        int out = Math.max(dx, dz);
        if (out > 0) {
            sea(o, out);
            return;
        }
        int coast = coast(x);
        if (z >= coast) {
            sea(o, z - coast + 1);
            CityRoads.pier(x, z, coast, o);
            CityBuildings.crane(x, z, o);
            return;
        }
        if (x < -HALF + 6 || x >= HALF - 6 || z < -HALF + 6) {      // playa
            o.ground = s(Blocks.SAND);
            return;
        }
        // Río (y puentes donde lo cruzan las calles)
        if (x >= RIVER_X0 && x < RIVER_X1) {
            int e = Math.min(x - RIVER_X0, RIVER_X1 - 1 - x);
            o.floorY = Math.max(RIVER_FLOOR, 63 - e * 2);
            o.ground = e == 0 ? s(Blocks.STONE_BRICKS) : e < 3 ? s(Blocks.SAND) : s(Blocks.GRAVEL);
            o.waterTop = SEA;
            if (isRoadAxis(z)) {
                CityRoads.bridge(x, z, o);
            }
            return;
        }
        if (z >= coast - 12) {
            CityRoads.quay(x, z, coast, o);
            CityBuildings.crane(x, z, o);
            return;
        }
        boolean bx = isRoadAxis(x);
        boolean bz = isRoadAxis(z);
        if (bx || bz) {
            CityRoads.road(x, z, bx, bz, o);
            return;
        }
        Lot l = lot(lineIdx(x), lineIdx(z), cache);
        CityBuildings.column(l, x, z, x - l.x0, z - l.z0, o);
    }

    private static void sea(Out o, int d) {
        o.floorY = Math.max(SEA_FLOOR, 61 - d);
        o.ground = s(Blocks.SAND);
        o.waterTop = SEA;
    }
}
