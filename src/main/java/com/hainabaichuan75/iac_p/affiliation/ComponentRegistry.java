package com.hainabaichuan75.iac_p.affiliation;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 遗留类 —— 将在后续清理中移除。
 * <p>
 * 已被删除，改用 {@code com.hainabaichuan75.iac_p.core.part.PartQuery} 实时扫描。
 * 此类保留仅为避免大规模重构时的编译错误。
 */
@Deprecated
public final class ComponentRegistry {

    private ComponentRegistry() {}

    @Deprecated
    public static List<ComponentEntry> getComponents(UUID subUUID, @Nullable ComponentRole role) {
        return Collections.emptyList();
    }

    @Deprecated
    public static void onWorldLoad() {
    }
}
