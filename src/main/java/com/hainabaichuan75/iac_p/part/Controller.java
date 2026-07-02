package com.hainabaichuan75.iac_p.part;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * 控制器 —— 操作者的驾驶意图接口。
 * <p>
 * 任何可以作为载具输入源的 Part（驾驶舱、遥控终端、AI 核心）都应实现此接口。
 * System 通过 {@code instanceof Controller} 查找主控输入源。
 * <p>
 * <b>纯数据约束</b>：只暴露状态 getter，不暴露计算方法。
 * 各输入由外部（网络包）写入 Part 字段，System 只读。
 */
public interface Controller extends Part {

    /**
     * 驾驶意图方向向量（SubLevel 局部空间）。
     * <p>
     * 零向量 = 无驾驶输入。向量长度表示输入强度（最大 1.0）。
     * <p>
     * <pre>
     * z- = 前，z+ = 后
     * x+ = 右，x- = 左
     * </pre>
     */
    @NotNull Vector3dc getMovementIntent();

    /**
     * @return 刹车是否踩下
     */
    boolean isBraking();

    /**
     * @return 是否正在开火
     */
    boolean isFiring();

    /**
     * @return 瞄准目标点的世界坐标（供武器瞄准 System 使用），
     *         {@code null} 表示无瞄准目标
     */
    @Nullable Vector3dc getAimTarget();
}
