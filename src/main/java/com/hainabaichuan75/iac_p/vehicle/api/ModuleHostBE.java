package com.hainabaichuan75.iac_p.vehicle.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 载具方块实体基类（可选便利抽象）。
 * <p>
 * 子类声明 {@link Module} 字段即自动获得 NBT 持久化。
 * 不继承此类时实现 {@link ModuleHost} 并在 save/load 中手动调用
 * {@link ModuleHost#saveComponents saveComponents} /
 * {@link ModuleHost#loadComponents loadComponents} 亦可。
 */
public abstract class ModuleHostBE extends BlockEntity implements ModuleHost {

    protected ModuleHostBE(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveComponents(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadComponents(tag);
    }
}
