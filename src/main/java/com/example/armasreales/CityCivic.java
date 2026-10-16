package com.example.armasreales;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.example.armasreales.CityBuildings.BANK;
import static com.example.armasreales.CityBuildings.CINEMA;
import static com.example.armasreales.CityBuildings.CYBER;
import static com.example.armasreales.CityBuildings.FIRE;
import static com.example.armasreales.CityBuildings.HOSP;
import static com.example.armasreales.CityBuildings.HOTEL;
import static com.example.armasreales.CityBuildings.LIBRARY;
import static com.example.armasreales.CityBuildings.MALL;
import static com.example.armasreales.CityBuildings.OFFICE;
import static com.example.armasreales.CityBuildings.POLICE;
import static com.example.armasreales.CityBuildings.RESTO;
import static com.example.armasreales.CityBuildings.SCHOOL;
import static com.example.armasreales.CityBuildings.STATION;
import static com.example.armasreales.CityBuildings.UNIV;

/**
 * Edificios de servicios de la ciudad: restaurantes, comisarías, bomberos, hospitales, cibercafés, universidades,
 * centros comerciales, hoteles, bancos, cines, bibliotecas, escuelas y estaciones de tren.
 */
final class CityCivic {
    private CityCivic() {}

    private static final Direction N = Direction.NORTH;
    private static final Direction E = Direction.EAST;
    private static final Direction S = Direction.SOUTH;
    private static final Direction W = Direction.WEST;

    private static final CityPlan.T[] TYPES = {CityPlan.T.RESTO, CityPlan.T.POLICE, CityPlan.T.FIRE, CityPlan.T.HOSP,
            CityPlan.T.CYBER, CityPlan.T.UNIV, CityPlan.T.MALL, CityPlan.T.HOTEL, CityPlan.T.BANK, CityPlan.T.CINEMA,
            CityPlan.T.LIBRARY, CityPlan.T.SCHOOL};
    // probabilidad (por mil) de cada tipo según el anillo de la ciudad
    private static final int[] CORE = {50, 15, 15, 15, 25, 0, 40, 40, 40, 20, 15, 0};
    private static final int[] MID = {60, 15, 15, 25, 30, 25, 30, 30, 25, 20, 15, 20};
    private static final int[] OUT = {70, 15, 20, 20, 30, 20, 25, 15, 15, 15, 15, 30};
    // edificios fijos cerca del centro para que siempre haya uno de cada
    private static final int[][] LAND = {{1, 1, 3}, {-2, 1, 5}, {-2, -1, 1}, {1, -2, 2}, {2, 0, 6}, {-1, 2, 7},
            {0, -3, 8}, {-3, 0, 9}, {3, -1, 4}, {2, -2, 0}, {-3, -2, 10}, {3, 2, 11}};

    static CityPlan.T pick(CityPlan.T base, int ix, int iz, int dist) {
        if (iz == CityRail.IZ) {
            return CityRail.isStationIx(ix) ? CityPlan.T.STATION : CityPlan.T.RAIL;
        }
        for (int[] a : LAND) {
            if (a[0] == ix && a[1] == iz) {
                return TYPES[a[2]];
            }
        }
        if (base == CityPlan.T.PARK || base == CityPlan.T.PLAZA) {
            return base;
        }
        int r = CityPlan.h(ix, iz, 77) % 1000;
        int[] w = dist <= 192 ? CORE : dist <= 384 ? MID : OUT;
        int acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (r < acc) {
                return TYPES[i];
            }
        }
        return base;
    }

    /** Coordenadas (x, z) del frente del edificio de servicios fijo número i (orden de {@code PLACES} en el comando). */
    static int[] landmark(int i) {
        int[] best = null;
        for (int[] a : LAND) {
            if (a[2] == i) {
                CityPlan.Lot lot = CityPlan.lot(a[0], a[1], new java.util.HashMap<>());
                best = new int[] {lot.cx, lot.z0 + 3};
            }
        }
        return best == null ? new int[] {0, 0} : best;
    }

    static int[] stationSpot() {
        CityPlan.Lot lot = CityPlan.lot(-2, CityRail.IZ, new java.util.HashMap<>());
        return new int[] {lot.cx, CityRail.TZ0 - 2};
    }

    static int style(CityPlan.T t) {
        return switch (t) {
            case RESTO -> RESTO;
            case POLICE -> POLICE;
            case FIRE -> FIRE;
            case HOSP -> HOSP;
            case CYBER -> CYBER;
            case UNIV -> UNIV;
            case MALL -> MALL;
            case HOTEL -> HOTEL;
            case BANK -> BANK;
            case CINEMA -> CINEMA;
            case LIBRARY -> LIBRARY;
            case SCHOOL -> SCHOOL;
            case STATION -> STATION;
            default -> OFFICE;
        };
    }

    private static void fp(CityPlan.Lot l, int fw, int fd, int front, int floors) {
        l.fw = fw;
        l.fd = fd;
        l.fx0 = l.cx - fw / 2;
        l.fz0 = l.z0 + front;
        l.floors = floors;
    }

    static void footprint(CityPlan.Lot l, int lw, int ld, int hh) {
        switch (l.t) {
            case RESTO -> fp(l, Math.min(lw - 4, 34), Math.min(ld - 14, 24), 8, 1 + hh / 5 % 2);
            case POLICE -> fp(l, Math.min(lw - 8, 36), 20, 10, 2 + hh / 5 % 2);
            case FIRE -> fp(l, Math.min(lw - 8, 36), 22, 10, 2);
            case HOSP -> fp(l, lw - 8, 30, 10, 4 + hh / 5 % 3);
            case CYBER -> fp(l, Math.min(lw - 10, 30), 24, 8, 2);
            case UNIV -> fp(l, lw - 6, 28, 10, 3 + hh / 5 % 2);
            case MALL -> fp(l, lw - 4, Math.min(ld - 14, 38), 8, 3);
            case HOTEL -> fp(l, Math.min(lw - 8, 36), Math.min(ld - 12, 28), 8, 6 + hh / 5 % 5);
            case BANK -> fp(l, Math.min(lw - 10, 30), 22, 8, 2 + hh / 5 % 2);
            case CINEMA -> fp(l, Math.min(lw - 8, 36), Math.min(ld - 14, 30), 8, 2);
            case LIBRARY -> fp(l, Math.min(lw - 8, 34), 24, 8, 2);
            case SCHOOL -> fp(l, lw - 8, 18, 10, 2);
            case STATION -> {
                fp(l, Math.min(lw - 8, 40), 12, 4, 2);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Materiales

    private static final Block[] RESTO_W = {Blocks.BRICKS, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE,
            Blocks.WHITE_CONCRETE, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_CONCRETE};
    private static final Block[] SCHOOL_W = {Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
            Blocks.ORANGE_CONCRETE};
    private static final Block[] MALL_W = {Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE,
            Blocks.LIGHT_BLUE_CONCRETE, Blocks.PINK_CONCRETE};
    private static final Block[] HOTEL_W = {Blocks.SMOOTH_SANDSTONE, Blocks.CALCITE, Blocks.QUARTZ_BLOCK,
            Blocks.LIGHT_GRAY_CONCRETE};

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState wall(CityPlan.Lot l, int st) {
        int p = l.palette;
        return switch (st) {
            case RESTO -> s(RESTO_W[p % RESTO_W.length]);
            case POLICE -> s(Blocks.LIGHT_GRAY_CONCRETE);
            case FIRE -> s(Blocks.BRICKS);
            case HOSP -> s(Blocks.WHITE_CONCRETE);
            case CYBER -> s(Blocks.BLACK_CONCRETE);
            case UNIV -> s(Blocks.BRICKS);
            case MALL -> s(MALL_W[p % MALL_W.length]);
            case HOTEL -> s(HOTEL_W[p % HOTEL_W.length]);
            case BANK -> s(Blocks.SMOOTH_SANDSTONE);
            case CINEMA -> s(Blocks.GRAY_CONCRETE);
            case LIBRARY -> s(Blocks.STONE_BRICKS);
            case SCHOOL -> s(SCHOOL_W[p % SCHOOL_W.length]);
            default -> s(Blocks.SMOOTH_SANDSTONE);                    // estación
        };
    }

    static BlockState acc(CityPlan.Lot l, int st) {
        return switch (st) {
            case RESTO -> s(Blocks.DARK_OAK_PLANKS);
            case POLICE -> s(Blocks.BLUE_CONCRETE);
            case FIRE -> s(Blocks.WHITE_CONCRETE);
            case HOSP -> s(Blocks.LIGHT_BLUE_CONCRETE);
            case CYBER -> s(Blocks.PURPLE_CONCRETE);
            case UNIV -> s(Blocks.STONE_BRICKS);
            case MALL -> s(Blocks.WHITE_CONCRETE);
            case HOTEL -> s(Blocks.GRAY_CONCRETE);
            case BANK -> s(Blocks.QUARTZ_PILLAR);
            case CINEMA -> s(Blocks.RED_CONCRETE);
            case LIBRARY -> s(Blocks.DEEPSLATE_BRICKS);
            case SCHOOL -> s(Blocks.BLUE_CONCRETE);
            default -> s(Blocks.GRAY_CONCRETE);
        };
    }

    static BlockState glass(CityPlan.Lot l, int st) {
        return switch (st) {
            case HOSP -> s(Blocks.LIGHT_BLUE_STAINED_GLASS);
            case CYBER -> s(Blocks.PURPLE_STAINED_GLASS);
            case POLICE -> s(Blocks.BLUE_STAINED_GLASS);
            case MALL, STATION -> s(Blocks.CYAN_STAINED_GLASS);
            default -> s(Blocks.GLASS);
        };
    }

    static BlockState floor(int st) {
        return switch (st) {
            case RESTO -> s(Blocks.SPRUCE_PLANKS);
            case HOSP -> s(Blocks.SMOOTH_QUARTZ);
            case CYBER, CINEMA -> s(Blocks.GRAY_CONCRETE);
            case FIRE -> s(Blocks.GRAY_CONCRETE);
            case MALL, BANK, HOTEL -> s(Blocks.POLISHED_DIORITE);
            case UNIV, LIBRARY -> s(Blocks.POLISHED_ANDESITE);
            case SCHOOL, POLICE -> s(Blocks.SMOOTH_STONE);
            default -> s(Blocks.STONE_BRICKS);
        };
    }

    static BlockState roof(int st) {
        return switch (st) {
            case HOSP -> s(Blocks.WHITE_CONCRETE);
            case CYBER -> s(Blocks.BLACK_CONCRETE);
            default -> s(Blocks.GRAY_CONCRETE);
        };
    }

    static Block stairBlock(int st) {
        return switch (st) {
            case RESTO, SCHOOL -> Blocks.SPRUCE_STAIRS;
            case HOSP, HOTEL, MALL, BANK, UNIV -> Blocks.SMOOTH_QUARTZ_STAIRS;
            default -> Blocks.STONE_BRICK_STAIRS;
        };
    }

    // ------------------------------------------------------------------ Fachadas

    static int doorHalf(int st) {
        return switch (st) {
            case HOSP, MALL, UNIV, STATION, HOTEL, CINEMA, BANK -> 2;
            default -> 1;
        };
    }

    /** Puertas de garaje del cuartel de bomberos (en la fachada pz == 0). */
    static boolean fireBay(CityPlan.Lot l, int px) {
        return px >= 7 && (px - 7) % 9 < 6 && px <= l.fw - 4;
    }

    /** true si en esa posición de la fachada va cristal (si no, pared). */
    static boolean windowAt(int st, int rel, int m, int col) {
        boolean ground = rel <= 3;
        return switch (st) {
            case RESTO -> ground ? col % 5 != 0 : (m >= 2 && col % 4 == 2);
            case POLICE -> m >= 2 && col % 4 != 0;
            case FIRE -> rel > 4 && m >= 2 && col % 4 == 1;
            case HOSP -> m >= 2 && col % 4 != 0;
            case CYBER -> ground ? col % 6 != 0 : (m >= 2 && col % 3 == 1);
            case UNIV -> m >= 1 && col % 3 != 0;
            case MALL -> ground ? col % 8 != 0 : (m >= 2 && col % 6 < 4);
            case HOTEL -> m >= 2 && col % 3 != 0;
            case BANK -> ground ? col % 7 == 3 : (m >= 2 && col % 4 == 2);
            case CINEMA -> ground && col % 5 != 0;
            case LIBRARY -> m >= 2 && col % 3 == 1;
            case SCHOOL -> m >= 2 && col % 3 != 0;
            case STATION -> ground ? col % 4 != 0 : (m >= 2 && col % 4 == 2);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ Interiores

    private static void d(CityPlan.Out o, int rel, String id, Direction f) {
        CityBuildings.d(o, rel, id, f);
    }

    private static boolean part(CityPlan.Out o, int rel, String id, Direction f, int ox, int oz, int px, int pz) {
        return CityDeco.part(o, rel, id, f, ox, oz, px, pz);
    }

    private static void bars(CityPlan.Out o, int rel, Direction f) {
        for (int r = 0; r < 3; r++) {
            d(o, rel + r, "rejas_celda", f);
        }
    }

    static void furnish(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o, int st) {
        if (st == HOTEL && k > 0) {
            CityBuildings.apartFloor(l, px, pz, rel, o);      // habitaciones (ya respetan la escalera)
            return;
        }
        if (px <= 6 && pz <= 3) {
            return;                                           // zona de la escalera: se deja libre
        }
        switch (st) {
            case RESTO -> resto(l, px, pz, rel, k, o);
            case POLICE -> police(l, px, pz, rel, k, o);
            case FIRE -> fire(l, px, pz, rel, k, o);
            case HOSP -> hospital(l, px, pz, rel, k, o);
            case CYBER -> cyber(l, px, pz, rel, k, o);
            case UNIV -> university(l, px, pz, rel, k, o);
            case MALL -> mall(l, px, pz, rel, k, o);
            case HOTEL -> hotel(l, px, pz, rel, k, o);
            case BANK -> bank(l, px, pz, rel, k, o);
            case CINEMA -> cinema(l, px, pz, rel, k, o);
            case LIBRARY -> library(l, px, pz, rel, k, o);
            case SCHOOL -> school(l, px, pz, rel, k, o);
            case STATION -> station(l, px, pz, rel, k, o);
            default -> { }
        }
    }

    private static void resto(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k == 0 && pz <= 2 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (k == 0) {
            if (pz == fd - 2 && px >= 3 && px <= fw - 4) {                  // línea de cocina
                int u = px - 3;
                int sg = u / 2;
                int ox = 3 + sg * 2;
                switch (sg % 4) {
                    case 0 -> {
                        if (ox + 1 <= fw - 4) {
                            part(o, rel, "cocina_industrial", N, ox, fd - 2, px, pz);
                        }
                    }
                    case 1 -> {
                        if (ox + 1 <= fw - 4) {
                            part(o, rel, "horno_pizza", N, ox, fd - 2, px, pz);
                        }
                    }
                    case 2 -> d(o, rel, "nevera", N);
                    default -> d(o, rel, u % 2 == 0 ? "fregadero" : "mueble_cocina", N);
                }
                return;
            }
            if (pz == fd - 4 && px >= 4 && px <= fw - 5 && (px - 4) % 5 != 4) {   // mesadas de preparación
                d(o, rel, "mostrador", N);
                if ((px - 4) % 5 == 1) {
                    d(o, rel + 1, "cafetera", N);
                } else if ((px - 4) % 5 == 3) {
                    d(o, rel + 1, "tostadora", N);
                }
                return;
            }
            if (fw >= 24 && px >= 8 && px <= fw - 9 && (px - 8) % 3 < 2) {      // barra con taburetes
                int ox = px - (px - 8) % 3;
                if (pz == fd - 7 && ox + 1 <= fw - 9) {
                    part(o, rel, "barra_bar", N, ox, fd - 7, px, pz);
                    return;
                }
                if (pz == fd - 8 && ox + 1 <= fw - 9) {
                    d(o, rel, "banqueta", N);
                    return;
                }
                if (pz == fd - 5 && ox + 1 <= fw - 9) {
                    part(o, rel, "estante_botellas", N, ox, fd - 5, px, pz);
                    return;
                }
            }
        }
        int zMax = k == 0 ? fd - 10 : fd - 4;
        // cabinas contra las paredes laterales
        if ((px == 1 || px == fw - 2) && pz >= 6 && pz <= zMax) {
            int u = (pz - 6) % 3;
            int oz = pz - u;
            if (u < 2 && oz + 1 <= zMax) {
                if (px == 1) {
                    part(o, rel, "cabina_restaurante", E, 1, oz, px, pz);
                } else {
                    part(o, rel, "cabina_restaurante", W, fw - 2, oz + 1, px, pz);
                }
                return;
            }
        }
        if ((px == 2 || px == fw - 3) && pz >= 6 && (pz - 6) % 3 == 0 && pz + 1 <= zMax) {
            d(o, rel, "mesa_mantel", N);
            return;
        }
        // mesas con sillas
        if (px >= 8 && px <= fw - 5 && px % 3 == 1 && Math.abs(px - mid) > 1) {
            int r = pz % 4;
            if (r == 1 && pz >= 5 && pz <= zMax) {
                d(o, rel, "mesa_mantel", N);
            } else if (r == 0 && pz >= 4 && pz + 1 <= zMax) {
                d(o, rel, "silla", S);
            } else if (r == 2 && pz >= 6 && pz <= zMax + 1) {
                d(o, rel, "silla", N);
            }
            return;
        }
        if ((px == 2 || px == fw - 3) && pz == fd - 5) {
            d(o, rel, "planta", N);
        }
    }

    private static void police(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k > 0) {
            CityBuildings.officeFloor(l, px, pz, rel, k + 1, o);
            return;
        }
        if (pz <= 2 && Math.abs(px - mid) <= 2) {
            return;
        }
        // calabozos al fondo a la izquierda
        if (pz == fd - 8 && px >= 1 && px <= 13 && px != 4 && px != 10) {
            bars(o, rel, N);
            return;
        }
        if ((px == 7 || px == 13) && pz > fd - 8 && pz <= fd - 2) {
            bars(o, rel, E);
            return;
        }
        if (pz >= fd - 3 && ((px == 3 || px == 10) && pz >= fd - 3)) {
            part(o, rel, "cama", N, px, fd - 3, px, pz);
            return;
        }
        if (pz == fd - 2 && (px == 6 || px == 12)) {
            d(o, rel, "inodoro", N);
            return;
        }
        if (px <= 13 && pz > fd - 8) {
            return;
        }
        // recepción
        if (pz == 5 && px >= mid - 6 && px <= mid + 5 && px != mid) {
            d(o, rel, "mostrador", N);
            if ((px - mid) % 3 == 0) {
                d(o, rel + 1, "computadora", S);
            }
            return;
        }
        if (pz == 6 && px >= mid - 6 && px <= mid + 5 && (px - mid) % 3 == 0) {
            d(o, rel, "silla_oficina", N);
            return;
        }
        if ((px == 3 || px == fw - 4) && (pz == 8 || pz == 9)) {
            d(o, rel, "banco", px == 3 ? E : W);
            return;
        }
        // despachos
        if (pz == 10 && px >= 16 && px <= fw - 6 && px % 4 == 0) {
            d(o, rel, "escritorio", N);
            d(o, rel + 1, px % 8 == 0 ? "computadora" : "laptop", N);
            return;
        }
        if (pz == 9 && px >= 16 && px <= fw - 6 && px % 4 == 0) {
            d(o, rel, "silla_oficina", S);
            return;
        }
        // taquillas, armero y muro de monitores al fondo
        if (pz == fd - 2) {
            if (px >= 14 && px <= 17) {
                d(o, rel, "taquilla", N);
            } else if (px >= 20 && px <= 21) {
                part(o, rel, "muro_monitores", N, 20, fd - 2, px, pz);
            } else if (px >= fw - 12 && px <= fw - 3) {
                int ox = fw - 12 + (px - (fw - 12)) / 2 * 2;
                part(o, rel, "armero", N, ox, fd - 2, px, pz);
            }
            return;
        }
        if (pz == fd - 4 && px >= 20 && px <= 21) {
            d(o, rel, px == 20 ? "escritorio" : "silla_oficina", px == 20 ? N : S);
        }
    }

    private static void fire(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        if (k > 0) {
            CityBuildings.barracksFloor(l, px, pz, rel, k, o);
            return;
        }
        // vehículos en las cocheras
        for (int i = 0; i < 3; i++) {
            int bs = 7 + 9 * i;
            if (bs + 6 > fw - 3) {
                break;
            }
            int hv = CityPlan.h(l.seed, i, 5) % 5;
            if (hv == 0) {
                continue;
            }
            if (hv == 4) {
                if (part(o, rel, "ambulancia", N, bs + 2, 3, px, pz)) {
                    return;
                }
            } else {
                if (part(o, rel, "camion_bomberos_cabina", N, bs + 1, 2, px, pz)
                        || part(o, rel, "camion_bomberos_cuerpo", N, bs + 1, 4, px, pz)
                        || part(o, rel, "camion_bomberos_cuerpo", N, bs + 1, 6, px, pz)) {
                    return;
                }
            }
        }
        if (pz < 9) {
            return;
        }
        if (pz == fd - 2) {
            if (px >= 3 && px <= fw - 12 && px % 3 != 0) {
                d(o, rel, "taquilla", N);
            } else if (px == fw - 10) {
                d(o, rel, "nevera", N);
            } else if (px == fw - 9) {
                d(o, rel, "fregadero", N);
            } else if (px == fw - 8) {
                d(o, rel, "mueble_cocina", N);
            } else if (px == fw - 7) {
                d(o, rel, "cocina", N);
            }
            return;
        }
        if (pz == 10 && px % 8 == 2 && px > 8) {
            d(o, rel, "manguera_carrete", S);
        } else if (pz == fd - 6 && px == fw - 6) {
            d(o, rel, "mesa", N);
        } else if (pz == fd - 7 && px == fw - 6) {
            d(o, rel, "silla", S);
        } else if (pz == fd - 5 && px == fw - 6) {
            d(o, rel, "silla", N);
        } else if (pz == fd - 6 && (px == fw - 8 || px == fw - 4)) {
            d(o, rel, "silla", px == fw - 8 ? E : W);
        } else if (pz == fd - 4 && px == 4) {
            d(o, rel, "extintor", S);
        } else if (pz == fd - 5 && px >= 4 && px <= 8 && px % 2 == 0) {
            d(o, rel, "banco", N);
        }
    }

    private static void hospital(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        int F = l.floors;
        if (k == 0) {
            if (pz <= 3 && Math.abs(px - mid) <= 3) {
                return;
            }
            if (pz == 6 && px >= mid - 5 && px <= mid + 4 && px != mid) {
                d(o, rel, "mostrador", N);
                if ((px - mid) % 3 == 0) {
                    d(o, rel + 1, "computadora", S);
                }
                return;
            }
            if (pz == 7 && px >= mid - 5 && px <= mid + 4 && (px - mid) % 3 == 0) {
                d(o, rel, "silla_oficina", N);
                return;
            }
            if ((px == 3 || px == fw - 4) && pz >= 9 && pz <= 11) {
                d(o, rel, "sofa", px == 3 ? E : W);
                return;
            }
            if ((px == 5 || px == fw - 6) && pz == 10) {
                d(o, rel, "mesa", N);
                d(o, rel + 1, "planta", N);
                return;
            }
            if (pz == 8 && (px == 2 || px == fw - 3)) {
                d(o, rel, px == 2 ? "maquina_expendedora" : "dispensador_agua", px == 2 ? E : W);
                return;
            }
            // boxes de urgencias
            for (int row = 0; row < 2; row++) {
                int oz = 14 + row * 5;
                if (oz + 1 > fd - 8) {
                    break;
                }
                if (px >= 8 && px <= fw - 14 && (px - 8) % 6 == 0 && pz >= oz && pz <= oz + 1) {
                    part(o, rel, "camilla", N, px, oz, px, pz);
                    return;
                }
                if (pz == oz && px >= 7 && px <= fw - 13 && (px - 7) % 6 == 0) {
                    d(o, rel, "pie_suero", N);
                    return;
                }
                if (pz == oz + 1 && px >= 10 && px <= fw - 10 && (px - 10) % 6 == 0) {
                    d(o, rel, "monitor_vital", N);
                    return;
                }
            }
            // resonancia magnética y farmacia
            if (part(o, rel, "mri", N, fw - 8, fd - 7, px, pz)) {
                return;
            }
            if (pz == fd - 5 && px == fw - 10) {
                d(o, rel, "escritorio", N);
                d(o, rel + 1, "computadora", S);
                return;
            }
            if (pz == fd - 2 && px >= 3 && px <= 12) {
                d(o, rel, px % 3 == 0 ? "botiquin" : "estanteria", N);
                return;
            }
            return;
        }
        // plantas de habitaciones
        if (k == F - 1 && F >= 4) {                              // quirófano en la última planta
            int qx = mid - 2;
            int qz = fd / 2 - 1;
            if (px == qx && pz == qz) {
                d(o, rel + 2, "lampara_quirofano", N);
            }
            if (part(o, rel, "mesa_quirofano", N, qx, qz, px, pz)) {
                return;
            }
            if ((px == qx + 3 && pz == qz) || (px == qx - 2 && pz == qz + 1)) {
                d(o, rel, "pie_suero", N);
                return;
            }
            if (px == qx + 3 && pz == qz + 1) {
                d(o, rel, "monitor_vital", N);
                return;
            }
        }
        if (px >= 8 && px <= fw - 5 && (px - 8) % 4 <= 0) {
            if (pz >= 4 && pz <= 5) {
                part(o, rel, "cama_hospital", N, px, 4, px, pz);
                return;
            }
            if (pz >= fd - 6 && pz <= fd - 5) {
                part(o, rel, "cama_hospital", N, px, fd - 6, px, pz);
                return;
            }
        }
        if (px >= 9 && px <= fw - 4 && (px - 9) % 4 == 0 && (pz == 4 || pz == fd - 6)) {
            d(o, rel, "pie_suero", N);
        } else if (px >= 10 && px <= fw - 3 && (px - 10) % 4 == 0 && (pz == 4 || pz == fd - 6)) {
            d(o, rel, "mesita_noche", N);
        } else if (pz == fd / 2 && px == 3) {
            d(o, rel, "silla_ruedas", E);
        } else if (pz == fd / 2 && px == fw - 4) {
            d(o, rel, "extintor", W);
        }
    }

    private static void cyber(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int mid = fw / 2;
        if (k == 0 && pz <= 3 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (k == 0 && pz == 4 && px >= mid + 3 && px <= mid + 8) {
            d(o, rel, "mostrador", N);
            if (px % 2 == 0) {
                d(o, rel + 1, "cafetera", N);
            }
            return;
        }
        if (k == 0 && px == fw - 2 && (pz == 3 || pz == 5)) {
            d(o, rel, pz == 3 ? "maquina_expendedora" : "dispensador_agua", W);
            return;
        }
        if (pz >= 7 && pz <= l.fd - 3 && px >= 8 && px <= fw - 4 && px % 3 == 1 && Math.abs(px - mid) > 1) {
            int r = pz % 4;
            if (r == 3) {
                d(o, rel, "puesto_gamer", N);
            } else if (r == 2) {
                d(o, rel, "silla_oficina", S);
            }
            return;
        }
        if ((px == 2 || px == fw - 3) && (pz == l.fd - 2 || pz == 8)) {
            d(o, rel, "planta", N);
        } else if (px == fw - 2 && pz == 12 + k) {
            d(o, rel, "impresora", W);
        }
    }

    private static void classroom(CityPlan.Lot l, int px, int pz, int rel, int step, CityPlan.Out o) {
        int fw = l.fw;
        int mid = fw / 2;
        if (pz == 1 && px >= mid - 3 && px <= mid + 3) {
            d(o, rel + 1, "pizarra", S);
            return;
        }
        if (pz == 3 && px == mid) {
            d(o, rel, "mesa", N);
            return;
        }
        if (pz == 4 && px == mid) {
            d(o, rel, "silla_oficina", S);
            return;
        }
        if (pz >= 6 && pz <= l.fd - 3 && pz % step == 0 && px >= 8 && px <= fw - 6 && px % 2 == 0
                && Math.abs(px - mid) > 1) {
            d(o, rel, "pupitre", N);
        }
    }

    private static void university(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k == 0) {
            if (pz <= 3 && Math.abs(px - mid) <= 3) {
                return;
            }
            if (pz == 7 && px >= mid - 4 && px <= mid + 3 && px != mid) {
                d(o, rel, "mostrador", N);
                if ((px - mid) % 3 == 0) {
                    d(o, rel + 1, "computadora", S);
                }
                return;
            }
            if (px == mid && pz == 12) {
                d(o, rel, "estatua", N);
                return;
            }
            if (pz == fd - 2 && px >= 4 && px <= fw - 5) {
                int ox = px - px % 2;
                if (ox + 1 <= fw - 5 && Math.abs(ox - mid) > 1 && Math.abs(ox + 1 - mid) > 1) {
                    part(o, rel, "libreria", N, ox, fd - 2, px, pz);
                }
                return;
            }
            if (pz == fd - 5 && px >= 8 && px <= fw - 6 && px % 6 == 2) {
                d(o, rel, "mesa", N);
                return;
            }
            if ((pz == fd - 6 || pz == fd - 4) && px >= 8 && px <= fw - 6 && px % 6 == 2) {
                d(o, rel, "silla", pz == fd - 6 ? S : N);
                return;
            }
            if (pz == 10 && (px == 4 || px == fw - 5)) {
                d(o, rel, "banco", px == 4 ? E : W);
                return;
            }
            if (pz == 1 && px % 10 == 5 && px > 8) {
                d(o, rel + 1, "cuadro", S);
            }
            return;
        }
        if (k == 2) {                                            // laboratorios
            if (pz >= 6 && pz <= fd - 4 && px >= 8 && px <= fw - 6 && (px - 8) % 4 < 2) {
                int r = pz % 4;
                int ox = px - (px - 8) % 4;
                if (r == 2 && ox + 1 <= fw - 6) {
                    part(o, rel, "banco_lab", N, ox, pz, px, pz);
                    if ((px - 8) % 4 == 0) {
                        d(o, rel + 1, "microscopio", N);
                    }
                    return;
                }
                if (r == 1 && (px - 8) % 4 == 0) {
                    d(o, rel, "taburete", N);
                    return;
                }
            }
            if (pz == 1 && px >= mid - 3 && px <= mid + 3) {
                d(o, rel + 1, "pizarra", S);
            }
            return;
        }
        if (k >= 3) {
            CityBuildings.officeFloor(l, px, pz, rel, k + 1, o);
            return;
        }
        classroom(l, px, pz, rel, 2, o);
    }

    private static void school(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fd = l.fd;
        int mid = l.fw / 2;
        if (k == 0 && pz <= 3 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (pz == fd - 2 && px >= 3 && px <= l.fw - 4 && px % 2 == 1 && (px / 2) % 3 != 1) {
            d(o, rel, "taquilla", N);
            return;
        }
        if (pz >= fd - 4) {
            return;
        }
        classroom(l, px, pz, rel, 2, o);
    }

    private static void mallStore(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o, boolean left) {
        int fw = l.fw;
        int mid = fw / 2;
        int outer = left ? 1 : fw - 2;
        int inner = left ? mid - 6 : mid + 6;
        Direction face = left ? E : W;
        int center = (outer + inner) / 2;
        int sIdx = pz / 8;
        int u = pz % 8;
        int type = k == l.floors - 1 ? 2 : CityPlan.h(l.seed + k * 7, (left ? 0 : 31) + sIdx, 5) % 4;
        if (u == 0 && pz >= 8) {                                 // cristales divisorios
            for (int r = 0; r < 3; r++) {
                o.set(rel + r, s(Blocks.GLASS_PANE));
            }
            return;
        }
        if (u == 0 || pz > l.fd - 4) {
            return;
        }
        switch (type) {
            case 0 -> {
                if (px == outer && u <= 3) {
                    d(o, rel, "armario", face);
                } else if (px == outer && (u == 5 || u == 6)) {
                    d(o, rel, "perchero", face);
                } else if (px == inner && (u == 2 || u == 4 || u == 6)) {
                    d(o, rel, "maniqui", face);
                } else if (px == center && u == 4) {
                    d(o, rel, "mostrador", N);
                }
            }
            case 1 -> {
                if (px == outer && (u == 2 || u == 4 || u == 6)) {
                    d(o, rel, "tv", face);
                } else if (px == center && (u == 3 || u == 5)) {
                    d(o, rel, "mesa", N);
                    d(o, rel + 1, "laptop", N);
                }
            }
            case 2 -> {
                if (px == center && (u == 2 || u == 6)) {
                    d(o, rel, "mesa_terraza", N);
                } else if ((px == center - 1 || px == center + 1) && (u == 2 || u == 6)) {
                    d(o, rel, "silla", px < center ? E : W);
                } else if (px == outer && u == 4) {
                    d(o, rel, "mostrador", face);
                }
            }
            default -> {
                if (px == outer && u % 2 == 1) {
                    d(o, rel, "estanteria", face);
                } else if (px == center && u == 4) {
                    d(o, rel, "mesa", N);
                    d(o, rel + 1, "jarron", N);
                }
            }
        }
    }

    private static void mall(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k == 0 && pz <= 4 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (px >= 1 && px <= mid - 6) {
            mallStore(l, px, pz, rel, k, o, true);
            return;
        }
        if (px >= mid + 6 && px <= fw - 2) {
            mallStore(l, px, pz, rel, k, o, false);
            return;
        }
        // atrio central
        if (k == 0 && part(o, rel, "fuente_shopping", N, mid - 1, fd / 2 - 1, px, pz)) {
            return;
        }
        if (k == 0 && px == mid + 3 && pz == fd / 2 + 4) {
            d(o, rel, "directorio", W);
        } else if ((px == mid - 4 || px == mid + 4) && pz % 8 == 5) {
            d(o, rel, "banco", px < mid ? E : W);
        } else if ((px == mid - 3 || px == mid + 3) && pz % 8 == 2 && pz > 8) {
            d(o, rel, "planta", N);
        } else if (k == l.floors - 1 && px >= mid - 3 && px <= mid + 3 && pz % 4 == 2 && pz > 6 && (px - mid) % 3 == 0) {
            d(o, rel, "mesa_terraza", N);
        }
    }

    private static void hotel(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int mid = fw / 2;
        if (k > 0) {
            CityBuildings.apartFloor(l, px, pz, rel, o);
            return;
        }
        if (pz <= 3 && Math.abs(px - mid) <= 3) {
            return;
        }
        if (pz == 6 && px >= mid - 5 && px <= mid + 4 && px != mid) {
            d(o, rel, "mostrador", N);
            if ((px - mid) % 3 == 0) {
                d(o, rel + 1, "computadora", S);
            }
            return;
        }
        if (pz == 7 && px >= mid - 5 && px <= mid + 4 && (px - mid) % 3 == 0) {
            d(o, rel, "silla_oficina", N);
            return;
        }
        if ((px == 4 || px == fw - 5) && pz >= 10 && pz <= 12) {
            d(o, rel, "sofa", px == 4 ? E : W);
        } else if ((px == 6 || px == fw - 7) && pz == 11) {
            d(o, rel, "mesa", N);
            d(o, rel + 1, "jarron", N);
        } else if (px == mid && pz == 14) {
            d(o, rel, "estatua", N);
        } else if ((px == 2 || px == fw - 3) && (pz == 2 || pz == l.fd - 3)) {
            d(o, rel, "planta", N);
        } else if (pz == 8 && (px == 8 || px == fw - 9)) {
            d(o, rel, "carrito_compras", E);
        } else if (pz == l.fd - 2 && px % 7 == 3) {
            d(o, rel, "cuadro", N);
        }
    }

    private static void bank(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k > 0) {
            CityBuildings.officeFloor(l, px, pz, rel, k + 1, o);
            return;
        }
        if (pz <= 3 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (pz >= fd - 6) {                                       // cámara acorazada
            if (pz == fd - 6 && px >= 5 && px <= fw - 6 && Math.abs(px - mid) > 0) {
                for (int r = 0; r < 3; r++) {
                    o.set(rel + r, s(Blocks.IRON_BARS));
                }
            } else if (pz == fd - 2 && px >= 6 && px <= fw - 7 && px % 2 == 0) {
                d(o, rel, "caja_fuerte", N);
            }
            return;
        }
        if (pz == 6 && px >= 8 && px <= fw - 8 && (px - 8) % 3 < 2 && Math.abs(px - mid) > 1) {
            int ox = px - (px - 8) % 3;
            if (ox + 1 <= fw - 8 && Math.abs(ox - mid) > 1 && Math.abs(ox + 1 - mid) > 1) {
                part(o, rel, "ventanilla_banco", N, ox, 6, px, pz);
            }
            return;
        }
        if (pz == 7 && px >= 8 && px <= fw - 8 && (px - 8) % 3 == 0 && Math.abs(px - mid) > 1) {
            d(o, rel, "silla_oficina", N);
            return;
        }
        if (pz == 1 && px >= mid + 4 && px <= mid + 8 && px % 2 == 0) {
            d(o, rel, "cajero", S);
        } else if (pz == 3 && (px == 8 || px == fw - 9)) {
            d(o, rel, "banco", N);
        } else if ((px == 2 || px == fw - 3) && (pz == 2 || pz == fd - 8)) {
            d(o, rel, "planta", N);
        } else if (px == 9 && pz == fd - 8) {
            d(o, rel, "mesa", N);
            d(o, rel + 1, "computadora", S);
        }
    }

    private static void cinema(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k == 0) {
            if (pz <= 3 && Math.abs(px - mid) <= 2) {
                return;
            }
            if (pz == 6 && px >= mid - 8 && px <= mid - 4 && px % 2 == 0) {
                d(o, rel, "palomitas", N);
            } else if (pz == 6 && px >= mid + 3 && px <= mid + 8) {
                d(o, rel, "mostrador", N);
                if (px % 2 == 1) {
                    d(o, rel + 1, "computadora", S);
                }
            } else if (pz == 1 && px % 5 == 2 && px > 8) {
                d(o, rel + 1, "cartelera", S);
            } else if (pz == 9 && (px == 3 || px == fw - 4)) {
                d(o, rel, "sofa", px == 3 ? E : W);
            } else if (pz == 12 && (px == 8 || px == fw - 9)) {
                d(o, rel, "maquina_expendedora", N);
            } else if (pz == fd - 3 && (px == 3 || px == fw - 4)) {
                d(o, rel, "planta", N);
            }
            return;
        }
        // sala de proyección
        if (part(o, rel, "pantalla_cine", S, mid + 1, 1, px, pz)) {
            return;
        }
        if (pz >= 5 && pz <= fd - 4 && pz % 2 == 1 && px >= mid - 8 && px <= mid + 7 && px != mid - 5 && px != mid + 4) {
            d(o, rel, "butaca_cine", N);
        }
    }

    private static void library(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k == 0 && pz <= 3 && Math.abs(px - mid) <= 2) {
            return;
        }
        if (k == 0 && pz == 5 && px >= mid - 6 && px <= mid - 2) {
            d(o, rel, "mostrador", N);
            if (px % 2 == 0) {
                d(o, rel + 1, "computadora", S);
            }
            return;
        }
        if ((pz == 9 || pz == 16) && px >= 8 && px <= fw - 6 && (px - 8) % 3 < 2) {
            int ox = px - (px - 8) % 3;
            if (ox + 1 <= fw - 6 && Math.abs(ox - mid) > 1 && Math.abs(ox + 1 - mid) > 1) {
                part(o, rel, "libreria", N, ox, pz, px, pz);
            }
            return;
        }
        if (pz == 12 && px >= 8 && px <= fw - 6 && px % 5 == 3) {
            d(o, rel, "mesa", N);
            d(o, rel + 1, k == 0 ? "lampara" : "laptop", N);
            return;
        }
        if ((pz == 11 || pz == 13) && px >= 8 && px <= fw - 6 && px % 5 == 3) {
            d(o, rel, "silla", pz == 11 ? S : N);
            return;
        }
        if (pz == fd - 2 && px >= 4 && px <= fw - 5 && px % 4 == 0) {
            d(o, rel, "estanteria", N);
        } else if (pz == fd - 4 && px % 7 == 3 && px > 7) {
            d(o, rel, "sillon", N);
        } else if ((px == 2 || px == fw - 3) && pz == 7) {
            d(o, rel, "planta", N);
        }
    }

    private static void station(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        if (k > 0) {
            CityBuildings.officeFloor(l, px, pz, rel, k + 1, o);
            return;
        }
        if (Math.abs(px - mid) <= 2) {                            // paso de la entrada al andén
            if (pz == fd - 3 && px != mid) {
                d(o, rel, "torniquete", N);
            }
            return;
        }
        if (pz == 3 && ((px >= mid - 12 && px <= mid - 4) || (px >= mid + 4 && px <= mid + 12))) {
            int base = px < mid ? mid - 12 : mid + 4;
            int u = (px - base) % 3;
            if (u < 2) {
                part(o, rel, "ventanilla_banco", N, px - u, 3, px, pz);
            }
            return;
        }
        if (pz == 6 && (px - mid) % 5 == 0 && Math.abs(px - mid) >= 5 && Math.abs(px - mid) <= 15) {
            d(o, rel, "banco", N);
        } else if (pz == 8 && (px == mid - 6 || px == mid + 6)) {
            d(o, rel, "cajero", N);
        } else if (pz == fd - 2 && px % 6 == 1 && px > 8) {
            d(o, rel, "cartelera", N);
        } else if (pz == 8 && (px == 8 || px == fw - 9)) {
            d(o, rel, "maquina_expendedora", N);
        } else if (pz == 1 && (px - mid) % 7 == 0 && Math.abs(px - mid) > 3) {
            d(o, rel + 1, "reloj_pared", S);
        } else if (pz == fd - 3 && (px == 7 || px == fw - 8)) {
            d(o, rel, "planta", N);
        }
    }

    // ------------------------------------------------------------------ Azotea y exteriores

    static void roofTop(CityPlan.Lot l, int px, int pz, int H, CityPlan.Out o, int st) {
        if (st == HOSP) {
            int dx = px - l.fw / 2;
            int dz = pz - l.fd / 2;
            int r2 = dx * dx + dz * dz;
            if (r2 <= 36) {
                boolean letter = (Math.abs(dx) == 1 && Math.abs(dz) <= 2) || (dz == 0 && Math.abs(dx) <= 1);
                o.set(H, letter ? s(Blocks.RED_CONCRETE) : r2 >= 25 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.WHITE_CONCRETE));
            }
        } else if (st == UNIV || st == BANK || st == HOTEL || st == MALL) {
            if (px % 9 == 4 && pz % 9 == 4 && px > 2 && pz > 2 && px < l.fw - 3 && pz < l.fd - 3) {
                o.set(H + 1, s(Blocks.LIGHT_GRAY_CONCRETE));
                o.set(H + 2, s(Blocks.IRON_BARS));
            }
        }
    }

    private static void car(CityPlan.Out o, String id, int px, int pz, int ox, boolean north) {
        if (north) {
            part(o, 1, id, N, ox, -9, px, pz);
        } else {
            part(o, 1, id, S, ox + 1, -6, px, pz);
        }
    }

    static void yard(CityPlan.Lot l, int st, int x, int z, int lx, int lz, int px, int pz, int w, int dd,
                     CityPlan.Out o) {
        int fw = l.fw;
        int fd = l.fd;
        int mid = fw / 2;
        boolean front = pz < 0 && pz >= -(l.fz0 - l.z0) + 1 && px >= 0 && px < fw;
        switch (st) {
            case POLICE -> {
                if (front) {
                    int[] xs = {mid - 14, mid - 9, mid + 8, mid + 13};
                    for (int i = 0; i < 4; i++) {
                        if (CityPlan.h(l.seed, i, 33) % 4 != 0) {
                            car(o, "patrulla", px, pz, xs[i], i % 2 == 0);
                        }
                    }
                    if (pz == -1 && px % 8 == 0 && Math.abs(px - mid) > 3) {
                        d(o, 1, "bolardo", N);
                    }
                }
            }
            case HOSP -> {
                if (front) {
                    car(o, "ambulancia", px, pz, mid - 11, true);
                    if (CityPlan.h(l.seed, 1, 34) % 3 != 0) {
                        car(o, "ambulancia", px, pz, mid + 9, false);
                    }
                    if (pz == -1 && px % 8 == 2 && Math.abs(px - mid) > 3) {
                        d(o, 1, "bolardo", N);
                    }
                }
            }
            case FIRE -> {
                if (pz < 0 && px >= 6 && px < fw - 3) {
                    o.ground = s(Blocks.GRAY_CONCRETE);
                }
                if (front && pz == -2 && (px == 3 || px == fw - 4)) {
                    d(o, 1, "hidrante", N);
                }
            }
            case RESTO -> {
                if (front && pz >= -5 && pz <= -3) {
                    if (pz == -4 && px % 6 == 2 && Math.abs(px - mid) > 3) {
                        d(o, 1, "mesa_terraza", N);
                    } else if ((pz == -5 || pz == -3) && (px - 2) % 6 == 0 && Math.abs(px - mid) > 3) {
                        d(o, 1, "silla", pz == -5 ? S : N);
                    }
                }
            }
            case UNIV, SCHOOL -> {
                if (pz < 0 && Math.abs(px - mid) > 2 && lx > 2 && lx < w - 3 && lz > 1) {
                    o.ground = s(Blocks.GRASS_BLOCK);
                    if (pz == -3 && px % 7 == 2 && Math.abs(px - mid) > 4 && px >= 0 && px < fw) {
                        d(o, 1, "banco", S);
                    }
                }
                if (st == UNIV && front && px == mid - 5 && pz == -5) {
                    d(o, 1, "estatua", S);
                }
                if (st == SCHOOL && pz >= fd + 3 && px >= 3 && px < fw - 3 && lz < dd - 2) {
                    o.ground = s(Blocks.GREEN_CONCRETE);
                    boolean line = px == 3 || px == fw - 4 || pz == fd + 3 || pz == dd - (l.fz0 - l.z0) - 4
                            || px == mid;
                    if (line && lz < dd - 3) {
                        o.ground = s(Blocks.WHITE_CONCRETE);
                    }
                    if (pz == fd + 6 && px >= 5 && px <= 7) {
                        part(o, 1, "columpio", N, 5, fd + 6, px, pz);
                    }
                    if (px == fw - 9 && pz >= fd + 6 && pz <= fd + 8) {
                        part(o, 1, "tobogan", N, fw - 9, fd + 6, px, pz);
                    }
                }
            }
            default -> {
                if (front) {
                    if (pz == -3 && px % 12 == 3 && Math.abs(px - mid) > 3) {
                        d(o, 1, "banco", S);
                    } else if (pz == -1 && px % 16 == 8 && Math.abs(px - mid) > 3) {
                        d(o, 1, "farola", N);
                    } else if (pz == -3 && px % 12 == 9 && Math.abs(px - mid) > 3) {
                        d(o, 1, "papelera_calle", N);
                    }
                }
            }
        }
    }
}
