package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.vehicle.cabin.CabinBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseCabinBlock extends CabinBlock {

    public BaseCabinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // ============================
    //  渲染
    // ============================

    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BaseCabinBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BaseCabinBlockEntity(pos, state);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.block();
    }

}
