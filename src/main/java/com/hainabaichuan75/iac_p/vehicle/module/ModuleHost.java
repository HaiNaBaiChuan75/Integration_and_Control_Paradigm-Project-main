package com.hainabaichuan75.iac_p.vehicle.module;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * BE 实现此接口即可获得 Module 自动发现与 NBT 持久化。
 * <p>
 * 需要 NBT 持久化时，在 {@code saveAdditional/loadAdditional} 中调用
 * {@link #saveComponents} / {@link #loadComponents}。
 */
public interface ModuleHost extends BlockEntitySubLevelActor {

    List<Module> modules();

    default void saveComponents(CompoundTag tag) {
        for (var module : modules()) {
            var ct = new CompoundTag();
            module.save(ct);
            tag.put(module.componentName(), ct);
        }
    }

    default void loadComponents(CompoundTag tag) {
        for (var module : modules()) {
            var ct = tag.getCompound(module.componentName());
            if (!ct.isEmpty()) module.load(ct);
        }
    }
}
