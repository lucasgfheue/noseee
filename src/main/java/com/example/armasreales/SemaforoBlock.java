package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Semáforo funcional: ciclo de 20 s (verde 8 s, ámbar 2 s, rojo 10 s). Los semáforos orientados
 * norte/sur van en contrafase con los orientados este/oeste. La fase se calcula con la hora del mundo,
 * así que todos los semáforos de la ciudad están sincronizados.
 */
public class SemaforoBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SemaforoBlock> CODEC = simpleCodec(SemaforoBlock::new);
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);
    private static final VoxelShape SHAPE = Block.box(4, 0, 5, 12, 16, 9);

    public SemaforoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PHASE, 2));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PHASE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    static int phaseFor(Direction facing, long time) {
        long t = time % 400L;
        if (facing == Direction.EAST || facing == Direction.WEST) {
            t = (t + 200L) % 400L;
        }
        if (t < 160L) {
            return 0;
        }
        return t < 200L ? 1 : 2;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 5);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 5);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int ph = phaseFor(state.getValue(FACING), level.getGameTime());
        if (state.getValue(PHASE) != ph) {
            level.setBlock(pos, state.setValue(PHASE, ph), 3);
        }
        level.scheduleTick(pos, this, 10);
    }
}
