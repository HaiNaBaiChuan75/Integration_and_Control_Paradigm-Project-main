package com.hainabaichuan75.iac_p.vehicle.cabin;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class CabinBlock<BE extends CabinBlockEntity> extends Block implements EntityBlock {
    protected static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CabinBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
    public boolean assemble(ServerLevel level, BlockPos pos, BlockState state) {
        var result = SubLevelAssemblyHelper.gatherConnectedBlocks(pos, level, 1000, null);

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            onAssembleFailed(level, pos, state, result);
            return false;
        }

        // 3. 组装为物理 SubLevel
        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();
        if (bounds == null) {return false;}
        if (blocks == null) {return false;}
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks((ServerLevel) level, pos, blocks, bounds);
        return true;
    }

    public void onAssembleFailed(ServerLevel level, BlockPos pos, BlockState state,
                                 SubLevelAssemblyHelper.GatherResult result) {

    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {return InteractionResult.PASS;}
        boolean result = assemble((ServerLevel) level, pos, state);
        if (result){return InteractionResult.SUCCESS;}
        return InteractionResult.PASS;
    }

    @Override
    public abstract @Nullable BE newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState);

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.mirror(mirror);
    }
}
