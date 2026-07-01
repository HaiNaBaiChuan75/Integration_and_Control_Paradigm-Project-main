package com.hainabaichuan75.iac_p.core.part;

import com.hainabaichuan75.iac_p.core.util.SubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 部件查询工具——替代已删除的 {@code ComponentRegistry}。
 * <p>
 * 通过扫描 SubLevel 的已加载 chunk 来查找特定类型的部件，
 * 不再需要单独的注册表维护。
 */
public final class PartQuery {

    private PartQuery() {}

    /**
     * 收集指定 SubLevel 中所有符合条件（Predicate）的 BlockEntity。
     *
     * @param level     世界
     * @param subLevel  目标 SubLevel
     * @param filter    过滤条件，返回 true 的 BE 被收集
     * @return 符合条件的 BE 列表
     */
    public static <T extends BlockEntity> List<T> findParts(
            Level level, SubLevel subLevel, Class<T> type) {
        return findParts(level, subLevel, type, be -> true);
    }

    /**
     * 收集指定 SubLevel 中符合类型和额外条件的 BlockEntity。
     *
     * @param level     世界
     * @param subLevel  目标 SubLevel
     * @param type      目标类型
     * @param extraFilter 额外过滤条件
     * @return 符合条件的 BE 列表
     */
    public static <T extends BlockEntity> List<T> findParts(
            Level level, SubLevel subLevel, Class<T> type, Predicate<T> extraFilter) {
        List<T> result = new ArrayList<>();
        if (subLevel == null || subLevel.getPlot() == null) return result;

        ObjectArrayList<BlockPos> allBlocks = SubLevelUtil.collectBlocks(level, subLevel);
        for (BlockPos pos : allBlocks) {
            BlockEntity be = level.getBlockEntity(pos);
            if (type.isInstance(be)) {
                T typed = type.cast(be);
                if (extraFilter.test(typed)) {
                    result.add(typed);
                }
            }
        }
        return result;
    }

    /**
     * 按 SubLevel UUID 查询。
     * 通过所有容器的全量扫描定位 SubLevel（较慢，不频繁调用）
     */
    public static <T extends BlockEntity> List<T> findPartsByUUID(
            Level level, UUID subUUID, Class<T> type) {
        // 通过所有容器的全量扫描定位 SubLevel
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return List.of();

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.getUniqueId().equals(subUUID)) {
                return findParts(level, sl, type);
            }
        }
        return List.of();
    }
}
