package com.hainabaichuan75.iac_p.affiliation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 遗留 record —— 将在后续清理中移除。
 * <p>
 * 原有 {@code ComponentRegistry} 已被删除，改用 {@code PartQuery} 实时扫描。
 * 此 record 保留仅为避免大规模重构时的编译错误。
 */
@Deprecated
public record ComponentEntry(
        UUID subLevelUUID,
        BlockPos blockPos,
        ComponentRole role,
        @Nullable BlockEntity blockEntity
) {

    @Deprecated
    public static ComponentEntry readFromNbt(net.minecraft.nbt.CompoundTag tag) {
        return new ComponentEntry(null, BlockPos.ZERO, ComponentRole.COCKPIT, null);
    }

    @Deprecated
    public ComponentEntry withBlockEntity(@Nullable BlockEntity be) {
        return new ComponentEntry(subLevelUUID, blockPos, role, be);
    }
}
