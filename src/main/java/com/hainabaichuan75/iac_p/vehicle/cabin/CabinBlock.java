package com.hainabaichuan75.iac_p.vehicle.cabin;

import com.hainabaichuan75.iac_p.util.AssemblyUtil;
import com.hainabaichuan75.iac_p.util.SubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CabinBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public CabinBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }


    // TODO: 高级组装方法
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {return InteractionResult.PASS;}
        if (player.isShiftKeyDown()) {return InteractionResult.PASS;}
        ServerLevel serverLevel = (ServerLevel) level;
        SubLevel subLevel = SubLevelUtil.getSubLevelAt(serverLevel, pos);
        if (subLevel == null) {
            SubLevel result = AssemblyUtil.assemble(serverLevel, pos);
            if (result!=null){return InteractionResult.SUCCESS;}
            return InteractionResult.FAIL;
        }

        AssemblyUtil.disassembleSubLevel(level, subLevel, pos);

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof CabinBlockEntity cabinBe) {
                    cabinBe.tickClient((ClientLevel) lvl, pos, st);
                }
            };
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CabinBlockEntity cabinBe) {
                cabinBe.tickServer((ServerLevel) lvl, pos, st);
            }
        };
    }

    @Nullable
    @Override
    public abstract BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state);

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.mirror(mirror);
    }
}
