package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mueble decorativo genérico: gira según hacia dónde mires al colocarlo y tiene la forma de sus cajas.
 * Los televisores, computadoras y lámparas se encienden y apagan con clic derecho.
 */
public class DecoBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DecoBlock> CODEC = simpleCodec(p -> new DecoBlock(p, ModDeco.DEFS.get(0)));
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final boolean toggle;
    private final int mode;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public DecoBlock(Properties properties, ModDeco.Def def) {
        super(properties);
        this.toggle = def.toggle();
        this.mode = def.mode();
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
        for (int turns = 0; turns < 4; turns++) {
            VoxelShape s = Shapes.empty();
            for (double[] b : def.boxes()) {
                double x0 = b[0];
                double z0 = b[2];
                double x1 = b[3];
                double z1 = b[5];
                for (int t = 0; t < turns; t++) {
                    double nx0 = 16 - z1;
                    double nx1 = 16 - z0;
                    double nz0 = x0;
                    double nz1 = x1;
                    x0 = nx0;
                    x1 = nx1;
                    z0 = nz0;
                    z1 = nz1;
                }
                double y0 = Math.max(0, b[1]);
                double y1 = Math.min(16, b[4]);
                x0 = Math.max(0, Math.min(16, x0));
                x1 = Math.max(0, Math.min(16, x1));
                z0 = Math.max(0, Math.min(16, z0));
                z1 = Math.max(0, Math.min(16, z1));
                if (x1 - x0 < 0.01 || z1 - z0 < 0.01 || y1 - y0 < 0.01) {
                    continue;
                }
                s = Shapes.or(s, Block.box(x0, y0, z0, x1, y1, z1));
            }
            if (s.isEmpty()) {
                s = Block.box(0, 0, 0, 16, 16, 16);
            }
            shapes[turns] = s;
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(FACING)) {
            case EAST:
                return shapes[1];
            case SOUTH:
                return shapes[2];
            case WEST:
                return shapes[3];
            default:
                return shapes[0];
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!toggle) {
            return InteractionResult.PASS;
        }
        boolean on = !state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, on), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 0.7f : 0.5f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // --- modo 1: farola automática (se enciende de noche o con lluvia)
    private boolean wantsOn(Level level) {
        long t = level.getDayTime() % 24000L;
        return (t >= 12800L && t < 23200L) || level.isRaining();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (mode == 1 && !level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (mode == 1 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (mode != 1) {
            return;
        }
        boolean on = wantsOn(level);
        if (state.getValue(LIT) != on) {
            level.setBlock(pos, state.setValue(LIT, on), 3);
        }
        level.scheduleTick(pos, this, 100);
    }
}
