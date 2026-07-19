package com.hainabaichuan75.iac_p.vehicle.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对应 {@link ModuleHostBE} 的 Block 基类。
 * <p>
 * 子类只需实现 {@link #createBlockEntity} 返回对应的 BE 实例。
 * 使用 {@link BlockBehaviour.Properties} 构造，兼容 {@code DeferredRegister.Blocks} 的 {@code ::new} 引用。
 */
public abstract class ModuleHostBlock extends Block implements EntityBlock {

    public ModuleHostBlock(@NotNull BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return createBlockEntity(pos, state);
    }

    protected abstract @NotNull ModuleHostBE createBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state);
}
