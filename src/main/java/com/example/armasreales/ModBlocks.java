package com.example.armasreales;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArmasRealesMod.MOD_ID);

    public static final DeferredBlock<Block> RADIO = BLOCKS.registerBlock("radio", RadioBlock::new,
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion());

    private ModBlocks() {
    }
}
