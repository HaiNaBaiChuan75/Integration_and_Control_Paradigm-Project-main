package com.hainabaichuan75.iac_p.vehicle.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * BE 内部模块 —— 可独立 save/load 的数据与领域方法。
 */
public abstract class Module{

    public final @NotNull BlockEntity be;

    protected Module(@NotNull BlockEntity be) { this.be = be; }

    public abstract String componentName();
    public abstract void save(CompoundTag tag);
    public abstract void load(CompoundTag tag);

    public final void setChanged() { be.setChanged(); }
    public final void sync() {
        var level = be.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        }
    }
}
