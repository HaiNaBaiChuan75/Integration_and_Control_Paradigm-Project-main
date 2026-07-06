package com.hainabaichuan75.iac_p.block.test_blank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 测试用空白方块 —— 不携带任何组件，可通过 NBT 手动添加。
 * <p>
 * 用于测试场景：手动改 NBT 添加 {@code vehicle_parts}，验证 ComponentKey fallback
 * 机制、多组件组合等。放置后无默认组件，BE 组件 map 为空。
 */
public class TestBlankBlock extends Block implements EntityBlock {

    public TestBlankBlock(@NotNull BlockBehaviour.Properties properties) {
        super(properties);
    }

    // ====== BlockEntity ======

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TestBlankBlockEntity(pos, state);
    }
}
