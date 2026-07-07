package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.util.NbtUtil;
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
 * @param suspensionDamping   悬挂阻尼系数（N·s/m），
 *                            {@code impulse = (k·x − c·v)Δt} 中的 c
 */
public record WheelDef(double radius, @NotNull Vector3dc mountDirection, @NotNull Vector3dc mountPoint,
                       @NotNull Vector3dc suspensionDirection, double suspensionStiffness,
                       @NotNull Vector3dc steeringAxis, double maxSteeringAngle, boolean driven, double gripForward,
                       double gripLateral, double rollingResistance, double suspensionDamping) {

    /**
     * 默认轮子定义（半径 1.0m，刚度 2000 N/m，阻尼 30 N·s/m）
     */
    public static final WheelDef DEFAULT = new WheelDef(1, new Vector3d(0, 0, -1), new Vector3d(0.5, 0, 0.5), new Vector3d(0, 1, 0), 2000.0, new Vector3d(0, 1, 0), 30.0, true, 0.85, 0.80, 0.015, 30.0);

    /**
     * 紧凑构造：null 向量回退到默认值；无效数值回退；入口防御性拷贝。
     * 参见 CLAUDE.md 「JOML 对象约定」。
     */
    public WheelDef {
        mountDirection = new Vector3d(mountDirection);
        mountPoint = new Vector3d(mountPoint);
        suspensionDirection = new Vector3d(suspensionDirection);
        steeringAxis = new Vector3d(steeringAxis);
        if (!Double.isFinite(mountDirection.lengthSquared())) mountDirection = new Vector3d(0, 0, -1);
        if (!Double.isFinite(mountPoint.lengthSquared())) mountPoint = new Vector3d(0.5, 0, 0.5);
        if (!Double.isFinite(suspensionDirection.lengthSquared())) suspensionDirection = new Vector3d(0, 1, 0);
        if (!Double.isFinite(steeringAxis.lengthSquared())) steeringAxis = new Vector3d(0, 1, 0);
        if (!Double.isFinite(radius)) radius = 1.0;
        if (!Double.isFinite(suspensionStiffness)) suspensionStiffness = 2000.0;
        if (!Double.isFinite(maxSteeringAngle)) maxSteeringAngle = 0;
        if (!Double.isFinite(gripForward)) gripForward = 0.85;
        if (!Double.isFinite(gripLateral)) gripLateral = 0.80;
        if (!Double.isFinite(rollingResistance)) rollingResistance = 0.015;
        if (!Double.isFinite(suspensionDamping)) suspensionDamping = 30.0;
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_RADIUS = "radius";

    private static final String TAG_MOUNT_DIR = "mountDir";
    private static final String TAG_MOUNT_POINT = "mountPoint";
    private static final String TAG_SUSP_DIR = "suspDir";
    private static final String TAG_STIFFNESS = "suspensionStiffness";
    private static final String TAG_STEER_AXIS = "steerAxis";

    private static final String TAG_MAX_STEER_ANGLE = "maxSteeringAngle";

    private static final String TAG_DRIVEN = "driven";

    private static final String TAG_SUSP_DAMPING = "suspensionDamping";

    private static final String TAG_GRIP_FWD = "gripForward";
    private static final String TAG_GRIP_LAT = "gripLateral";
    private static final String TAG_ROLLING_RESIST = "rollingResistance";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_RADIUS, radius);
        NbtUtil.putVec3d(tag, TAG_MOUNT_DIR, mountDirection);
        NbtUtil.putVec3d(tag, TAG_MOUNT_POINT, mountPoint);
        NbtUtil.putVec3d(tag, TAG_SUSP_DIR, suspensionDirection);
        tag.putDouble(TAG_STIFFNESS, suspensionStiffness);
        NbtUtil.putVec3d(tag, TAG_STEER_AXIS, steeringAxis);
        tag.putDouble(TAG_MAX_STEER_ANGLE, maxSteeringAngle);
        tag.putBoolean(TAG_DRIVEN, driven);
        tag.putDouble(TAG_GRIP_FWD, gripForward);
        tag.putDouble(TAG_GRIP_LAT, gripLateral);
        tag.putDouble(TAG_ROLLING_RESIST, rollingResistance);
        tag.putDouble(TAG_SUSP_DAMPING, suspensionDamping);
        return tag;
    }

    @NotNull
    public static WheelDef fromTag(@NotNull CompoundTag tag) {
        return new WheelDef(tag.getDouble(TAG_RADIUS), NbtUtil.getVec3d(tag, TAG_MOUNT_DIR), NbtUtil.getVec3d(tag,
                TAG_MOUNT_POINT), NbtUtil.getVec3d(tag, TAG_SUSP_DIR), tag.getDouble(TAG_STIFFNESS),
                NbtUtil.getVec3d(tag, TAG_STEER_AXIS), tag.getDouble(TAG_MAX_STEER_ANGLE), tag.getBoolean(TAG_DRIVEN), tag.getDouble(TAG_GRIP_FWD), tag.getDouble(TAG_GRIP_LAT), tag.getDouble(TAG_ROLLING_RESIST), tag.getDouble(TAG_SUSP_DAMPING));
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
