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
 * 轮子运行时状态 —— ECS <b>组件（Component）</b>，当前帧的轮位运行值。
 * <p>
 * 包含三个机械自由度的当前值：
 * <ul>
 *   <li><b>angularVelocity</b> — 旋转</li>
 *   <li><b>suspensionCompression</b> — 悬挂</li>
 *   <li><b>steeringAngle</b> — 转向</li>
 * </ul>
 * <p>
 * 以及当前帧输入（由上游 System 写入）：
 * <ul>
 *   <li><b>torque</b> — 驱动力矩（Nm），符号由动力系统根据驾驶意图分配，
 *               正值：使轮子向 {@link WheelDef#mountDirection} + 右手定则的正方向加速</li>
 *   <li><b>braking</b> — 刹车是否施加，System 据此阻滞轮速，让摩擦力自然减速</li>
 * </ul>
 * <p>
 * 制动不是独立力矩——刹车钳只是阻滞轮子旋转，实际减速度来自轮胎-地面摩擦。
 * <p>
 * 和环境快照（来自 SubLevel 物理查询，避免重复 raycast）：
 * <ul>
 *   <li><b>contactPointLocal</b> — 本 tick 压缩量最大的接触点</li>
 * </ul>
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 * 与配置参数 {@link WheelDef} 分离，Def/State 各持独立 {@link ComponentKey}。
 *
 * @param angularVelocity       当前轮速（rad/s），正值 = {@link WheelDef#mountDirection} 正方向旋转
 * @param suspensionCompression 当前悬挂压缩量（m），{@code 0} = 全伸展
 * @param steeringAngle         当前转向角（°），{@code 0} = 回正，
 *                              {@link WheelDef#maxSteeringAngle} 决定限位
 * @param torque                驱动力矩（Nm），符号由动力系统根据驾驶意图分配，
 *                              正值：使轮子向 {@link WheelDef#mountDirection} + 右手定则的正方向加速
 * @param braking               刹车是否施加，{@code true} = 阻滞轮速让摩擦力减速
 * @param prevCompression       上一帧的悬挂压缩量（m），用于阻尼计算中的
 *                              {@code velocity = (compression − prevCompression) / dt}
 * @param contactPointLocal     当前 tick 中压缩量最大的接触点在 SubLevel 局部空间中的位置，
 *                              {@code null} 表示本 tick 所有射线均未命中地面。
 *                              仅运行时写入，不持久化到 NBT。
 */
public record WheelState(double angularVelocity, double suspensionCompression, double steeringAngle, double torque,
                         boolean braking, double prevCompression, @Nullable Vector3dc contactPointLocal) {

    /**
     * 紧凑构造：null contactPoint 跳过拷贝；无效浮点归零。
     * 参见 CLAUDE.md 「JOML 对象约定」
     */
    public WheelState {
        if (contactPointLocal != null) {
            contactPointLocal = new Vector3d(contactPointLocal);
        }
        if (!Double.isFinite(angularVelocity)) angularVelocity = 0;
        if (!Double.isFinite(suspensionCompression)) suspensionCompression = 0;
        if (!Double.isFinite(steeringAngle)) steeringAngle = 0;
        if (!Double.isFinite(torque)) torque = 0;
        if (!Double.isFinite(prevCompression)) prevCompression = 0;
    }

    /**
     * 零状态：静止、回正、全伸展、无力矩、不刹车、无接触
     */
    public static final WheelState IDLE = new WheelState(0, 0, 0, 0, false, 0, null);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    @NotNull
    public WheelState withAngularVelocity(double angularVelocity) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    @NotNull
    public WheelState withCompression(double suspensionCompression) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    @NotNull
    public WheelState withSteeringAngle(double steeringAngle) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    @NotNull
    public WheelState withTorque(double torque) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    @NotNull
    public WheelState withBraking(boolean braking) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    @NotNull
    public WheelState withContactPoint(@Nullable Vector3dc contactPointLocal) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, prevCompression,
                contactPointLocal);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_ANGULAR_VEL = "angularVelocity";
    private static final String TAG_COMPRESSION = "compression";
    private static final String TAG_STEER_ANGLE = "steeringAngle";
    private static final String TAG_TORQUE = "torque";
    private static final String TAG_BRAKING = "braking";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_ANGULAR_VEL, angularVelocity);
        tag.putDouble(TAG_COMPRESSION, suspensionCompression);
        tag.putDouble(TAG_STEER_ANGLE, steeringAngle);
        tag.putDouble(TAG_TORQUE, torque);
        tag.putBoolean(TAG_BRAKING, braking);
        // contactPointLocal 仅运行时写入，不同步 NBT
        return tag;
    }

    @NotNull
    public static WheelState fromTag(@NotNull CompoundTag tag) {
        return new WheelState(tag.getDouble(TAG_ANGULAR_VEL), tag.getDouble(TAG_COMPRESSION), tag.getDouble(TAG_STEER_ANGLE), tag.getDouble(TAG_TORQUE), tag.getBoolean(TAG_BRAKING), 0, // prevCompression 不持久化
                null // 接触点不持久化
        );
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    public static final ComponentKey<WheelState> KEY = ComponentKey.of(WheelState.class, "wheel", WheelState::toTag,
            tag -> WheelState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  工厂
    // ====================================================================

    @NotNull
    public static WheelState createDefault() {
        return IDLE;
    }

    /**
     * 更新压缩量与上一帧压缩量（由 SuspensionSystem 调用）。
     * <p>
     * prevCompression 自动设为旧值、suspensionCompression 设为新值。
     */
    @NotNull
    public WheelState withCompressionUpdate(double newCompression, @Nullable Vector3dc newContactPoint) {
        return new WheelState(angularVelocity, newCompression, steeringAngle, torque, braking, suspensionCompression,
                newContactPoint);
    }

    // ====================================================================
    //  访问器
    // ====================================================================

    @Nullable
    public static View<WheelState> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
