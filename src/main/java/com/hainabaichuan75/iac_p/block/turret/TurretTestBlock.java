package com.hainabaichuan75.iac_p.block.turret;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * TurretTestBlock —— 炮塔测试方块。
 * <p>
 * 使用 RenderShape.ENTITYBLOCK_ANIMATED 让 GeckoLib 接管渲染，
 * 用于验证 GeckoLib 炮塔模型 + 动态 bone 旋转（yaw/pitch）在 SubLevel 内的效果。
 * <p>
 * 对应 Crossout 第5章「运动学武器装饰器」——武器作为纯视觉+碰撞元素，
 * 不创建额外 SubLevel，不产生物理反力。
 * <p>
 * <b>交互</b>：
 * <ul>
 * <li>空手右键：切换自动旋转</li>
 * <li>潜行 + 空手右键：重置角度为 0</li>
 * </ul>
 */
public class TurretTestBlock extends Block implements EntityBlock {

    /**
     * 是否开启自动旋转。使用 BlockState 属性自动同步到客户端。
     */
    public static final BooleanProperty AUTO_ROTATE = BooleanProperty.create("auto_rotate");

    public TurretTestBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AUTO_ROTATE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AUTO_ROTATE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretTestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 双端 tick：驱动自动旋转（通过 BlockState 同步状态）
        return (lvl, pos, st, be) -> ((TurretTestBlockEntity) be).tick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TurretTestBlockEntity be) {
            if (player.isShiftKeyDown()) {
                // 潜行 + 右键：重置角度并关闭自动旋转
                level.setBlockAndUpdate(pos, state.setValue(AUTO_ROTATE, false));
                be.resetAngles();
            } else {
                // 右键：切换自动旋转（BlockState 自动同步到客户端）
                level.setBlockAndUpdate(pos, state.cycle(AUTO_ROTATE));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }
}
