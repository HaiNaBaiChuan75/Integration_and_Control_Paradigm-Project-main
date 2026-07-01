package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.List;

/**
 * 载具客户端 Tick System — 20Hz 客户端逻辑。
 * <p>
 * 在客户端 {@code LevelTickEvent.Pre} 中通过 {@link VehicleSystemDispatcher} 调度。
 * 适合：HUD 更新、调试覆盖层数据准备、客户端粒子/特效。
 */
@FunctionalInterface
public interface VehicleClientSystem {

    /**
     * 每客户端 tick 调用一次。
     *
     * @param subLevel 当前 SubLevel（客户端）
     * @param parts    该 SubLevel 内收集到的所有 Part
     */
    void onTick(ClientSubLevel subLevel, List<PartBlockEntity> parts);
}
