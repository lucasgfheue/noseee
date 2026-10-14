package com.example.armasreales;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Radio: al usarla (clic derecho) capta la señal de la base militar más cercana y muestra sus coordenadas. */
public class RadioBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);
    public static final TagKey<Structure> BASES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "base_militar_radio"));
    private static final int SEARCH_CHUNKS = 120;
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    private static final VoxelShape NS = Block.box(3, 0, 5, 13, 9, 11);
    private static final VoxelShape EW = Block.box(5, 0, 3, 11, 9, 13);

    public RadioBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction d = state.getValue(FACING);
        return d == Direction.NORTH || d == Direction.SOUTH ? NS : EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        long now = server.getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < 60) {
            return InteractionResult.CONSUME;
        }
        LAST_USE.put(player.getUUID(), now);
        server.playSound(null, pos, SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.BLOCKS, 0.6f, 0.5f);
        BlockPos found = server.findNearestMapStructure(BASES, pos, SEARCH_CHUNKS, false);
        if (found == null) {
            player.displayClientMessage(Component.literal(
                    "[Radio] ...kshhh... Solo se oye estática. No hay señal de ninguna base cerca."), false);
            return InteractionResult.CONSUME;
        }
        int x = found.getX() + 8;      // el centro de la base es el centro de su chunk
        int z = found.getZ() + 8;
        int dist = (int) Math.sqrt(pos.distSqr(new BlockPos(x, pos.getY(), z)));
        player.displayClientMessage(Component.literal(
                "[Radio] ...kshhh... Aquí Base Militar, cambio. Nuestra posición: X: " + x + "  Z: " + z
                        + "  (a unos " + dist + " bloques de ti)"), false);
        return InteractionResult.CONSUME;
    }
}
