package com.hainabaichuan75.iac_p.ecs.system;

import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;

/**
 * 载具逻辑 System——20Hz 服务端逻辑 Tick。
 * <p>
 * 在服务端 {@code ServerTickEvent.Post} 中由
 * {@link com.hainabaichuan75.iac_p.ecs.dispatch.VehicleSystemDispatcher VehicleSystemDispatcher} 调度。
 * 适合：瞄准目标计算、变速箱换挡逻辑、控制输入处理、状态更新与同步。
 * <p>
 * 所有注册的 System 由 {@link VehicleSystemRegistry} 管理，
 * 每 tick 对每个 SubLevel 依次调用。
 * <p>
 * <b>约束</b>：System 必须保持无状态——运行时数据保留在 Part 或 SubLevel 上，
 * 不在 System 内部持有可变字段。
 */
@FunctionalInterface
public interface VehicleTickSystem {

    /**
     * 每服务端逻辑 tick 调用一次。
     *
     * @param subLevel 当前 SubLevel（服务端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 Part，已过滤空列表
     */
    void onTick(ServerSubLevel subLevel, List<PartBlockEntity> parts);
}
