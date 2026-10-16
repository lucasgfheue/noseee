package com.example.armasreales;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Edificios, parcelas, grúas y vehículos de bloques de la ciudad. */
final class CityBuildings {
    private CityBuildings() {}

    static final int SKY = 0, OFFICE = 1, APART = 2, SHOPS = 3, HOUSE = 4, VILLAGE = 5, HALL = 6, HANGAR = 7,
            BARRACKS = 8, BARN = 9, MILL = 10;

    static final Block[] FAC = {Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
            Blocks.LIGHT_BLUE_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.RED_CONCRETE, Blocks.PINK_CONCRETE,
            Blocks.WHITE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.BLUE_CONCRETE,
            Blocks.GREEN_CONCRETE, Blocks.BROWN_CONCRETE, Blocks.PURPLE_CONCRETE};
    static final Block[] GLS = {Blocks.GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS};
    static final Block[] ACC = {Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.WHITE_CONCRETE,
            Blocks.LIGHT_GRAY_CONCRETE};
    static final Block[] OFF = {Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.STONE_BRICKS,
            Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_SANDSTONE, Blocks.CALCITE};
    static final Block[] PLANK = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.DARK_OAK_PLANKS, Blocks.ACACIA_PLANKS};
    static final Block[] LOGS = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.DARK_OAK_LOG,
            Blocks.ACACIA_LOG};
    static final Block[] SHED = {Blocks.LIGHT_GRAY_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.WHITE_CONCRETE,
            Blocks.GRAY_CONCRETE};

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState leaf() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState ladder() {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
    }

    // ------------------------------------------------------------------ Materiales por estilo

    private static BlockState wallB(CityPlan.Lot l, int st) {
        int p = l.palette;
        return switch (st) {
            case SKY -> s(ACC[(p >> 4) % ACC.length]);
            case OFFICE -> s(OFF[p % OFF.length]);
            case APART, SHOPS -> s(FAC[p % FAC.length]);
            case HOUSE -> s(FAC[(p >> 3) % FAC.length]);
            case VILLAGE -> s(PLANK[p % PLANK.length]);
            case HALL -> s(SHED[p % SHED.length]);
            case HANGAR -> s(Blocks.GRAY_CONCRETE);
            case BARRACKS -> s(Blocks.GREEN_TERRACOTTA);
            case BARN -> s(Blocks.RED_CONCRETE);
            default -> s(Blocks.WHITE_CONCRETE);
        };
    }

    private static BlockState accB(CityPlan.Lot l, int st) {
        int p = l.palette;
        return switch (st) {
            case SKY, OFFICE -> s(ACC[(p >> 4) % ACC.length]);
            case APART, SHOPS, HOUSE -> s(p % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE);
            case VILLAGE -> s(LOGS[p % LOGS.length]);
            case HALL, HANGAR -> s(Blocks.GRAY_CONCRETE);
            case BARRACKS -> s(Blocks.BLACK_CONCRETE);
            case BARN -> s(Blocks.WHITE_CONCRETE);
            default -> s(Blocks.BROWN_CONCRETE);
        };
    }

    private static BlockState glassB(CityPlan.Lot l, int st) {
        if (st == SKY) {
            return s(GLS[(l.palette >> 7) % GLS.length]);
        }
        return s(st == OFFICE && l.palette % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.GLASS);
    }

    private static BlockState floorB(int st) {
        return switch (st) {
            case SKY, OFFICE -> s(Blocks.POLISHED_ANDESITE);
            case APART, SHOPS -> s(Blocks.SMOOTH_STONE);
            case HOUSE, BARN -> s(Blocks.OAK_PLANKS);
            case VILLAGE -> s(Blocks.SPRUCE_PLANKS);
            case HALL, HANGAR -> s(Blocks.GRAY_CONCRETE);
            case BARRACKS -> s(Blocks.STONE);
            default -> s(Blocks.STONE_BRICKS);
        };
    }

    private static BlockState roofB(CityPlan.Lot l, int st) {
        return switch (st) {
            case HOUSE, APART, SHOPS -> s(Blocks.GRAY_CONCRETE);
            case BARN -> s(Blocks.WHITE_CONCRETE);
            case VILLAGE -> s(PLANK[(l.palette + 2) % PLANK.length]);
            default -> s(Blocks.GRAY_CONCRETE);
        };
    }

    // ------------------------------------------------------------------ Fachadas

    private static BlockState face(CityPlan.Lot l, int st, int px, int pz, int rel, int H) {
        int fw = l.fw;
        int fd = l.fd;
        boolean hall = st == HALL || st == HANGAR || st == BARN;
        boolean ns = pz == 0 || pz == fd - 1;
        boolean corner = (px == 0 || px == fw - 1) && (pz == 0 || pz == fd - 1);
        int col = ns ? px : pz;
        BlockState wall = wallB(l, st);
        BlockState acc = accB(l, st);
        BlockState gl = glassB(l, st);
        if (pz == 0 || (st == HANGAR && pz == fd - 1)) {                       // puertas
            if (hall) {
                int hw = st == HANGAR ? 6 : st == BARN ? 2 : 3;
                if (Math.abs(px - fw / 2) < hw && rel <= (st == HANGAR ? 9 : st == BARN ? 4 : 5)) {
                    return null;
                }
            } else {
                int half = st == SKY ? 2 : 1;
                if (pz == 0 && px >= fw / 2 - half && px < fw / 2 + half && rel <= 3) {
                    return null;
                }
            }
        }
        if (corner) {
            return acc;
        }
        if (rel == H) {
            return acc;
        }
        if (hall) {
            if (rel == 1) {
                return acc;
            }
            if (st == BARN) {
                return col % 6 == 0 ? acc : wall;
            }
            if (rel >= H - 3 && rel <= H - 1 && col % 3 != 0 && st != HANGAR) {
                return gl;
            }
            return wall;
        }
        int m = rel & 3;
        if (m == 0) {
            return acc;
        }
        switch (st) {
            case SKY:
                return col % 4 == 0 ? acc : gl;
            case OFFICE:
                return (m == 1 || col % 5 == 0) ? wall : gl;
            case APART:
                if (pz == 0 && rel > 4 && px % 6 == 2 && m <= 2) {
                    return null;
                }
                return (m >= 2 && col % 3 == 1) ? gl : wall;
            case SHOPS:
                if (pz == 0 && rel <= 3) {
                    return px % 6 == 0 ? acc : gl;
                }
                return (m >= 2 && col % 3 == 1) ? gl : wall;
            case HOUSE:
                return (m >= 2 && col % 4 == 2) ? gl : wall;
            case VILLAGE:
                return col % 4 == 0 ? acc : ((m >= 2 && col % 4 == 2) ? gl : wall);
            case BARRACKS:
                return (m == 2 && col % 3 == 1) ? gl : wall;
            default:
                return (m >= 2 && col == (ns ? fw / 2 : fd / 2)) ? gl : wall;
        }
    }

    // ------------------------------------------------------------------ Edificio

    private static void building(CityPlan.Lot l, int px, int pz, CityPlan.Out o, int st, int F) {
        int fw = l.fw;
        int fd = l.fd;
        boolean hall = st == HALL || st == HANGAR || st == BARN;
        int H = hall ? (st == HANGAR ? 14 : 10) : 4 * F;
        if (px < 0 || pz < 0 || px >= fw || pz >= fd) {
            extras(l, px, pz, o, st, F);
            return;
        }
        BlockState acc = accB(l, st);
        o.set(0, floorB(st));
        boolean edge = px == 0 || pz == 0 || px == fw - 1 || pz == fd - 1;
        if (edge) {
            for (int r = 1; r <= H; r++) {
                BlockState b = face(l, st, px, pz, r, H);
                if (b != null) {
                    o.set(r, b);
                }
            }
            o.set(H + 1, acc);
            return;
        }
        if (px == 1 && pz == 1) {                                           // hueco de escalera
            for (int r = 1; r <= H + 1; r++) {
                o.set(r, ladder());
            }
            return;
        }
        BlockState roof = roofB(l, st);
        if (hall) {
            o.set(H, px % 8 == 4 && pz % 8 == 4 ? s(Blocks.SEA_LANTERN) : roof);
            hallContents(l, px, pz, o, st);
        } else {
            for (int k = 1; k <= F; k++) {
                BlockState slab = k == F ? roof : floorB(st);
                if (px % 6 == 3 && pz % 6 == 3) {
                    slab = s(Blocks.SEA_LANTERN);
                }
                o.set(4 * k, slab);
            }
            for (int k = 0; k < F; k++) {
                furnish(l, px, pz, 4 * k + 1, k, o, st);
            }
        }
        roofExtras(l, px, pz, H, o, st);
    }

    private static void furnish(CityPlan.Lot l, int px, int pz, int rel, int k, CityPlan.Out o, int st) {
        int fw = l.fw;
        int fd = l.fd;
        switch (st) {
            case SKY, OFFICE -> {
                if (px % 8 == 4 && pz % 8 == 4) {
                    for (int r = 0; r < 3; r++) {
                        o.set(rel + r, accB(l, st));
                    }
                } else if (px % 6 == 2 && pz % 5 == 2) {
                    o.set(rel, s(Blocks.SPRUCE_SLAB));
                } else {
                    o.set(rel, s(Blocks.LIGHT_GRAY_CARPET));
                }
            }
            case APART -> {
                if (px % 12 == 0 && px > 1 && Math.abs(pz - fd / 2) > 1) {
                    for (int r = 0; r < 3; r++) {
                        o.set(rel + r, wallB(l, st));
                    }
                } else if (pz == fd - 2 && px % 12 == 4) {
                    o.set(rel, s(Blocks.CRAFTING_TABLE));
                } else {
                    o.set(rel, s(Blocks.WHITE_CARPET));
                }
            }
            case SHOPS -> {
                if (pz == 3 && px % 8 >= 2 && px % 8 <= 5) {
                    o.set(rel, s(Blocks.SPRUCE_SLAB));
                } else if (pz == fd - 2 && px % 4 < 2) {
                    o.set(rel, s(Blocks.BOOKSHELF));
                    o.set(rel + 1, s(Blocks.BOOKSHELF));
                }
            }
            case HOUSE, VILLAGE -> {
                if (px == fw / 2 && pz == fd / 2) {
                    o.set(rel, s(Blocks.OAK_FENCE));
                    o.set(rel + 1, s(Blocks.OAK_PRESSURE_PLATE));
                } else if (px == fw - 2 && pz == fd - 2) {
                    o.set(rel, k == 0 ? s(Blocks.CRAFTING_TABLE) : s(Blocks.BOOKSHELF));
                } else if (px == fw - 2 && pz >= 2 && pz <= 3) {
                    o.set(rel, s(Blocks.BOOKSHELF));
                    o.set(rel + 1, s(Blocks.BOOKSHELF));
                } else if (Math.abs(px - fw / 2) <= 2 && Math.abs(pz - fd / 2) <= 1) {
                    o.set(rel, s(Blocks.RED_CARPET));
                }
            }
            case BARRACKS -> {
                if (px % 3 == 1 && (pz == 2 || pz == fd - 3)) {
                    o.set(rel, s(Blocks.GREEN_WOOL));
                }
            }
            default -> {
                if (px == fw - 2 && pz == fd - 2) {
                    o.set(rel, s(Blocks.HAY_BLOCK));
                    o.set(rel + 1, s(Blocks.HAY_BLOCK));
                }
            }
        }
    }

    private static void hallContents(CityPlan.Lot l, int px, int pz, CityPlan.Out o, int st) {
        int fw = l.fw;
        int fd = l.fd;
        if (Math.abs(px - fw / 2) <= 3) {
            return;
        }
        if (st == HANGAR) {
            if (l.cx < -620) {
                tank(px - fw / 2, pz - 6, o, true);
            } else if (px % 6 < 3 && pz % 7 < 3 && pz > 3) {
                int hgt = 1 + CityPlan.h(px / 6, pz / 7, l.seed) % 3;
                for (int r = 1; r <= hgt; r++) {
                    o.set(r, s(Blocks.SPRUCE_PLANKS));
                }
            }
            return;
        }
        if (st == BARN) {
            if (pz > fd / 2 && px % 4 < 2 && pz % 4 < 3) {
                o.set(1, s(Blocks.HAY_BLOCK));
                if (CityPlan.h(px / 4, pz / 4, l.seed) % 2 == 0) {
                    o.set(2, s(Blocks.HAY_BLOCK));
                }
            }
            return;
        }
        if (l.t == CityPlan.T.FACTORY) {
            if (pz == fd / 2) {
                o.set(1, s(Blocks.BLACK_CONCRETE));
            } else if (px % 7 == 3 && pz % 7 == 3) {
                o.set(1, s(Blocks.IRON_BLOCK));
                o.set(2, s(Blocks.IRON_BLOCK));
                o.set(3, s(Blocks.IRON_BARS));
            }
            return;
        }
        if (px % 5 < 2 && pz % 6 >= 3 && pz % 6 <= 4 && pz > 3) {
            int hgt = 1 + CityPlan.h(px / 5, pz / 6, l.seed) % 3;
            for (int r = 1; r <= hgt; r++) {
                o.set(r, s(r % 2 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS));
            }
        }
    }

    private static void roofExtras(CityPlan.Lot l, int px, int pz, int H, CityPlan.Out o, int st) {
        int fw = l.fw;
        int fd = l.fd;
        switch (st) {
            case SKY -> {
                if (px >= 3 && px < fw - 3 && pz >= 3 && pz < fd - 3) {
                    for (int r = 1; r <= 4; r++) {
                        o.set(H + r, r == 3 ? s(Blocks.SEA_LANTERN) : accB(l, st));
                    }
                    if (px == fw / 2 && pz == fd / 2) {
                        for (int r = 5; r <= 18; r++) {
                            o.set(H + r, s(Blocks.IRON_BARS));
                        }
                        o.set(H + 19, s(Blocks.RED_CONCRETE));
                        o.set(H + 20, s(Blocks.SEA_LANTERN));
                    }
                }
            }
            case OFFICE -> {
                if (px % 9 == 4 && pz % 9 == 4) {
                    o.set(H + 1, s(Blocks.LIGHT_GRAY_CONCRETE));
                    o.set(H + 2, s(Blocks.IRON_BARS));
                }
            }
            case APART -> {
                if (px == fw - 4 && pz == fd - 4) {
                    for (int r = 1; r <= 3; r++) {
                        o.set(H + r, s(Blocks.COPPER_BLOCK));
                    }
                }
            }
            case MILL -> {
                if (px >= 2 && px <= fw - 3 && pz >= 2 && pz <= fd - 3) {
                    for (int r = 1; r <= 3; r++) {
                        o.set(H + r, accB(l, st));
                    }
                }
            }
            default -> { }
        }
    }

    private static void extras(CityPlan.Lot l, int px, int pz, CityPlan.Out o, int st, int F) {
        if (px < 0 || px >= l.fw) {
            return;
        }
        if (st == APART && pz >= -2 && pz <= -1 && px % 6 >= 1 && px % 6 <= 3) {
            for (int k = 1; k < F; k++) {
                o.set(4 * k, s(Blocks.WHITE_CONCRETE));
                if (pz == -2 || px % 6 != 2) {
                    o.set(4 * k + 1, s(Blocks.IRON_BARS));
                }
            }
        } else if (st == SHOPS && pz >= -2 && pz <= -1) {
            o.set(3, (px / 2) % 2 == 0 ? s(Blocks.WHITE_CONCRETE) : wallB(l, st));
        }
    }

    // ------------------------------------------------------------------ Vegetación y vehículos

    private static void tree(CityPlan.Out o, int ox, int oz, int r, int th) {
        int a = Math.abs(ox);
        int b = Math.abs(oz);
        if (a > r || b > r) {
            return;
        }
        if (a == 0 && b == 0) {
            for (int k = 1; k <= th + 1; k++) {
                o.set(k, s(Blocks.OAK_LOG));
            }
            o.set(th + 2, leaf());
            return;
        }
        if (!(a == r && b == r)) {
            o.set(th, leaf());
            o.set(th + 1, leaf());
        }
        if (a <= r - 1 && b <= r - 1) {
            o.set(th + 2, leaf());
        }
    }

    private static void treeGrid(CityPlan.Out o, int lx, int lz, int w, int d, int pitch, int salt, int chance, int margin) {
        int cx = Math.floorDiv(lx, pitch);
        int cz = Math.floorDiv(lz, pitch);
        int tx = cx * pitch + pitch / 2;
        int tz = cz * pitch + pitch / 2;
        if (tx < margin || tx >= w - margin || tz < margin || tz >= d - margin) {
            return;
        }
        if (CityPlan.h(cx, cz, salt) % 100 >= chance) {
            return;
        }
        tree(o, lx - tx, lz - tz, 2, 3);
    }

    static boolean tank(int du, int dv, CityPlan.Out o, boolean green) {
        if (du < -2 || du > 2 || dv < -3 || dv > 5) {
            return false;
        }
        BlockState hull = s(green ? Blocks.GREEN_CONCRETE : Blocks.GRAY_CONCRETE);
        boolean any = false;
        if (Math.abs(du) == 2 && dv <= 3) {
            o.set(1, s(Blocks.BLACK_CONCRETE));
            o.set(2, s(Blocks.BLACK_CONCRETE));
            return true;
        }
        if (Math.abs(du) <= 1 && dv <= 3) {
            o.set(1, hull);
            o.set(2, hull);
            any = true;
            if (dv >= -1 && dv <= 2) {
                o.set(3, hull);
                if (du == 0 && dv >= 0 && dv <= 1) {
                    o.set(4, s(Blocks.GRAY_CONCRETE));
                }
            }
        }
        if (du == 0 && dv >= 3) {
            o.set(3, s(Blocks.GRAY_CONCRETE));
            any = true;
        }
        return any;
    }

    static boolean truck(int du, int dv, CityPlan.Out o, int colorIdx) {
        if (du < -1 || du > 1 || dv < -5 || dv > 4) {
            return false;
        }
        BlockState cab = s(CityRoads.CAR[Math.floorMod(colorIdx, CityRoads.CAR.length)]);
        BlockState box = s(FAC[Math.floorMod(colorIdx * 3 + 1, FAC.length)]);
        o.set(1, du == 0 ? s(Blocks.GRAY_CONCRETE) : s(Blocks.BLACK_CONCRETE));
        if (dv >= 3) {
            o.set(2, cab);
            o.set(3, cab);
            if (dv == 4) {
                o.set(3, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            }
        } else if (dv <= 2 && dv >= -5) {
            o.set(2, box);
            o.set(3, box);
            o.set(4, box);
        }
        return true;
    }

    private static void trucks(CityPlan.Lot l, int lx, int lz, int w, int d, int zStart, CityPlan.Out o) {
        if (lz < zStart || lx < 4) {
            return;
        }
        int ku = (lx - 4) / 8;
        int du = (lx - 4) % 8 - 1;
        int kv = (lz - zStart) / 14;
        int dv = (lz - zStart) % 14 - 5;
        if (ku >= (w - 8) / 8 || kv > 3 || lz + 6 > d) {
            return;
        }
        if (CityPlan.h(ku, kv, l.seed + 3) % 2 == 0) {
            truck(du, dv, o, CityPlan.h(ku, kv, l.seed + 4));
        }
    }

    private static void barriers(CityPlan.Lot l, int lx, int lz, CityPlan.Out o) {
        if (lz % 10 == 0 && lx % 6 < 4 && CityPlan.h(lx / 6, lz / 10, l.seed + 8) % 6 == 0) {
            o.set(1, s(Blocks.GRAY_CONCRETE));
            o.set(2, s(Blocks.STONE_SLAB));
        }
    }

    // ------------------------------------------------------------------ Grúas

    static void crane(int x, int z, CityPlan.Out o) {
        if (Math.abs(x) > 620 || CityRoads.cat(x) < 2) {
            return;
        }
        int i = CityPlan.lineIdx(x);
        int coast = CityPlan.coastOfIdx(i);
        int xc = CityPlan.lineStart(i) + 36;
        int dx = x - xc;
        int dz = z - coast;
        if (Math.abs(dx) > 4 || dz < -11 || dz > 24) {
            return;
        }
        BlockState yel = s(Blocks.YELLOW_CONCRETE);
        boolean legX = Math.abs(dx) >= 3;
        boolean legZ = dz <= -10 && dz >= -11 || dz >= -4 && dz <= -3;
        if (legX && legZ) {
            for (int r = 1; r <= 30; r++) {
                o.set(r, yel);
            }
        } else if (legX && dz >= -9 && dz <= -5) {
            o.set(12, s(Blocks.IRON_BARS));
            o.set(22, s(Blocks.IRON_BARS));
        } else if (dz >= -11 && dz <= -3 && Math.abs(dx) <= 2 && (dz == -10 || dz == -4)) {
            o.set(12, s(Blocks.IRON_BARS));
            o.set(22, s(Blocks.IRON_BARS));
        }
        o.set(31, yel);
        if (Math.abs(dx) >= 3 || dx == 0) {
            o.set(32, s(Blocks.ORANGE_CONCRETE));
        }
        if (dz >= 8 && dz <= 10 && Math.abs(dx) <= 1) {
            o.set(33, s(Blocks.WHITE_CONCRETE));
            o.set(34, dz == 10 ? s(Blocks.LIGHT_BLUE_STAINED_GLASS) : s(Blocks.WHITE_CONCRETE));
            o.set(35, s(Blocks.GRAY_CONCRETE));
        }
        if (dz == 15 && dx == 0) {
            for (int r = 15; r <= 30; r++) {
                o.set(r, s(Blocks.IRON_BARS));
            }
        }
        if (dz >= 14 && dz <= 16 && Math.abs(dx) <= 1) {
            o.set(13, s(Blocks.GRAY_CONCRETE));
            o.set(14, s(Blocks.GRAY_CONCRETE));
        }
    }

    // ------------------------------------------------------------------ Parcelas

    private static void paving(int lx, int lz, CityPlan.Out o) {
        o.ground = ((lx / 6 + lz / 6) & 1) == 0 ? s(Blocks.LIGHT_GRAY_CONCRETE) : s(Blocks.WHITE_CONCRETE);
    }

    private static void cornerTrees(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        int[][] c = {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}};
        for (int[] p : c) {
            int ox = lx - p[0];
            int oz = lz - p[1];
            if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                int px = lx + l.x0 - l.fx0;
                int pz = lz + l.z0 - l.fz0;
                if (px >= -1 && pz >= -1 && px <= l.fw && pz <= l.fd) {
                    return;
                }
                if (ox == 0 && oz == 0) {
                    o.set(1, s(Blocks.OAK_LOG));
                    o.set(2, s(Blocks.OAK_LOG));
                    o.set(3, s(Blocks.OAK_LOG));
                }
                o.set(4, leaf());
                o.set(5, leaf());
                return;
            }
        }
    }

    static void column(CityPlan.Lot l, int x, int z, int lx, int lz, CityPlan.Out o) {
        int w = l.x1 - l.x0;
        int d = l.z1 - l.z0;
        int px = x - l.fx0;
        int pz = z - l.fz0;
        switch (l.t) {
            case SKY -> {
                paving(lx, lz, o);
                building(l, px, pz, o, SKY, l.floors);
                cornerTrees(l, lx, lz, w, d, o);
            }
            case OFFICE -> {
                paving(lx, lz, o);
                building(l, px, pz, o, OFFICE, l.floors);
                cornerTrees(l, lx, lz, w, d, o);
            }
            case APART -> {
                paving(lx, lz, o);
                building(l, px, pz, o, APART, l.floors);
                cornerTrees(l, lx, lz, w, d, o);
            }
            case SHOPS -> {
                paving(lx, lz, o);
                building(l, px, pz, o, SHOPS, l.floors);
                cornerTrees(l, lx, lz, w, d, o);
            }
            case HOUSE, VILLAGE, FARM -> house(l, x, z, lx, lz, w, d, px, pz, o);
            case PARK -> park(l, lx, lz, w, d, o);
            case PLAZA -> plaza(l, lx, lz, w, d, o);
            case WAREHOUSE -> {
                o.ground = s(Blocks.LIGHT_GRAY_CONCRETE);
                building(l, px, pz, o, HALL, l.floors);
                trucks(l, lx, lz, w, d, l.fz0 - l.z0 + l.fd + 2, o);
            }
            case HANGAR -> {
                o.ground = s(Blocks.GRAY_CONCRETE);
                building(l, px, pz, o, HANGAR, l.floors);
                if (l.cx < -620) {
                    military(l, lx, lz, w, d, o);
                }
            }
            case CONTAINERS -> containers(l, lx, lz, o);
            case FUEL -> fuel(l, lx, lz, w, d, o);
            case BARRACKS -> {
                o.ground = s(Blocks.GRAY_CONCRETE);
                building(l, px, pz, o, BARRACKS, l.floors);
                military(l, lx, lz, w, d, o);
                barriers(l, lx, lz, o);
            }
            case MOTOR -> {
                o.ground = s(Blocks.GRAY_CONCRETE);
                int ku = (lx - 3) / 9;
                int ru = (lx - 3) % 9;
                int kv = (lz - 4) / 14;
                int rv = (lz - 4) % 14;
                if (lx >= 3 && lz >= 4 && ru < 5 && rv < 9 && ku < (w - 6) / 9 && kv < (d - 6) / 14) {
                    tank(ru - 2, rv - 3, o, true);
                }
                military(l, lx, lz, w, d, o);
            }
            case RADAR -> radar(l, lx, lz, w, d, o);
            case FACTORY -> {
                o.ground = s(Blocks.LIGHT_GRAY_CONCRETE);
                building(l, px, pz, o, HALL, l.floors);
                if (lx >= 1 && lx <= 3 && lz >= 1 && lz <= 3) {
                    for (int r = 1; r <= 40; r++) {
                        o.set(r, r >= 36 && r <= 38 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.BRICKS));
                    }
                }
                trucks(l, lx, lz, w, d, l.fz0 - l.z0 + l.fd + 2, o);
            }
            case FIELD -> field(l, lx, lz, w, d, o);
            case ORCHARD -> {
                treeGrid(o, lx, lz, w, d, 7, l.seed, 85, 3);
            }
            case MEADOW -> {
                treeGrid(o, lx, lz, w, d, 12, l.seed + 1, 40, 4);
                int hv = CityPlan.h(x, z, 91) % 40;
                if (hv < 3) {
                    o.set(1, s(hv == 0 ? Blocks.POPPY : hv == 1 ? Blocks.DANDELION : Blocks.SHORT_GRASS));
                }
            }
            case BARN -> {
                if (lx > 6 && lx < w - 6 && lz > 12) {
                    o.ground = s(Blocks.COARSE_DIRT);
                }
                building(l, px, pz, o, BARN, l.floors);
                if (lz > 24 && (lx == 3 || lx == w - 4 || lz == d - 3)) {
                    o.set(1, s(Blocks.OAK_FENCE));
                }
            }
            case WELL -> well(l, lx, lz, w, d, o);
            case WINDMILL -> windmill(l, x, z, lx, lz, o);
            case EMPTY -> {
                if (l.z0 >= CityPlan.PORT_Z) {
                    o.ground = s(Blocks.LIGHT_GRAY_CONCRETE);
                    barriers(l, lx, lz, o);
                    trucks(l, lx, lz, w, d, 4, o);
                    if (l.cx < -620) {
                        military(l, lx, lz, w, d, o);
                    }
                }
            }
            default -> { }
        }
    }

    private static void house(CityPlan.Lot l, int x, int z, int lx, int lz, int w, int d, int px, int pz,
                              CityPlan.Out o) {
        int F = l.t == CityPlan.T.VILLAGE ? Math.max(2, l.floors + l.seed % 2) : l.floors;
        int st = l.t == CityPlan.T.VILLAGE ? VILLAGE : HOUSE;
        boolean inFoot = px >= 0 && pz >= 0 && px < l.fw && pz < l.fd;
        if (!inFoot) {
            // sendero hasta la puerta
            if (px >= l.fw / 2 - 1 && px <= l.fw / 2 && pz < 0) {
                o.ground = s(Blocks.STONE_BRICKS);
            } else if (l.t == CityPlan.T.VILLAGE && pz < 0 && px >= l.fw / 2 - 1) {
                o.ground = s(Blocks.GRASS_BLOCK);
            }
            // valla delantera
            if (lz == 2 && (px < l.fw / 2 - 2 || px > l.fw / 2 + 1) && lx >= 2 && lx < w - 2) {
                o.set(1, s(Blocks.OAK_FENCE));
            }
            if (pz > l.fd + 2) {
                treeGrid(o, lx, lz, w, d, 8, l.seed, 55, 4);
            }
            if (l.t == CityPlan.T.FARM && pz > l.fd + 1 && pz < l.fd + 10 && lx > 8 && lx < w - 8) {
                if (lx % 6 == 0 && lz % 4 == 0) {
                    o.set(1, s(Blocks.HAY_BLOCK));
                }
            }
        }
        building(l, px, pz, o, st, F);
    }

    private static void park(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        boolean path = Math.abs(lx - w / 2) <= 1 || Math.abs(lz - d / 2) <= 1;
        if (path) {
            o.ground = s(Blocks.STONE_BRICKS);
            if (lx == w / 2 + 2 && lz == d / 2 + 2) {
                for (int r = 1; r <= 5; r++) {
                    o.set(r, s(Blocks.COBBLESTONE_WALL));
                }
                o.set(6, s(Blocks.GLOWSTONE));
            }
            return;
        }
        if ((lx == w / 2 - 3 || lx == w / 2 + 3) && lz % 8 < 3 && lz > 3 && lz < d - 3 && lx != 0) {
            o.set(1, s(Blocks.OAK_SLAB));
            return;
        }
        if (Math.abs(lx - w / 2) > 3 && Math.abs(lz - d / 2) > 3) {
            treeGrid(o, lx, lz, w, d, 8, l.seed, 70, 4);
        }
    }

    private static void plaza(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        paving(lx, lz, o);
        int dx = lx - w / 2;
        int dz = lz - d / 2;
        int q = dx * dx + dz * dz;
        if (q <= 24) {
            o.set(0, s(Blocks.WATER));
            if (q <= 2) {
                for (int r = 1; r <= 4; r++) {
                    o.set(r, s(Blocks.QUARTZ_BLOCK));
                }
                o.set(5, s(Blocks.SEA_LANTERN));
            }
        } else if (q <= 38) {
            o.set(1, s(Blocks.STONE_BRICKS));
        }
        int[][] c = {{6, 6}, {w - 7, 6}, {6, d - 7}, {w - 7, d - 7}};
        for (int[] p : c) {
            tree(o, lx - p[0], lz - p[1], 2, 3);
        }
        if ((lx == 3 || lx == w - 4) && lz % 8 < 3 && lz > 10 && lz < d - 10) {
            o.set(1, s(Blocks.SPRUCE_SLAB));
        }
    }

    private static void containers(CityPlan.Lot l, int lx, int lz, CityPlan.Out o) {
        o.ground = s(Blocks.GRAY_CONCRETE);
        int px = lx - 3;
        int pz = lz - 3;
        if (px < 0 || pz < 0) {
            return;
        }
        int col = px / 4;
        int row = pz / 14;
        if (px % 4 > 2 || pz % 14 > 11 || col >= 11 || row >= 4) {
            return;
        }
        if (CityPlan.h(col, row, l.seed) % 6 == 0) {
            return;
        }
        int hgt = 1 + CityPlan.h(col, row, l.seed + 2) % 4;
        Block c = CityRoads.CAR[CityPlan.h(col, row, l.seed + 5) % CityRoads.CAR.length];
        Block c2 = FAC[CityPlan.h(col, row, l.seed + 6) % FAC.length];
        for (int r = 1; r <= hgt * 3; r++) {
            o.set(r, s(((r - 1) / 3) % 2 == 0 ? c : c2));
        }
    }

    private static void fuel(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        o.ground = s(Blocks.GRAY_CONCRETE);
        cyl(lx - 13, lz - 13, o);
        cyl(lx - (w - 14), lz - (d - 14), o);
    }

    private static void cyl(int dx, int dz, CityPlan.Out o) {
        int q = dx * dx + dz * dz;
        if (q > 81) {
            return;
        }
        boolean ring = q >= 64;
        if (ring) {
            if (dz < -5 && Math.abs(dx) <= 1) {
                for (int r = 4; r <= 14; r++) {
                    o.set(r, s(r >= 7 && r <= 8 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE));
                }
            } else {
                for (int r = 1; r <= 14; r++) {
                    o.set(r, s(r >= 7 && r <= 8 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE));
                }
            }
            o.set(15, s(Blocks.IRON_BARS));
        } else {
            o.set(0, s(Blocks.BLACK_CONCRETE));
        }
        o.set(14, s(Blocks.LIGHT_GRAY_CONCRETE));
    }

    private static void military(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        int cxl = Math.min(lx, w - 1 - lx);
        int czl = Math.min(lz, d - 1 - lz);
        if (cxl <= 2 && czl <= 2) {                              // torre de vigilancia
            for (int r = 1; r <= 4; r++) {
                o.set(r, s(Blocks.GRAY_CONCRETE));
            }
            o.set(5, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            o.set(6, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            o.set(7, s(Blocks.GRAY_CONCRETE));
            return;
        }
        if (cxl == 0 || czl == 0) {
            if ((lz == 0 || lz == d - 1) && Math.abs(lx - w / 2) <= 3) {
                return;
            }
            o.set(1, s(Blocks.IRON_BARS));
            o.set(2, s(Blocks.IRON_BARS));
        }
    }

    private static void radar(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        o.ground = s(Blocks.GRAY_CONCRETE);
        military(l, lx, lz, w, d, o);
        int dx = lx - 12;
        int dz = lz - 12;
        int ad = Math.max(Math.abs(dx), Math.abs(dz));
        if (ad <= 1) {
            for (int r = 1; r <= 10; r++) {
                o.set(r, s(Blocks.LIGHT_GRAY_CONCRETE));
            }
        }
        if (ad <= 3) {
            o.set(11, s(Blocks.WHITE_CONCRETE));
            if (ad == 3) {
                o.set(12, s(Blocks.WHITE_CONCRETE));
            }
            if (ad == 0) {
                for (int r = 12; r <= 16; r++) {
                    o.set(r, s(Blocks.IRON_BARS));
                }
                o.set(17, s(Blocks.RED_CONCRETE));
            }
        }
        // helipuerto
        int hx = lx - (w - 16);
        int hz = lz - (d - 16);
        int q = hx * hx + hz * hz;
        if (q <= 100) {
            o.ground = q > 81 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE);
            if (q <= 81 && ((Math.abs(hx) == 2 && Math.abs(hz) <= 3) || (hz == 0 && Math.abs(hx) <= 2))) {
                o.ground = s(Blocks.WHITE_CONCRETE);
            }
        }
    }

    private static void field(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        boolean border = lx == 1 || lx == w - 2 || lz == 1 || lz == d - 2;
        if (border) {
            if (Math.abs(lx - w / 2) >= 2 && Math.abs(lz - d / 2) >= 2) {
                o.set(1, s(Blocks.OAK_FENCE));
            }
            return;
        }
        if (lx < 3 || lx > w - 4 || lz < 3 || lz > d - 4) {
            return;
        }
        if (lz % 5 == 0) {                                      // acequia
            o.floorY = 63;
            o.ground = s(Blocks.DIRT);
            o.waterTop = 64;
            return;
        }
        o.ground = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        Block crop = switch (l.seed % 3) {
            case 0 -> Blocks.WHEAT;
            case 1 -> Blocks.CARROTS;
            default -> Blocks.POTATOES;
        };
        o.set(1, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
    }

    private static void well(CityPlan.Lot l, int lx, int lz, int w, int d, CityPlan.Out o) {
        o.ground = ((lx / 4 + lz / 4) & 1) == 0 ? s(Blocks.COBBLESTONE) : s(Blocks.STONE_BRICKS);
        int dx = lx - w / 2;
        int dz = lz - d / 2;
        int ad = Math.max(Math.abs(dx), Math.abs(dz));
        if (ad <= 1) {
            o.set(0, s(Blocks.WATER));
        } else if (ad == 2) {
            o.set(1, s(Blocks.COBBLESTONE));
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                o.set(2, s(Blocks.OAK_FENCE));
                o.set(3, s(Blocks.OAK_FENCE));
            }
        }
        if (ad <= 2) {
            o.set(4, s(Blocks.OAK_SLAB));
        }
        // puestos de mercado
        int[][] st = {{9, 9}, {w - 10, 9}, {9, d - 10}, {w - 10, d - 10}};
        for (int[] p : st) {
            int ox = lx - p[0];
            int oz = lz - p[1];
            if (Math.abs(ox) <= 2 && Math.abs(oz) <= 1) {
                if (Math.abs(ox) == 2 && Math.abs(oz) == 1) {
                    o.set(1, s(Blocks.OAK_FENCE));
                    o.set(2, s(Blocks.OAK_FENCE));
                    o.set(3, s(Blocks.OAK_FENCE));
                }
                o.set(4, ((ox + 2) % 2 == 0) ? s(Blocks.RED_WOOL) : s(Blocks.WHITE_WOOL));
                if (oz == 0 && Math.abs(ox) <= 1) {
                    o.set(1, s(Blocks.HAY_BLOCK));
                }
            }
        }
    }

    private static void windmill(CityPlan.Lot l, int x, int z, int lx, int lz, CityPlan.Out o) {
        if (l.fw == 0) {
            l.fw = 9;
            l.fd = 9;
            l.fx0 = l.cx - 4;
            l.fz0 = l.cz - 4;
            l.floors = 5;
        }
        treeGrid(o, lx, lz, l.x1 - l.x0, l.z1 - l.z0, 10, l.seed, 25, 6);
        int px = x - l.fx0;
        int pz = z - l.fz0;
        if (Math.abs(x - l.cx) <= 6 && Math.abs(z - l.cz) <= 6 && (px < 0 || pz < 0 || px >= 9 || pz >= 9)) {
            o.ground = s(Blocks.COBBLESTONE);
            o.set(1, null);
        }
        building(l, px, pz, o, MILL, 5);
        if (z == l.cz + 5) {                                   // aspas
            int dx = x - l.cx;
            for (int rel = 7; rel <= 25; rel++) {
                int dy = rel - 16;
                int ax = Math.abs(dx);
                int ay = Math.abs(dy);
                if (ax == 0 && ay == 0) {
                    o.set(rel, s(Blocks.DARK_OAK_LOG));
                } else if (ax == ay && ax <= 9) {
                    o.set(rel, s(Blocks.SPRUCE_PLANKS));
                } else if (Math.abs(ax - ay) == 1 && Math.min(ax, ay) >= 2 && Math.max(ax, ay) <= 8) {
                    o.set(rel, s(Blocks.WHITE_WOOL));
                }
            }
        }
    }
}
