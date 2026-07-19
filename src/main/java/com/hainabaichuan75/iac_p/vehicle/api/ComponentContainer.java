package com.hainabaichuan75.iac_p.vehicle.api;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * BE 实现此接口即获得组件自动发现与 NBT 序列化。
 * <p>
 * <pre>{@code
 * public class EngineBE extends ComponentBE {
 *     public final Engine engine = new Engine(this);
 *     public EngineBE(BlockPos pos, BlockState state) {
 *         super(ModTypes.ENGINE.get(), pos, state);
 *     }
 * }
 * }</pre>
 */
public interface ComponentContainer extends BlockEntitySubLevelActor {

    default List<Component> vehicleComponents() {
        return ComponentCollector.collect(this);
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
