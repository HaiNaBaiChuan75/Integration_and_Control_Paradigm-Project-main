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
 *
 * <h3>字段</h3>
 * <ul>
 *   <li><b>angularVelocity</b> — 旋转（rad/s）</li>
 *   <li><b>suspensionCompression</b> — 悬挂压缩量（m），{@code 0} = 全伸展</li>
 *   <li><b>steeringAngle</b> — 转向（°），{@code 0} = 回正</li>
 *   <li><b>torque</b> — 驱动力矩（Nm），符号含方向，由上游 System 写入</li>
 *   <li><b>braking</b> — 刹车是否施加</li>
 *   <li><b>contactPointLocal</b> — 本 tick 接触点，{@code null} = 未命中地面</li>
 *   <li><b>compressionDelta</b> — 本 tick 压缩量变化（m），用于阻尼计算</li>
 * </ul>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 */
public record WheelState(double angularVelocity, double suspensionCompression, double steeringAngle, double torque,
                         boolean braking, @Nullable Vector3dc contactPointLocal, double compressionDelta) {

    /**
     * 紧凑构造：无效浮点归零；contactPoint null 跳过拷贝。
     */
    public WheelState {
        if (contactPointLocal != null) {
            contactPointLocal = new Vector3d(contactPointLocal);
        }
        if (!Double.isFinite(angularVelocity)) angularVelocity = 0;
        if (!Double.isFinite(suspensionCompression)) suspensionCompression = 0;
        if (!Double.isFinite(steeringAngle)) steeringAngle = 0;
        if (!Double.isFinite(torque)) torque = 0;
        if (!Double.isFinite(compressionDelta)) compressionDelta = 0;
    }

    public static final WheelState IDLE = new WheelState(0, 0, 0, 0, false, null, 0);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    @NotNull
    public WheelState withAngularVelocity(double av) {
        return new WheelState(av, suspensionCompression, steeringAngle, torque, braking, contactPointLocal, compressionDelta);
    }

    @NotNull
    public WheelState withCompression(double sc) {
        return new WheelState(angularVelocity, sc, steeringAngle, torque, braking, contactPointLocal, compressionDelta);
    }

    @NotNull
    public WheelState withSteeringAngle(double sa) {
        return new WheelState(angularVelocity, suspensionCompression, sa, torque, braking, contactPointLocal, compressionDelta);
    }

    @NotNull
    public WheelState withTorque(double t) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, t, braking, contactPointLocal, compressionDelta);
    }

    @NotNull
    public WheelState withBraking(boolean br) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, br, contactPointLocal, compressionDelta);
    }

    @NotNull
    public WheelState withContactPoint(@Nullable Vector3dc cp) {
        return new WheelState(angularVelocity, suspensionCompression, steeringAngle, torque, braking, cp, compressionDelta);
    }

    @NotNull
    public WheelState withCompressionAndDelta(double nc, @Nullable Vector3dc ncp) {
        return new WheelState(angularVelocity, nc, steeringAngle, torque, braking, ncp, nc - suspensionCompression);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble("angularVelocity", angularVelocity);
        tag.putDouble("compression", suspensionCompression);
        tag.putDouble("steeringAngle", steeringAngle);
        tag.putDouble("torque", torque);
        tag.putBoolean("braking", braking);
        return tag;
    }

    @NotNull
    public static WheelState fromTag(@NotNull CompoundTag tag) {
        return new WheelState(tag.getDouble("angularVelocity"), tag.getDouble("compression"),
                tag.getDouble("steeringAngle"), tag.getDouble("torque"), tag.getBoolean("braking"), null, 0);
    }

    // ====================================================================
    //  组件键 + 工厂
    // ====================================================================

    public static final ComponentKey<WheelState> KEY = ComponentKey.of(WheelState.class, "wheel", WheelState::toTag,
            tag -> WheelState.fromTag((CompoundTag) tag));

    @NotNull
    public static WheelState createDefault() {
        return IDLE;
    }

    @Nullable
    public static View<WheelState> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
