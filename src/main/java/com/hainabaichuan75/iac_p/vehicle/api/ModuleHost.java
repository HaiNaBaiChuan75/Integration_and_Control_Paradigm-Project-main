package com.hainabaichuan75.iac_p.vehicle.api;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BE 实现此接口即获得组件自动发现与生命周期转发。
 * <p>
 * 需要 NBT 持久化时，在 {@code saveAdditional/loadAdditional} 中调用
 * {@link #saveComponents} / {@link #loadComponents}。
 */
public interface ModuleHost extends BlockEntitySubLevelActor {

    default List<Module> vehicleComponents() {
        return ModuleCollector.collect(this);
    }

    default void saveComponents(CompoundTag tag) {
        for (var c : vehicleComponents()) {
            var ct = new CompoundTag();
            c.save(ct);
            tag.put(c.componentName(), ct);
        }
    }

    default void loadComponents(CompoundTag tag) {
        for (var c : vehicleComponents()) {
            var ct = tag.getCompound(c.componentName());
            if (!ct.isEmpty()) c.load(ct);
        }
    }

    default void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        for (Module component : vehicleComponents()) {
            component.serverTick(level, pos, state);
        }
    }

    default void clientTick(ClientLevel level, BlockPos pos, BlockState state) {
        for (Module component : vehicleComponents()) {
            component.clientTick(level, pos, state);
        }
    }

    @Override
    default void sable$tick(ServerSubLevel subLevel) {
        for (Module component : vehicleComponents()) {
            component.sable$tick(subLevel);
        }
    }

    @Override
    default void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        for (Module component : vehicleComponents()) {
            component.sable$physicsTick(subLevel, handle, timeStep);
        }
    }
}
