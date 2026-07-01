package com.hainabaichuan75.iac_p.affiliation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 遗留类 —— 将在后续清理中移除。
 * <p>
 * 已被删除。此类保留仅为避免大规模重构时的编译错误。
 */
@Deprecated
public final class DeferredRegistration {

    private DeferredRegistration() {}

    @Deprecated
    public static void add(BlockPos pos, ComponentRole role) {}

    @Deprecated
    public static void tick(BlockEntity be) {}

    @Deprecated
    public static void clearAll() {}
}
