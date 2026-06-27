package com.hainabaichuan75.iac_p.content.blocks.test_controller;

import com.hainabaichuan75.iac_p.index.ModTestControllerBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * TestControllerBlock —— GeckoLib 多方块渲染测试用。
 * <p>
 * 使用 RenderShape.ENTITYBLOCK_ANIMATED 让 GeckoLib 接管渲染，
 * 单个方块渲染覆盖 2×2×2 区域的视觉模型，验证 SubLevel 内 GeckoLib 渲染兼容性。
 */
public class TestControllerBlock extends Block implements EntityBlock {

    public TestControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestControllerBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }
}
