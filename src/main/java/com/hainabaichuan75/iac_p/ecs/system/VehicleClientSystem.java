package com.hainabaichuan75.iac_p.ecs.system;

import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.List;

/**
 * 载具客户端 System——20Hz 客户端逻辑 Tick。
 * <p>
 * 在客户端 {@code LevelTickEvent.Pre} 中由
 * {@link com.hainabaichuan75.iac_p.ecs.dispatch.VehicleSystemDispatcher VehicleSystemDispatcher} 调度。
 * 适合：HUD 更新、调试覆盖层数据准备、客户端粒子/特效。
 * <p>
 * <b>注意</b>：此 System 仅在客户端运行，不能访问服务端数据。
 * 需要同步的数据应通过 Part 的同步字段获取。
 */
@FunctionalInterface
public interface VehicleClientSystem {

    /**
     * 每客户端 tick 调用一次（约 20Hz，取决于客户端帧率）。
     *
     * @param subLevel 当前 SubLevel（客户端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 Part，已过滤空列表
     */
    void onTick(ClientSubLevel subLevel, List<PartBlockEntity> parts);
}
