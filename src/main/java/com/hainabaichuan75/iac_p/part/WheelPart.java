package com.hainabaichuan75.iac_p.part;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.part.Part;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 轮子基接口 —— 所有轮子共有的几何、悬挂与运动学数据。
 * <p>
 * <b>设计决策：不由 {@code SuspensionTestBlockEntity} 直接实现</b>。
 * 本接口作为抽象数据规约存在，将"轮子"的概念契约与具体 BE 实现解耦。
 * <p>
 * 属性分组：
 * <ul>
 *   <li><b>轴向法线</b> — 旋转轴方向，各 System 据此推导牵引方向与侧向</li>
 *   <li><b>轮速</b> — 当前实际轮端 RPM，物理 System 写，动力/转向 System 读</li>
 *   <li><b>悬挂</b> — 刚度系数（硬件属性）、压缩量（运行时状态）和挂点偏移（几何属性）</li>
 *   <li><b>半径</b> — 轮胎几何尺寸，用于力臂和转速换算</li>
 * </ul>
 *
 * @see SteeringWheel
 * @see DriveWheel
 */
public interface WheelPart extends Part {

    // ==================================================================
    //  悬挂挂点偏移（几何属性，方块坐标系）
    // ==================================================================

    /**
     * 默认的零偏移常量（方块中心）。
     */
    Vector3dc SUSPENSION_ATTACHMENT_CENTER = new Vector3d();

    /**
     * 轮毂（悬挂挂点）相对于方块几何中心的偏移量。
     * <p>
     * 偏移量在方块的 <b>SubLevel 局部坐标系</b>中表达：
     * <ul>
     *   <li><b>Y 分量</b>（纵向）— 轮毂在方块中的上下位置。
     *       例如 {@code y = -0.4} 表示轮毂在块中心下方 0.4 米（近底部），
     *       这决定了射线检测地面时的起始高度。</li>
     *   <li><b>沿轴向的分量</b>（轴线方向）— 轮子在其旋转轴线上偏离方块中心的位置。
     *       轴向由 {@link #getAxialNormal()} 定义。</li>
     * </ul>
     * <p>
     * 默认值为 {@code (0, 0, 0)}（方块中心）。若轮子的实际几何中心
     * 不在方块中心（如轮子模型位于方块底部），实现者应重写此方法。
     *
     * @return 偏移向量（SubLevel 局部空间），默认为零向量
     */
    default Vector3dc getSuspensionAttachmentOffset() {
        return SUSPENSION_ATTACHMENT_CENTER;
    }

    /**
     * 轮毂（悬挂挂点）在世界坐标中的位置。
     * <p>
     * 将 {@link #getSuspensionAttachmentOffset()} 从 SubLevel 局部空间变换到世界坐标：
     * <pre>
     * hubWorld = subLevelPose * (blockCenter_Local + offset)
     * </pre>
     * 与直接使用 {@code getCenterInWorld() + offset} 不同，本方法在 SubLevel
     * 有旋转时也能得到正确结果。
     * <p>
     * 此方法由 {@code SuspensionSystem} 调用以确定射线检测的起点。
     *
     * @return 轮毂世界坐标（不变向量）
     */
    @NotNull
    default Vector3dc getSuspensionAttachmentInWorld() {
        // 方块中心在 SubLevel 局部空间
        Vector3d local = JOMLConversion.atCenterOf(getBlockEntity().getBlockPos());
        // 加上挂点偏移
        Vector3dc offset = getSuspensionAttachmentOffset();
        local.add(offset.x(), offset.y(), offset.z());

        // 通过 SubLevel 姿态变换到世界坐标
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(local, new Vector3d());
        }
        IACP.LOGGER.warn("WheelPart {} has no SubLevel, using local coordinates as world", this);
        return local;
    }

    // ==================================================================
    //  轴向法线
    // ==================================================================

    /**
     * 车轮旋转轴的方向向量（SubLevel 局部空间，单位向量）。
     * <p>
     * 轴向法线定义了车轮绕之旋转的轴线方向。物理 System 据此推导：
     * <ul>
     *   <li>牵引方向（垂直于轴向法线）</li>
     *   <li>侧向（平行于轴向法线）</li>
     * </ul>
     * <p>
     * 默认值为 {@code (0, 0, -1)}（指向 Z-，即载具前方）。
     * 若轮子的安装朝向与此不同，实现者应重写此方法。
     *
     * @return 单位向量，表示车轮轴向方向
     */
    default Vector3dc getAxialNormal() {
        return Part.FORWARD;
    }

    // ==================================================================
    //  轮速
    // ==================================================================

    /**
     * @return 当前实际轮端 RPM，正值 = 前进方向旋转
     */
    double getRpm();

    /**
     * 设置当前实际轮端 RPM。
     * <p>
     * 由 {@code SuspensionPhysicsSystem} 在每个物理 tick 结束时
     * 从轮胎线速度推算并写入。
     *
     * @param rpm 轮端转速
     */
    void setRpm(double rpm);

    // ==================================================================
    //  悬挂
    // ==================================================================

    /**
     * @return 悬挂刚度系数（N/m），悬挂的硬件属性，物理 System 读用于弹力计算
     */
    double getSuspensionStiffness();

    /**
     * @return 当前悬挂压缩量（米）。{@code 0 = 全伸展 = 轮子离地}，正值增大 = 压缩越大
     */
    double getSuspensionCompression();

    /**
     * 设置当前悬挂压缩量。
     * <p>
     * 由 {@code SuspensionSystem} 在每个逻辑 tick 中根据 3 条射线扫描结果写入，
     * 视觉 System 读取用于轮位渲染。
     *
     * @param compression 压缩量（米），{@code 0 = 全伸展}
     */
    void setSuspensionCompression(double compression);

    // ==================================================================
    //  接触点（运行时，由 SuspensionSystem 写入）
    // ==================================================================

    /**
     * 获取本 tick 中压缩量最大的接触点在 <b>SubLevel 局部空间</b>中的位置。
     * <p>
     * 接触点位于轮子表面（距轮毂距离 = 轮半径），由 {@code SuspensionSystem}
     * 的 3 条弧面射线扫描确定。{@code null} 表示本 tick 所有射线均未命中地面。
     * <p>
     * 默认实现为空操作（返回 {@code null}）。需要接触点数据的实现者应重写此方法。
     *
     * @return SubLevel 局部空间的接触点位置，或 {@code null}
     */
    @Nullable
    default Vector3dc getContactPointLocal() {
        return null;
    }

    /**
     * 设置本 tick 的接触点位置。
     * <p>
     * 由 {@code SuspensionSystem} 在完成射线扫描后写入。
     *
     * @param point SubLevel 局部空间的接触点位置，或 {@code null}
     */
    default void setContactPointLocal(@Nullable Vector3dc point) {}

    // ==================================================================
    //  轮子物理属性
    // ==================================================================

    /**
     * @return 轮胎半径（米），用于力臂和轮速换算
     */
    double getRadius();
}
