package com.hainabaichuan75.iac_p.content.blocks.shotgun;

import com.hainabaichuan75.iac_p.IACP;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * ShotgunBaseBlock —— 霰弹枪底座方块（地毯状）。
 * <p>
 * 放置时自动召唤砂轮 SubLevel（水平旋转/方向机）和避雷针 SubLevel（俯仰/高低机）。
 * 右键（空手）可切换拆卸/重新装配。形状类似地毯（1/16 格高），玩家可以站在上面。
 */
public class ShotgunBaseBlock extends Block implements EntityBlock {

    /**
     * 地毯形状：1/16 格高
     */
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0);

    public ShotgunBaseBlock(Properties properties) {
        super(properties);
    }

    /**
     * 放置时自动召唤武器（砂轮 + 避雷针 SubLevel）。 玩家放置底座后立即装配，无需额外右键操作。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof ShotgunBaseBlockEntity be) {
            if (!be.isAssembled()) {
                IACP.LOGGER.info("[ShotgunBaseBlock] setPlacedBy: 自动装配 @ {}", pos);
                be.assemble();
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) return InteractionResult.FAIL;
        IACP.LOGGER.info("[ShotgunBaseBlock] useWithoutItem @ {} client={} player={}",
                pos, level.isClientSide, player.getName().getString());
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ShotgunBaseBlockEntity be) {
            IACP.LOGGER.info("[ShotgunBaseBlock] 回调 BE：assembled={}", be.isAssembled());
            if (be.isAssembled()) be.disassemble();
            else be.assemble();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.hasBlockEntity() && state.getBlock() != newState.getBlock()) {
            IACP.LOGGER.info("[ShotgunBaseBlock] onRemove @ {} newState={}", pos, newState);
            if (level.getBlockEntity(pos) instanceof ShotgunBaseBlockEntity shotgunBE) {
                IACP.LOGGER.info("[ShotgunBaseBlock] onRemove: 调用 disassemble, assembled={}", shotgunBE.isAssembled());
                shotgunBE.disassemble();
            } else {
                IACP.LOGGER.warn("[ShotgunBaseBlock] onRemove: BE 为空或类型不匹配: {}", level.getBlockEntity(pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ====== BlockEntity ======

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShotgunBaseBlockEntity(pos, state);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (l, p, s, be) -> {
            if (be instanceof ShotgunBaseBlockEntity sg) {
                sg.tick();
            }
        };
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(ShotgunBaseBlock::new);
    }
}
