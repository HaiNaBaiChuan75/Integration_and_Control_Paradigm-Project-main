package com.hainabaichuan75.iac_p.block.simplewheel;

import com.hainabaichuan75.iac_p.vehicle.CapabilityProviderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleWheelBlock extends Block implements CapabilityProviderBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 轮子 默认位于方块中心
    public double radius;
    public double thick;
    public double verticalOffset;
    public double horizontalOffset;

    // 悬挂
    public double stiffness;
    public double damping;

    public SimpleWheelBlock(Properties properties, double radius, double verticalOffset, double horizontalOffset,
                            double stiffness, double damping) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));

        this.radius = radius;
        this.verticalOffset = verticalOffset;
        this.horizontalOffset = horizontalOffset;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        // 根据 radius;thick;verticalOffset;horizontalOffset;返回方块
        switch (state.getValue(FACING)) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
            default:
        }
    }

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
    public @NotNull CapabilityProviderBlock.VehicleCapability getCapability(@NotNull BlockState state,
                                                                            @NotNull ServerLevel level,
                                                                            @NotNull BlockPos pos) {
        SimpleWheelBlockEntity blockEntity = (SimpleWheelBlockEntity) level.getBlockEntity(pos);
        return blockEntity;
    }
}
