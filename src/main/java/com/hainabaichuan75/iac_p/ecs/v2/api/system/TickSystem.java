package com.hainabaichuan75.iac_p.ecs.v2.api.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.dispatch.V2SystemDispatcher;
import com.hainabaichuan75.iac_p.ecs.v2.api.dispatch.V2SystemRegistry;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

/**
 * 载具逻辑 System——ECS 中的 <b>系统（System）</b>，20Hz 服务端逻辑 Tick。
 * <p>
 * 在服务端 {@code ServerTickEvent.Pre} 中由
 * {@link V2SystemDispatcher} 调度。
 * 适合：瞄准目标计算、变速箱换挡逻辑、控制输入处理、状态更新与同步。
 * <p>
 * 所有注册的 System 由 {@link V2SystemRegistry} 管理，
 * 每 tick 对每个 SubLevel 依次调用。
 * <p>
 * <b>约束</b>：System 必须保持无状态——运行时数据保留在 Part 或 SubLevel 上，
 * 不在 System 内部持有可变字段。
 * <p>
 * <b>与 V1 的关系</b>：此接口操作 {@link Part}（v2），
 * 与操作 {@code ecs.part.Part} 的 {@code VehicleTickSystem} 并行存在，
 * 两者互不干扰。
 */
@FunctionalInterface
public interface TickSystem {

    /**
     * 每服务端逻辑 tick 调用一次。
     *
     * @param subLevel 当前 SubLevel（服务端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 V2 Part，已过滤空列表
     */
    void onTick(ServerSubLevel subLevel, List<? extends Part> parts);
}
