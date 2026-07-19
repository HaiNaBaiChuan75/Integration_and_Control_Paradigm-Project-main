package com.hainabaichuan75.iac_p.vehicle.api;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 载具组件抽象 —— 数据 + 行为 + 自定义 NBT 序列化 + BE 关联 + 生命周期。
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

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {}
    public void clientTick(ClientLevel level, BlockPos pos, BlockState state) {}
    public void sable$tick(final ServerSubLevel subLevel) {}
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {}

}
