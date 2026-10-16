package com.example.armasreales;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Calles, avenidas, puentes, muelles y embarcaderos de la ciudad. */
final class CityRoads {
    private CityRoads() {}

    private static BlockState s(Block b) {
        return CityPlan.s(b);
    }

    static final Block[] CAR = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.YELLOW_CONCRETE,
            Blocks.BLACK_CONCRETE, Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE};

    /** Lámpara según la zona (distingue las zonas por la luz). */
    static BlockState lamp(int x, int z) {
        if (z >= CityPlan.PORT_Z) {
            return s(Blocks.OCHRE_FROGLIGHT);
        }
        if (Math.max(Math.abs(x), Math.abs(z)) <= 192) {
            return s(Blocks.SEA_LANTERN);
        }
        if (Math.max(Math.abs(x), Math.abs(z)) <= 384) {
            return s(Blocks.VERDANT_FROGLIGHT);
        }
        return s(Blocks.GLOWSTONE);
    }

    static void lampPost(Out2 o, int x, int z) {
        for (int r = 1; r <= 5; r++) {
            o.o.set(r, s(Blocks.COBBLESTONE_WALL));
        }
        o.o.set(6, lamp(x, z));
    }

    /** Envoltorio mínimo para no repetir parámetros. */
    static final class Out2 {
        final CityPlan.Out o;

        Out2(CityPlan.Out o) {
            this.o = o;
        }
    }

    static void road(int x, int z, boolean bx, boolean bz, CityPlan.Out o) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        boolean rural = z < CityPlan.RURAL_Z;
        boolean port = z >= CityPlan.PORT_Z;
        boolean urban = !rural && !port && dist <= 704;
        int ix = CityPlan.lineIdx(x);
        int iz = CityPlan.lineIdx(z);
        int wx = x - CityPlan.lineStart(ix);
        int wX = CityPlan.lineW(ix);
        int wz = z - CityPlan.lineStart(iz);
        int wZ = CityPlan.lineW(iz);

        if (rural) {
            o.ground = CityPlan.h(x, z, 3) % 5 == 0 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRAVEL);
            return;
        }
        o.ground = port ? s(Blocks.LIGHT_GRAY_CONCRETE) : s(Blocks.GRAY_CONCRETE);
        if (bx && bz) {                                         // cruce
            if (urban && (wx == 1 || wx == wX - 2) && (wz == 1 || wz == wZ - 2)) {
                for (int r = 1; r <= 4; r++) {
                    o.set(r, s(Blocks.COBBLESTONE_WALL));
                }
                o.set(5, s(Blocks.RED_CONCRETE));
                o.set(6, s(Blocks.YELLOW_CONCRETE));
                o.set(7, s(Blocks.LIME_CONCRETE));
            }
            return;
        }
        boolean ns = bx;
        int u = ns ? wx : wz;
        int w = ns ? wX : wZ;
        int a = Math.floorMod((ns ? z : x) + CityPlan.OFF, CityPlan.PITCH);
        int cw = ns ? wZ : wX;
        if (!urban) {
            if (port) {
                if (u == 1 || u == w - 2) {
                    o.ground = s(Blocks.YELLOW_CONCRETE);
                }
                if (u == 0 && a % 16 == 8) {
                    lampPost(new Out2(o), x, z);
                }
            } else if (u == w / 2 && a % 8 < 4) {
                o.ground = s(Blocks.YELLOW_CONCRETE);
            }
            return;
        }
        if (u < 2 || u >= w - 2) {                             // acera
            o.ground = (u == 0 || u == w - 1) ? s(Blocks.SMOOTH_STONE) : s(Blocks.LIGHT_GRAY_CONCRETE);
            if ((u == 0 && a % 16 == 4) || (u == w - 1 && a % 16 == 12)) {
                lampPost(new Out2(o), x, z);
            }
            return;
        }
        boolean avenue = w == 16;
        if (avenue && (u == 7 || u == 8)) {                    // mediana con árboles
            o.ground = s(Blocks.GRASS_BLOCK);
            int da = a % 16 - 8;
            if (u == 7 && da == 0) {
                o.set(1, s(Blocks.OAK_LOG));
                o.set(2, s(Blocks.OAK_LOG));
                o.set(3, s(Blocks.OAK_LOG));
            }
            if (da >= -1 && da <= 1 && u >= 6 && u <= 8 && a >= cw) {
                // copa (sólo sobre la mediana; los carriles quedan libres hasta el rel 4)
                o.set(4, CityBuildings.leaf());
                o.set(5, CityBuildings.leaf());
            }
            return;
        }
        // marcas viales
        int cwLine = avenue ? 4 : -1;
        if (avenue) {
            if ((u == 4 || u == 11) && a % 8 < 4) {
                o.ground = s(Blocks.WHITE_CONCRETE);
            }
        } else if (u == 5 && a % 8 < 5) {
            o.ground = s(Blocks.WHITE_CONCRETE);
        }
        // paso de peatones
        if ((a >= cw && a < cw + 2) || a >= CityPlan.PITCH - 2) {
            if ((u & 1) == 0) {
                o.ground = s(Blocks.WHITE_CONCRETE);
            }
            return;
        }
        // coches aparcados junto a la acera
        int seg = a - (cw + 6);
        if (seg >= 0 && a < CityPlan.PITCH - 8) {
            int k = seg / 10;
            int r = seg % 10;
            if (r < 4) {
                int side = -1;
                if (u == 2 || u == 3) {
                    side = 0;
                } else if (u == w - 4 || u == w - 3) {
                    side = 1;
                }
                if (side >= 0) {
                    int cid = CityPlan.h(CityPlan.lineIdx(x), CityPlan.lineIdx(z), 21 + (ns ? 0 : 1));
                    if (CityPlan.h(cid, k, 40 + side) % 3 == 0) {
                        Block col = CAR[CityPlan.h(cid, k, 50 + side) % CAR.length];
                        o.set(1, s(col));
                        if (r == 1 || r == 2) {
                            o.set(2, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                            o.set(3, s(col));
                        } else {
                            o.set(2, s(col));
                        }
                    }
                }
            }
        }
    }

    static void bridge(int x, int z, CityPlan.Out o) {
        int iz = CityPlan.lineIdx(z);
        int w = CityPlan.lineW(iz);
        int u = z - CityPlan.lineStart(iz);
        if (u < 2 || u >= w - 2) {
            o.set(0, s(Blocks.LIGHT_GRAY_CONCRETE));
        } else if (u == w / 2 && Math.floorMod(x, 8) < 4) {
            o.set(0, s(Blocks.WHITE_CONCRETE));
        } else {
            o.set(0, s(Blocks.GRAY_CONCRETE));
        }
        if (u == 0 || u == w - 1) {
            o.set(1, s(Blocks.STONE_BRICK_WALL));
            if (Math.floorMod(x, 16) == 0) {
                o.set(2, s(Blocks.STONE_BRICK_WALL));
                o.set(3, s(Blocks.STONE_BRICK_WALL));
                o.set(4, CityRoads.lamp(x, z));
            }
        }
        if ((u == 2 || u == w - 3) && Math.floorMod(x - CityPlan.RIVER_X0, 12) == 6) {
            o.pillar = true;
        }
    }

    static void quay(int x, int z, int coast, CityPlan.Out o) {
        int d = coast - z;
        o.ground = s(Blocks.LIGHT_GRAY_CONCRETE);
        if (d == 1) {
            o.ground = s(Blocks.STONE_BRICKS);
        } else if (d == 3) {
            o.ground = s(Blocks.YELLOW_CONCRETE);
        } else if (d == 2 && Math.floorMod(x, 12) == 0) {
            o.set(1, s(Blocks.COBBLESTONE_WALL));
            o.set(2, s(Blocks.BLACKSTONE));
        } else if (d == 8 && Math.floorMod(x, 24) == 6) {
            lampPost(new Out2(o), x, z);
        }
    }

    static int cat(int x) {
        return CityPlan.h(CityPlan.lineIdx(x), 0, 31) % 4;
    }

    /** Embarcaderos y barcos amarrados (z &gt;= costa). */
    static void pier(int x, int z, int coast, CityPlan.Out o) {
        if (Math.abs(x) > 620) {
            return;
        }
        int dz = z - coast;
        int idx = CityPlan.lineIdx(x);
        int lx = Math.floorMod(x + CityPlan.OFF, CityPlan.PITCH);
        int c = cat(x);
        if ((c == 0 || c == 3) && lx >= 20 && lx < 28 && dz < 64) {
            o.set(0, (lx == 20 || lx == 27 || dz == 63) ? s(Blocks.STONE_BRICKS) : s(Blocks.LIGHT_GRAY_CONCRETE));
            if ((lx == 20 || lx == 27) && dz % 8 == 0) {
                o.pillar = true;
                o.set(1, s(Blocks.COBBLESTONE_WALL));
            }
            if ((lx == 21 || lx == 26) && dz % 16 == 8) {
                o.set(1, s(Blocks.COBBLESTONE_WALL));
                o.set(2, s(Blocks.COBBLESTONE_WALL));
                o.set(3, lamp(x, z));
            }
            return;
        }
        if ((c == 1 || c == 2) && lx >= 8 && lx < 56 && dz >= 3 && dz < 12) {
            double half = 4.5;
            if (lx >= 50) {
                half = (56 - lx) * 0.8;
            }
            if (Math.abs(dz - 7) > half) {
                return;
            }
            o.pillar = true;
            o.pillarBlk = s(Blocks.RED_CONCRETE);
            o.set(0, s(Blocks.GRAY_CONCRETE));
            boolean edge = Math.abs(dz - 7) >= half - 0.9 || lx == 8;
            if (edge) {
                o.set(0, s(Blocks.BLACK_CONCRETE));
                o.set(1, s(Blocks.IRON_BARS));
            }
            if (lx >= 10 && lx <= 17 && !edge) {                // puente de mando
                for (int r = 1; r <= 3; r++) {
                    o.set(r, s(Blocks.WHITE_CONCRETE));
                }
                o.set(4, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                o.set(5, s(Blocks.WHITE_CONCRETE));
                o.set(6, s(Blocks.WHITE_CONCRETE));
                if (lx == 13 && dz == 7) {
                    for (int r = 7; r <= 11; r++) {
                        o.set(r, s(Blocks.IRON_BARS));
                    }
                    o.set(12, s(Blocks.RED_CONCRETE));
                }
            } else if (lx >= 20 && lx < 48 && !edge) {         // contenedores
                int k = (lx - 20) / 10;
                int sub = (lx - 20) % 10;
                int side = dz >= 8 ? 1 : 0;
                if (sub < 9 && (dz == 4 || dz == 5 || dz == 6 || dz == 8 || dz == 9 || dz == 10)) {
                    Block col = CAR[CityPlan.h(idx, k, 60 + side) % CAR.length];
                    int hgt = 3 + (CityPlan.h(idx, k, 70 + side) % 2) * 3;
                    for (int r = 1; r <= hgt; r++) {
                        o.set(r, s(col));
                    }
                }
            }
        }
    }
}
