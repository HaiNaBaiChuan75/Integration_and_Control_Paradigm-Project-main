package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.events.ServerMountHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * BaseCabinBlock —— 基础座舱方块（GeckoLib 渲染的单格驾驶舱）。
 * <p>
 * 功能上与 {@link com.hainabaichuan75.iac_p.block.cockpit.CockpitBlock} 等效，
 * 但仅占一格高度，使用 GeckoLib 骨骼动画模型渲染。
 * 视觉上作为"通用驾驶舱 2.0"，提供同样的载具驾驶功能。
 */
public class BaseCabinBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 完整立方体形状（可被自定义模型覆盖）
    private static final VoxelShape SHAPE = Shapes.block();

    public BaseCabinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    // ====== 空手右键 → 上车回调 ======

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ServerMountHandler.handleMountDismount(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    // ====== BlockEntity ======

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BaseCabinBlockEntity(pos, state);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (l, p, s, be) -> {
            if (be instanceof CockpitBlockEntity cockpit) {
                cockpit.tick();
            }
        };
    }
}
