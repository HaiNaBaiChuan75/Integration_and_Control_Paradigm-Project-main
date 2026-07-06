package com.hainabaichuan75.iac_p.ecs.v2.common.part;

import com.hainabaichuan75.iac_p.ecs.v2.api.part.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.View;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 轮子运行时状态 —— 当前轮速、悬挂压缩、接触点。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 * <p>
 * 与配置参数 {@link WheelDef} 分离 — Def/State 是同级组件，各持独立 {@link ComponentKey}，
 * 通过 {@code part.getComponent(WheelDef.KEY)} / {@code part.getComponent(WheelState.KEY)} 分别存取。
 * <p>
 * 悬挂压缩量的 clamp 约束由写入方（物理 System）保证，本记录不持有 Def 引用。
 *
 * @param rpm                   当前实际轮端 RPM，正值 = 前进方向旋转
 * @param suspensionCompression 当前悬挂压缩量（米）。
 *                              {@code 0 = 全伸展 = 轮子离地}，正值增大 = 压缩越大
 * @param contactPointLocal     本 tick 中压缩量最大的接触点在 SubLevel 局部空间中的位置，
 *                              {@code null} 表示本 tick 所有射线均未命中地面。
 *                              仅运行时写入，不持久化到 NBT。
 */
public record WheelState(double rpm, double suspensionCompression, @Nullable Vector3dc contactPointLocal) {

    /**
     * 紧凑构造：入口防御性拷贝，确保 record 持有独立副本不受外部修改。
     * 参见 CLAUDE.md 「JOML 对象约定」。
     */
    public WheelState {
        if (contactPointLocal != null) {
            contactPointLocal = new Vector3d(contactPointLocal);
        }
    }

    /**
     * 零轮速、全伸展、无接触点
     */
    public static final WheelState IDLE = new WheelState(0, 0, null);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    /**
     * 仅更新轮速，保持其他字段不变
     */
    @NotNull
    public WheelState withRpm(double rpm) {
        return new WheelState(rpm, suspensionCompression, contactPointLocal);
    }

    /**
     * 仅更新悬挂压缩，保持其他字段不变
     */
    @NotNull
    public WheelState withCompression(double suspensionCompression) {
        return new WheelState(rpm, suspensionCompression, contactPointLocal);
    }

    /**
     * 仅更新接触点，保持其他字段不变
     */
    @NotNull
    public WheelState withContactPoint(@Nullable Vector3dc contactPointLocal) {
        return new WheelState(rpm, suspensionCompression, contactPointLocal);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_RPM = "rpm";
    private static final String TAG_COMPRESSION = "compression";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_RPM, rpm);
        tag.putDouble(TAG_COMPRESSION, suspensionCompression);
        // contactPointLocal 仅运行时写入，不同步 NBT
        return tag;
    }

    @NotNull
    public static WheelState fromTag(@NotNull CompoundTag tag) {
        return new WheelState(tag.getDouble(TAG_RPM), tag.getDouble(TAG_COMPRESSION), null // 接触点不持久化
        );
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 轮子运行时状态的组件键，包含 NBT 元数据
     */
    public static final ComponentKey<WheelState> KEY = ComponentKey.of(WheelState.class, "wheel", WheelState::toTag,
            tag -> WheelState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  工厂
    // ====================================================================

    /**
     * 创建零轮速、全伸展状态的轮子
     */
    @NotNull
    public static WheelState createDefault() {
        return IDLE;
    }

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var wv = WheelState.view(part);
     * wv.set(wv.get().withRpm(100));
     * }</pre>
     */
    @NotNull
    public static View<WheelState> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
