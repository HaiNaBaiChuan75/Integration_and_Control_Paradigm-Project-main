package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;

/**
 * 载具逻辑 Tick System — 20Hz 服务端逻辑。
 * <p>
 * 在服务端 {@code ServerTickEvent.Post} 中通过 {@link VehicleSystemDispatcher} 调度。
 * 适合：瞄准目标计算、变速箱换挡逻辑、控制输入处理、状态更新与同步。
 * <p>
 * 所有注册的 System 会被 {@link VehicleSystemRegistry} 管理，
 * 每 tick 对每个 SubLevel 依次调用。
 */
@FunctionalInterface
public interface VehicleTickSystem {

    /**
     * 每服务端逻辑 tick 调用一次。
     *
     * @param subLevel 当前 SubLevel（服务端）
     * @param parts    该 SubLevel 内收集到的所有 Part
     */
    void onTick(ServerSubLevel subLevel, List<PartBlockEntity> parts);
}
