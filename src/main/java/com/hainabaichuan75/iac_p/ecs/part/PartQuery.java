package com.hainabaichuan75.iac_p.ecs.part;

import com.hainabaichuan75.iac_p.ecs.system.VehicleClientSystem;
import com.hainabaichuan75.iac_p.ecs.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.system.VehicleSystemRegistry;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 部件查询工具——替代已删除的 {@code ComponentRegistry}。
 * <p>
 * 使用 Sable 内部的 {@code getBlockEntityActors()} 遍历（O(actors)），
 * 不再需要全量扫描 SubLevel chunk 或维护独立注册表。
 * <p>
 * <b>⚠️ 已废弃：只应在 {@link VehicleTickSystem}
 * / {@link VehiclePhysicsSystem}
 * / {@link VehicleClientSystem}
 * 的实现类内部调用。</b> BlockEntity 自身不应直接调用 PartQuery —
 * 查询应由 System 层统一完成，BE 保持被动数据持有者角色。
 * 替代方案：在 System 的 {@code onTick()} 中通过
 * {@link VehicleSystemRegistry#collectParts
 * VehicleSystemRegistry.collectParts()} 获取全部 Part 后做类型过滤。
 *
 * @deprecated 部件查询应由 System 层统一负责，非 System 代码请勿直接调用。
 */
@Deprecated
public final class PartQuery {

    private PartQuery() {}

    /**
     * 收集指定 SubLevel 中所有指定类型的 BlockEntity。
     *
     * @param subLevel 目标 SubLevel
     * @param type     目标类型
     * @return 符合条件的 BE 列表
     */
    public static <T extends BlockEntity> List<T> findParts(
            SubLevel subLevel, Class<T> type) {
        return findParts(subLevel, type, be -> true);
    }

    /**
     * 收集指定 SubLevel 中所有指定类型的 BlockEntity（兼容旧签名）。
     *
     * @param level    未使用，仅为兼容旧调用方保留
     * @param subLevel 目标 SubLevel
     * @param type     目标类型
     * @return 符合条件的 BE 列表
     * @deprecated 使用 {@link #findParts(SubLevel, Class)} 替代
     */
    @Deprecated
    public static <T extends BlockEntity> List<T> findParts(
            Level level, SubLevel subLevel, Class<T> type) {
        return findParts(subLevel, type, be -> true);
    }

    /**
     * 收集指定 SubLevel 中符合类型和额外条件的 BlockEntity。
     *
     * @param subLevel    目标 SubLevel
     * @param type        目标类型
     * @param extraFilter 额外过滤条件
     * @return 符合条件的 BE 列表
     */
    public static <T extends BlockEntity> List<T> findParts(
            SubLevel subLevel, Class<T> type, Predicate<T> extraFilter) {
        List<T> result = new ArrayList<>();
        if (subLevel == null || subLevel.getPlot() == null) return result;

        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (type.isInstance(actor)) {
                T typed = type.cast(actor);
                if (extraFilter.test(typed)) {
                    result.add(typed);
                }
            }
        }
        return result;
    }

    /**
     * 收集指定 SubLevel 中符合类型和额外条件的 BlockEntity（兼容旧签名）。
     *
     * @param level      未使用，仅为兼容旧调用方保留
     * @param subLevel   目标 SubLevel
     * @param type       目标类型
     * @param extraFilter 额外过滤条件
     * @return 符合条件的 BE 列表
     * @deprecated 使用 {@link #findParts(SubLevel, Class, Predicate)} 替代
     */
    @Deprecated
    public static <T extends BlockEntity> List<T> findParts(
            Level level, SubLevel subLevel, Class<T> type, Predicate<T> extraFilter) {
        return findParts(subLevel, type, extraFilter);
    }

    /**
     * 按 SubLevel UUID 查询。
     * 遍历容器中所有 SubLevel 进行全量匹配（O(sublevels)），不频繁调用时可接受。
     *
     * @param level   目标 Level
     * @param subUUID 目标 SubLevel 的 UUID
     * @param type    目标类型
     * @return 符合条件的 BE 列表，未找到则返回空列表
     */
    public static <T extends BlockEntity> List<T> findPartsByUUID(
            Level level, UUID subUUID, Class<T> type) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return List.of();

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.getUniqueId().equals(subUUID)) {
                return findParts(sl, type);
            }
        }
        return List.of();
    }
}
