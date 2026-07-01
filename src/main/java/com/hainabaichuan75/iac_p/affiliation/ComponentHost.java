package com.hainabaichuan75.iac_p.affiliation;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 遗留接口 —— 将在后续清理中移除。
 * <p>
 * 原有 {@code ComponentRegistry} 已被删除，{@code ComponentHost} 不再需要。
 * 新代码应继承 {@code com.hainabaichuan75.iac_p.core.part.PartBlockEntity}。
 * <p>
 * 此接口保留仅为避免大规模重构时的编译错误。
 */
@Deprecated
public interface ComponentHost {

    /**
     * 返回默认角色。具体 BE 类不再需要重写此方法。
     */
    @Deprecated
    default ComponentRole getComponentRole() {
        return ComponentRole.COCKPIT;
    }

    @Deprecated
    static void registerComponent(BlockEntity be, ComponentRole role) {
        // 空操作 — ComponentRegistry 已删除
    }

    @Deprecated
    static void unregisterComponent(BlockEntity be) {
        // 空操作 — ComponentRegistry 已删除
    }
}
