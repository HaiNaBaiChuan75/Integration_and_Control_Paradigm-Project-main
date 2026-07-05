package com.hainabaichuan75.iac_p.part;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * 玩家指令接收器 —— 网络包写入驾驶意图的契约接口。
 * <p>
 * 与 {@link Controller} 互补——Controller 定义读取侧（getter），
 * 本接口定义写入侧（setter）。
 * <p>
 * 网络处理程序（如 VehicleControlC2SPacket）收到客户端输入后，
 * 通过此接口写入座舱，座舱的 Controller getter 再回传数据给 ECS 系统。
 *
 * @see Controller
 * @see com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity
 */
public interface PlayerCommandReceiver extends Part {

    /**
     * 设置移动意图方向向量。
     * <p>
     * 与 {@link Controller#getMovementIntent()} 同构：
     * {@code z = 前后(±1), x = 左右(±1)}。
     *
     * @param intent 移动意图向量，{@code null} 等效于零向量
     */
    void setMovementIntent(@NotNull Vector3dc intent);

    /**
     * 设置刹车状态。
     */
    void setBrake(boolean brake);

    /**
     * 设置开火状态。
     */
    void setFiring(boolean firing);

    /**
     * 设置瞄准目标的世界坐标。
     *
     * @param worldPos 瞄准点世界坐标，{@code null} 表示无目标
     */
    void setAimTarget(@Nullable Vector3dc worldPos);
}
