package com.hainabaichuan75.iac_p.ecs.v2.api.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.dispatch.V2SystemDispatcher;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import java.util.List;

/**
 * 载具客户端 System——ECS 中的 <b>系统（System）</b>，20Hz 客户端逻辑 Tick。
 * <p>
 * 在客户端 {@code LevelTickEvent.Pre} 中由
 * {@link V2SystemDispatcher} 调度。
 * 适合：HUD 更新、调试覆盖层数据准备、客户端粒子/特效。
 * <p>
 * <b>注意</b>：此 System 仅在客户端运行，不能访问服务端数据。
 * 需要同步的数据应通过 Part 的组件同步获取。
 * <p>
 * <b>与 V1 的关系</b>：此接口操作 {@link Part}（v2），
 * 与操作 {@code ecs.part.Part} 的 {@code VehicleClientSystem} 并行存在，
 * 两者互不干扰。
 */
@FunctionalInterface
public interface ClientSystem {

    /**
     * 每客户端 tick 调用一次（约 20Hz，取决于客户端帧率）。
     *
     * @param subLevel 当前 SubLevel（客户端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 V2 Part，已过滤空列表
     */
    void onTick(ClientSubLevel subLevel, List<? extends Part> parts);
}
