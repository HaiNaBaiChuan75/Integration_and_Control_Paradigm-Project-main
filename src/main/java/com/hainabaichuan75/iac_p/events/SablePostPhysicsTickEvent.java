package com.hainabaichuan75.iac_p.events;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 监听 Sable 物理 tick 结束事件 ({@link ForgeSablePostPhysicsTickEvent})。
 * <p>
 * 当前为空壳——骑乘玩家位置同步已移除（玩家不再被固定在驾驶舱位置）。
 * 保留事件注册以防其他系统后续需要在此钩子挂载延迟敏感逻辑。
 */
public class SablePostPhysicsTickEvent {

    /**
     * Sable 物理 tick 结束后调用（频率 ~100Hz）。
     * <p>
     * 目前为空操作——玩家上车后保留在世界原位，不随 SubLevel 移动。
     */
    @SubscribeEvent
    public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        // 当前无操作。骑乘玩家不再被同步到 SubLevel 驾驶舱位置，
        // 玩家实体留在上车时的世界位置。
    }
}
