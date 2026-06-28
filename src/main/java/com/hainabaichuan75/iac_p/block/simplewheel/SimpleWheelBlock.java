package com.hainabaichuan75.iac_p.block.simplewheel;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SimpleWheelBlock extends HorizontalDirectionalBlock implements EntityBlock, BlockSubLevelCollisionShape {
    // 轮子 默认位于方块中心
    public double radius;
    public double thick;
    public double verticalOffset;
    public double horizontalOffset;

    // 悬挂
    public double stiffness;
    public double damping;
    public double friction;

    public SimpleWheelBlock(Properties properties, double radius, double thick, double verticalOffset,
                            double horizontalOffset, double stiffness, double damping, double friction) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));

        this.radius = radius;
        this.thick = thick;
        this.verticalOffset = verticalOffset;
        this.horizontalOffset = horizontalOffset;
        this.stiffness = stiffness;
        this.damping = damping;
        this.friction = friction;
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec((p) -> this);
    }

    Vector3dc getWheelCenter(BlockState state) {
        Direction direction = state.getValue(SimpleWheelBlock.FACING);
        return new Vector3d(0.5, 0.5 - verticalOffset, 0.5).add(direction.step().mul((float) horizontalOffset));
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        Direction direction = state.getValue(SimpleWheelBlock.FACING);
        Vector3dc wheelCenter = getWheelCenter(state);

        Vector3d min =
                new Vector3d(-radius).add(wheelCenter).add(direction.step().absolute().mul((float) (radius - thick)));
        Vector3d max =
                new Vector3d(radius).add(wheelCenter).add(direction.step().absolute().mul((float) (-radius + thick)));
        return Shapes.or(Shapes.box(min.x, min.y, min.z, max.x, max.y, max.z), Shapes.block());
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {
        return Shapes.empty();
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
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new SimpleWheelBlockEntity(blockPos, blockState);
    }
}
