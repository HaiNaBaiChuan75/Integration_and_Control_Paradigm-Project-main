package com.hainabaichuan75.iac_p.block.suspension_test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SuspensionTestBlock extends Block implements EntityBlock {

    public static final DirectionProperty HORIZONTAL_FACING = HorizontalDirectionalBlock.FACING;

    public SuspensionTestBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Override @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.block();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.hasBlockEntity() && state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof SuspensionTestBlockEntity be) {
                ItemStack held = be.getHeldItem();
                if (!held.isEmpty()) {
                    Direction f = state.getValue(HORIZONTAL_FACING);
                    BlockPos dp = f != null ? pos.relative(f) : pos;
                    Containers.dropItemStack(level, dp.getX(), dp.getY(), dp.getZ(), held);
                }
            }
            level.removeBlockEntity(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        // 仅悬挂面（facing 方向）允许轮子交互
        Direction facing = state.getValue(HORIZONTAL_FACING);
        if (hit.getDirection() != facing) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof SuspensionTestBlockEntity be) {
            ItemStack current = be.getHeldItem();
            boolean tireInHand = held.has(dev.ryanhcode.offroad.index.OffroadDataComponents.TIRE);
            boolean tireInBE = current.has(dev.ryanhcode.offroad.index.OffroadDataComponents.TIRE);

            if (held.isEmpty() && tireInBE) {
                be.setHeldItem(ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(current);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.75f,
                        0.8f + level.random.nextFloat() * 0.4f);
                return ItemInteractionResult.CONSUME;
            } else if (tireInHand) {
                ItemStack insert = held.copyWithCount(1);
                be.setHeldItem(insert);
                if (!player.hasInfiniteMaterials()) held.shrink(1);
                if (tireInBE) player.getInventory().placeItemBackInInventory(current);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.75f,
                        0.8f + level.random.nextFloat() * 0.4f);
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ====== BlockEntity ======

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuspensionTestBlockEntity(pos, state);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (l, p, s, be) -> {
            if (be instanceof SuspensionTestBlockEntity suspension) {
                suspension.tick();
            }
        };
    }
}
