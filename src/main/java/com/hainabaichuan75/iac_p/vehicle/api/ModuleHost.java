package com.hainabaichuan75.iac_p.vehicle.api;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * BE 实现此接口即获得 Module 自动发现与 NBT 持久化。
 * <p>
 * 需要 NBT 持久化时，在 {@code saveAdditional/loadAdditional} 中调用
 * {@link #saveComponents} / {@link #loadComponents}。
 * <p>
 * Module 的 tick 由 BE 自行编排——接口不提供默认转发。
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
}
