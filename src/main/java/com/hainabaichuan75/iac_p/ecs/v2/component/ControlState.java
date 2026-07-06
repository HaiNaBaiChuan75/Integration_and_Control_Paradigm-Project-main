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
 * 控制状态 —— ECS <b>组件（Component）</b>，驾驶员的全部意图输入。
 * <p>
 * 由网络包写入、System 层读取。通过 {@code part.getComponent(ControlState.KEY)}
 * 获取完整快照。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 *
 * @param intent    移动意图向量（SubLevel 局部空间），z- = 前，z+ = 后，x+ = 右，x- = 左
 * @param braking   刹车是否踩下
 * @param firing    是否正在开火
 * @param aimTarget 瞄准目标的世界坐标，{@code null} = 无目标
 */
public record ControlState(@NotNull Vector3dc intent, boolean braking, boolean firing, @Nullable Vector3dc aimTarget) {

    /**
     * 零输入状态常量
     */
    public static final ControlState IDLE = new ControlState(new Vector3d(), false, false, null);

    /**
     * 紧凑构造：入口防御性拷贝，确保 record 持有独立副本不受外部修改。
     * 参见 CLAUDE.md 「JOML 对象约定」。
     */
    public ControlState {
        intent = new Vector3d(intent);
        if (aimTarget != null) {
            aimTarget = new Vector3d(aimTarget);
        }
    }

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    @NotNull
    public ControlState withIntent(@NotNull Vector3dc intent) {
        return new ControlState(intent, braking, firing, aimTarget);
    }

    @NotNull
    public ControlState withBraking(boolean braking) {
        return new ControlState(intent, braking, firing, aimTarget);
    }

    @NotNull
    public ControlState withFiring(boolean firing) {
        return new ControlState(intent, braking, firing, aimTarget);
    }

    @NotNull
    public ControlState withAimTarget(@Nullable Vector3dc aimTarget) {
        return new ControlState(intent, braking, firing, aimTarget);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_INTENT_X = "intentX";
    private static final String TAG_INTENT_Y = "intentY";
    private static final String TAG_INTENT_Z = "intentZ";
    private static final String TAG_BRAKING = "braking";
    private static final String TAG_FIRING = "firing";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_INTENT_X, intent.x());
        tag.putDouble(TAG_INTENT_Y, intent.y());
        tag.putDouble(TAG_INTENT_Z, intent.z());
        tag.putBoolean(TAG_BRAKING, braking);
        tag.putBoolean(TAG_FIRING, firing);
        return tag;
    }

    @NotNull
    public static ControlState fromTag(@NotNull CompoundTag tag) {
        return new ControlState(new Vector3d(tag.getDouble(TAG_INTENT_X), tag.getDouble(TAG_INTENT_Y),
                tag.getDouble(TAG_INTENT_Z)), tag.getBoolean(TAG_BRAKING), tag.getBoolean(TAG_FIRING), null //
                // 瞄准目标不同步 NBT，只在运行时由网络包写入
        );
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 控制状态的类型键，包含 NBT 元数据
     */
    public static final ComponentKey<ControlState> KEY = ComponentKey.of(ControlState.class, "control",
            ControlState::toTag, tag -> ControlState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器（可空）。
     * <p>
     * 部件无此组件时返回 {@code null}，调用方必须处理缺失情况。
     * <pre>{@code
     * var cv = ControlState.view(part);
     * if (cv != null) {
     *     cv.set(cv.get().withBraking(true));
     * }
     * }</pre>
     */
    @Nullable
    public static View<ControlState> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
