package com.hainabaichuan75.iac_p.part;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * 控制器 —— 提供玩家输入状态的纯数据接口。
 * <p>
 * 任何可以作为载具输入源的 Part（驾驶舱、遥控终端、AI 核心）都应实现此接口。
 * System 通过 {@code instanceof Controller} 查找主控输入源。
 * <p>
 * <b>纯数据约束</b>：只暴露状态 getter，不暴露计算方法。
 * 油门/转向/瞄准目标均由外部（网络包）写入 Part 字段，System 只读。
 */
public interface Controller {

    /** @return 是否有前进油门输入（W 键） */
    boolean isThrottleForward();

    /** @return 是否有后退油门输入（S 键） */
    boolean isThrottleBackward();

    /** @return 刹车踏板是否踩下 */
    boolean isBraking();

    /** @return 目标转向角（弧度），正值=左转 */
    double getTargetSteeringYaw();

    /**
     * @return 瞄准目标点的世界坐标（供武器瞄准 System 使用），
     *         null 表示无瞄准目标
     */
    @Nullable Vector3dc getAimTarget();

    /** @return 原始油门方向：+1=前进, -1=后退, 0=无输入 */
    int getRawThrottleDirection();
}
