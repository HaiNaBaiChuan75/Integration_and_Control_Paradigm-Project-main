package com.hainabaichuan75.iac_p.block.nbt_test;

import com.hainabaichuan75.iac_p.vehicle.api.ModuleHostBE;
import com.hainabaichuan75.iac_p.vehicle.api.ModuleHostBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class NbtTestBlock extends ModuleHostBlock {

    public NbtTestBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull ModuleHostBE createBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new NbtTestBlockEntity(pos, state);
    }
}
