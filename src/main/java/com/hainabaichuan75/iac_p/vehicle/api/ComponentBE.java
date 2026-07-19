package com.hainabaichuan75.iac_p.vehicle.api;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 载具方块实体基类。
 * <p>
 * 子类声明 {@link Component} 字段，构造时自动被 {@link ComponentContainer} 发现。
 * <pre>{@code
 * public class EngineBE extends ComponentBE {
 *     public final Engine engine = new Engine(this);
 *
 *     public EngineBE(BlockPos pos, BlockState state) {
 *         super(ModTypes.ENGINE.get(), pos, state);
 *     }
 * }
 * }</pre>
 */
public abstract class ComponentBE extends BlockEntity implements ComponentContainer {

    protected ComponentBE(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    public void serverTick(ServerLevel level, BlockPos pos,BlockState state) {
        for (Component component : vehicleComponents()) {
            component.serverTick(level,pos,state);
        }
    }

    public void clientTick(ClientLevel level, BlockPos pos, BlockState state) {
        for (Component component : vehicleComponents()) {
            component.clientTick(level,pos,state);
        }
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        for (Component component : vehicleComponents()) {
            component.sable$tick(subLevel);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        for (Component component : vehicleComponents()) {
            component.sable$physicsTick(subLevel, handle, timeStep);
        }
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
