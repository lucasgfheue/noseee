package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;


/**
 * Mueble de tamaño real que ocupa varios bloques (ancho x alto x fondo). Al colocarlo se rellenan todas las celdas
 * hacia delante/derecha/atrás según donde mires; al romper una parte se rompe todo y se devuelve un solo objeto.
 * La celda 0 es la que se coloca. Índice de celda = x + ancho * (y + alto * z), con x a la derecha, z hacia atrás.
 */
public class MultiDecoBlock extends DecoBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 15);

    private final int w;
    private final int h;
    private final int d;
    private final int mask;
    private final VoxelShape[][] cellShapes = new VoxelShape[16][4];

    public MultiDecoBlock(Properties properties, ModDeco.Def def) {
        super(properties, def);
        this.w = def.w();
        this.h = def.h();
        this.d = def.d();
        int m = 0;
        for (int idx = 0; idx < w * h * d && idx < 16; idx++) {
            double[][] cell = cellBoxes(def, idx);
            if (cell.length > 0) {
                m |= 1 << idx;
                for (int t = 0; t < 4; t++) {
                    cellShapes[idx][t] = buildShape(cell, t);
                }
            }
        }
        this.mask = m;
    }

    private static double[][] cellBoxes(ModDeco.Def def, int idx) {
        int lx = idx % def.w();
        int ly = (idx / def.w()) % def.h();
        int lz = idx / (def.w() * def.h());
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (double[] b : def.boxes()) {
            double x0 = Math.max(b[0], 16 * lx) - 16 * lx;
            double x1 = Math.min(b[3], 16 * lx + 16) - 16 * lx;
            double y0 = Math.max(b[1], 16 * ly) - 16 * ly;
            double y1 = Math.min(b[4], 16 * ly + 16) - 16 * ly;
            double z0 = Math.max(b[2], 16 * lz) - 16 * lz;
            double z1 = Math.min(b[5], 16 * lz + 16) - 16 * lz;
            if (x1 - x0 > 0.01 && y1 - y0 > 0.01 && z1 - z0 > 0.01) {
                out.add(new double[] {x0, y0, z0, x1, y1, z1});
            }
        }
        return out.toArray(new double[0][]);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    // ---- geometría de las celdas

    private BlockPos offset(BlockPos origin, Direction f, int idx) {
        int lx = idx % w;
        int ly = (idx / w) % h;
        int lz = idx / (w * h);
        int dx;
        int dz;
        switch (f) {
            case EAST -> {
                dx = -lz;
                dz = lx;
            }
            case SOUTH -> {
                dx = -lx;
                dz = -lz;
            }
            case WEST -> {
                dx = lz;
                dz = -lx;
            }
            default -> {
                dx = lx;
                dz = lz;
            }
        }
        return origin.offset(dx, ly, dz);
    }

    private BlockPos originOf(BlockPos pos, Direction f, int idx) {
        BlockPos rel = offset(BlockPos.ZERO, f, idx);
        return pos.subtract(rel);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction f = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int idx = 1; idx < w * h * d && idx < 16; idx++) {
            if ((mask & (1 << idx)) == 0) {
                continue;
            }
            BlockPos p = offset(pos, f, idx);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, f).setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction f = state.getValue(FACING);
        for (int idx = 1; idx < w * h * d && idx < 16; idx++) {
            if ((mask & (1 << idx)) != 0) {
                level.setBlock(offset(pos, f, idx), this.defaultBlockState().setValue(FACING, f).setValue(PART, idx), 3);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            Direction f = state.getValue(FACING);
            int part = state.getValue(PART);
            BlockPos origin = originOf(pos, f, part);
            if (!player.isCreative()) {
                Block.popResource(level, origin, new ItemStack(this));
            }
            for (int idx = 0; idx < w * h * d && idx < 16; idx++) {
                if ((mask & (1 << idx)) == 0) {
                    continue;
                }
                BlockPos p = offset(origin, f, idx);
                if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int idx = state.getValue(PART);
        int turns = switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape s = cellShapes[idx][turns];
        return s != null ? s : super.getShape(state, level, pos, context);
    }
}
