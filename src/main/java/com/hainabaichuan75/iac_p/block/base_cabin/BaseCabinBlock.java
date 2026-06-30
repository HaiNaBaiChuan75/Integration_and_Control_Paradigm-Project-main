package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * BaseCabinBlock —— 基础座舱方块（GeckoLib 渲染的单格驾驶舱）。
 * <p>
 * 功能上与 {@link com.hainabaichuan75.iac_p.content.blocks.cockpit.CockpitBlock} 等效，
 * 但仅占一格高度，使用 GeckoLib 骨骼动画模型渲染。
 * 视觉上作为"通用驾驶舱 2.0"，提供同样的载具驾驶功能。
 */
public class BaseCabinBlock extends Block implements IBE<BaseCabinBlockEntity> {

    // 完整立方体形状（可被自定义模型覆盖）
    private static final VoxelShape SHAPE = Shapes.block();

    public BaseCabinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ====== BlockEntity ======

    @Override
    public Class<BaseCabinBlockEntity> getBlockEntityClass() {
        return BaseCabinBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BaseCabinBlockEntity> getBlockEntityType() {
        return ModCockpitBlockEntityTypes.BASE_CABIN.get();
    }
}
