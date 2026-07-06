package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 轮子定义 —— ECS <b>组件（Component）</b>，不可变的轮位机械参数。
 * <p>
 * 描述一个轮位的<b>内禀挂载几何</b>（组装后就固定的部分）：
 * <ul>
 *   <li><b>mount*</b> — 轮子在底盘上的固定位置和自旋方向（内禀，不随转向/悬挂变化）</li>
 *   <li><b>suspension*</b> — 悬挂行程方向与刚度</li>
 *   <li><b>steering*</b> — 转向自由度与限位</li>
 *   <li><b>grip* / rolling*</b> — 轮胎摩擦特性</li>
 * </ul>
 * <p>
 * 运行时的实际自旋轴由 {@link #mountDirection} 绕 {@link #steeringAxis} 旋转
 * {@link WheelState#steeringAngle} 度得到。
 * <p>
 * 与运行时状态 {@link WheelState} 分离，Def/State 各持独立 {@link ComponentKey}。
 * <p>
 * 遵循值类型约定：不可变、可 NBT 序列化、按值相等。
 *
 * @param radius              轮胎半径（m）
 * @param mountDirection      挂载方向 — 即回正时的自旋轴（局部空间单位向量）
 * @param mountPoint          0 压缩挂载点（局部空间位置），悬挂和转向均不改变此位置
 * @param suspensionDirection 悬挂行程方向（局部空间单位向量）
 * @param suspensionStiffness 悬挂刚度（N/m）
 * @param steeringAxis        转向转动轴（局部空间单位向量）
 * @param maxSteeringAngle    最大转向角（°），{@code 0} = 非转向轮
 * @param driven              是否接受动力，{@code true} = System 向其分配扭矩，{@code false} = 惰轮
 * @param gripForward         纵向抓地系数
 * @param gripLateral         侧向抓地系数
 * @param rollingResistance   滚动阻力系数
 */
public record WheelDef(double radius, @NotNull Vector3dc mountDirection, @NotNull Vector3dc mountPoint,
                       @NotNull Vector3dc suspensionDirection, double suspensionStiffness,
                       @NotNull Vector3dc steeringAxis, double maxSteeringAngle, boolean driven, double gripForward,
                       double gripLateral, double rollingResistance) {

    /**
     * 默认挂载方向：指向 Z-（载具前方）
     */
    private static final Vector3dc DEFAULT_MOUNT_DIR = new Vector3d(0, 0, -1);

    /**
     * 默认挂载点：原点
     */
    private static final Vector3dc DEFAULT_MOUNT_POINT = new Vector3d();

    /**
     * 默认悬挂方向：沿 Y+（向上）
     */
    private static final Vector3dc DEFAULT_SUSPENSION_DIR = new Vector3d(0, 1, 0);

    /**
     * 默认转向轴：沿 Y+（向上）
     */
    private static final Vector3dc DEFAULT_STEERING_AXIS = new Vector3d(0, 1, 0);

    /**
     * 默认轮子定义（半径 0.5m，刚度 20000 N/m）
     */
    public static final WheelDef DEFAULT = new WheelDef(0.5, DEFAULT_MOUNT_DIR, DEFAULT_MOUNT_POINT,
            DEFAULT_SUSPENSION_DIR, 20000.0, DEFAULT_STEERING_AXIS, 30.0, true, 0.85, 0.80, 0.015);

    /**
     * 紧凑构造：null 向量回退到默认值；无效数值回退；入口防御性拷贝。
     * 参见 CLAUDE.md 「JOML 对象约定」。
     */
    public WheelDef {
        if (mountDirection == null) mountDirection = DEFAULT_MOUNT_DIR;
        else mountDirection = new Vector3d(mountDirection);

        if (mountPoint == null) mountPoint = DEFAULT_MOUNT_POINT;
        else mountPoint = new Vector3d(mountPoint);

        if (suspensionDirection == null) suspensionDirection = DEFAULT_SUSPENSION_DIR;
        else suspensionDirection = new Vector3d(suspensionDirection);

        if (steeringAxis == null) steeringAxis = DEFAULT_STEERING_AXIS;
        else steeringAxis = new Vector3d(steeringAxis);

        if (!Double.isFinite(radius)) radius = 0.5;
        if (!Double.isFinite(suspensionStiffness)) suspensionStiffness = 20000.0;
        if (!Double.isFinite(maxSteeringAngle)) maxSteeringAngle = 0;
        if (!Double.isFinite(gripForward)) gripForward = 0.85;
        if (!Double.isFinite(gripLateral)) gripLateral = 0.80;
        if (!Double.isFinite(rollingResistance)) rollingResistance = 0.015;
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_RADIUS = "radius";

    private static final String TAG_MOUNT_DIR_X = "mountDirX";
    private static final String TAG_MOUNT_DIR_Y = "mountDirY";
    private static final String TAG_MOUNT_DIR_Z = "mountDirZ";

    private static final String TAG_MOUNT_POINT_X = "mountPointX";
    private static final String TAG_MOUNT_POINT_Y = "mountPointY";
    private static final String TAG_MOUNT_POINT_Z = "mountPointZ";

    private static final String TAG_SUSP_DIR_X = "suspDirX";
    private static final String TAG_SUSP_DIR_Y = "suspDirY";
    private static final String TAG_SUSP_DIR_Z = "suspDirZ";

    private static final String TAG_STIFFNESS = "suspensionStiffness";

    private static final String TAG_STEER_AXIS_X = "steerAxisX";
    private static final String TAG_STEER_AXIS_Y = "steerAxisY";
    private static final String TAG_STEER_AXIS_Z = "steerAxisZ";

    private static final String TAG_MAX_STEER_ANGLE = "maxSteeringAngle";

    private static final String TAG_DRIVEN = "driven";

    private static final String TAG_GRIP_FWD = "gripForward";
    private static final String TAG_GRIP_LAT = "gripLateral";
    private static final String TAG_ROLLING_RESIST = "rollingResistance";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_RADIUS, radius);

        tag.putDouble(TAG_MOUNT_DIR_X, mountDirection.x());
        tag.putDouble(TAG_MOUNT_DIR_Y, mountDirection.y());
        tag.putDouble(TAG_MOUNT_DIR_Z, mountDirection.z());

        tag.putDouble(TAG_MOUNT_POINT_X, mountPoint.x());
        tag.putDouble(TAG_MOUNT_POINT_Y, mountPoint.y());
        tag.putDouble(TAG_MOUNT_POINT_Z, mountPoint.z());

        tag.putDouble(TAG_SUSP_DIR_X, suspensionDirection.x());
        tag.putDouble(TAG_SUSP_DIR_Y, suspensionDirection.y());
        tag.putDouble(TAG_SUSP_DIR_Z, suspensionDirection.z());

        tag.putDouble(TAG_STIFFNESS, suspensionStiffness);

        tag.putDouble(TAG_STEER_AXIS_X, steeringAxis.x());
        tag.putDouble(TAG_STEER_AXIS_Y, steeringAxis.y());
        tag.putDouble(TAG_STEER_AXIS_Z, steeringAxis.z());

        tag.putDouble(TAG_MAX_STEER_ANGLE, maxSteeringAngle);

        tag.putBoolean(TAG_DRIVEN, driven);

        tag.putDouble(TAG_GRIP_FWD, gripForward);
        tag.putDouble(TAG_GRIP_LAT, gripLateral);
        tag.putDouble(TAG_ROLLING_RESIST, rollingResistance);
        return tag;
    }

    @NotNull
    public static WheelDef fromTag(@NotNull CompoundTag tag) {
        return new WheelDef(tag.getDouble(TAG_RADIUS), new Vector3d(tag.getDouble(TAG_MOUNT_DIR_X),
                tag.getDouble(TAG_MOUNT_DIR_Y), tag.getDouble(TAG_MOUNT_DIR_Z)),
                new Vector3d(tag.getDouble(TAG_MOUNT_POINT_X), tag.getDouble(TAG_MOUNT_POINT_Y),
                        tag.getDouble(TAG_MOUNT_POINT_Z)), new Vector3d(tag.getDouble(TAG_SUSP_DIR_X),
                tag.getDouble(TAG_SUSP_DIR_Y), tag.getDouble(TAG_SUSP_DIR_Z)), tag.getDouble(TAG_STIFFNESS),
                new Vector3d(tag.getDouble(TAG_STEER_AXIS_X), tag.getDouble(TAG_STEER_AXIS_Y),
                        tag.getDouble(TAG_STEER_AXIS_Z)), tag.getDouble(TAG_MAX_STEER_ANGLE),
                tag.getBoolean(TAG_DRIVEN), tag.getDouble(TAG_GRIP_FWD), tag.getDouble(TAG_GRIP_LAT),
                tag.getDouble(TAG_ROLLING_RESIST));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    public static final ComponentKey<WheelDef> KEY = ComponentKey.of(WheelDef.class, "wheel_def", WheelDef::toTag,
            tag -> WheelDef.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    @Nullable
    public static View<WheelDef> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
