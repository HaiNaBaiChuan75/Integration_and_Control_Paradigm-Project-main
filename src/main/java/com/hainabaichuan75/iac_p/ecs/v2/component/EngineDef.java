package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 引擎定义 —— ECS <b>组件（Component）</b>，不可变的引擎动力参数。
 * <p>
 * 基于<b>速度衰减模型</b>：可用扭矩是载具当前速度的函数，而非传统 RPM 曲线。
 * 锚点从 {@code maxRpm}（发动机内部转速）替换为 {@link #ceilingSpeed}（载具理论天花板速度），
 * 消除了无变速箱时 RPM 无处可用的尴尬。
 * <p>
 * 命名借鉴武器射程概念：有效速度 = 命中率有保证的距离，天花板速度 = 弹头仍有动能的理论上限。
 * 实际可达极速由 System 层动力学方程自然决定，总低于 ceilingSpeed。
 * <p>
 * 与运行时状态 {@link EngineState} 分离，Def/State 各持独立 {@link ComponentKey}。
 * <p>
 * <b>衰减约定</b>（由 System 层实现）：
 * <pre>{@code
 * v ≤ effectiveSpeed → τ = maxTorque                                   // 满扭矩
 * effectiveSpeed < v < ceilingSpeed → τ = maxTorque × decay(v)         // 衰减
 * v ≥ ceilingSpeed → τ = 0                                             // 归零
 * }</pre>
 * 衰减曲线 {@code decay(v)} 默认用 Hermite 平滑：
 * {@code t = (v - effectiveSpeed) / (ceilingSpeed - effectiveSpeed),
 *  decay = 1 - smoothstep(t)}
 *
 * @param maxTorque      最大扭矩（Nm），{@code v ≤ effectiveSpeed} 时可用的满扭矩
 * @param effectiveSpeed 有效速度（m/s），低于此值满扭矩，超出后扭矩开始衰减
 * @param ceilingSpeed   天花板速度（m/s），扭矩在此归零的理论锚点。
 *                       实际极速由此值和阻力曲线的交点在下方自然收敛。
 *                       类似武器最大射程 ≠ 有效射程。
 */
public record EngineDef(double maxTorque, double effectiveSpeed, double ceilingSpeed) {

    /**
     * 默认引擎定义（扭矩 50 Nm，有效 5 m/s≈18 km/h，天花板 22.22 m/s≈80 km/h）
     */
    public static final EngineDef DEFAULT = new EngineDef(50.0, 5.0, 22.22);

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_MAX_TORQUE = "maxTorque";
    private static final String TAG_EFFECTIVE_SPEED = "effectiveSpeed";
    private static final String TAG_CEILING_SPEED = "ceilingSpeed";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_MAX_TORQUE, maxTorque);
        tag.putDouble(TAG_EFFECTIVE_SPEED, effectiveSpeed);
        tag.putDouble(TAG_CEILING_SPEED, ceilingSpeed);
        return tag;
    }

    @NotNull
    public static EngineDef fromTag(@NotNull CompoundTag tag) {
        return new EngineDef(tag.getDouble(TAG_MAX_TORQUE), tag.getDouble(TAG_EFFECTIVE_SPEED),
                tag.getDouble(TAG_CEILING_SPEED));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    public static final ComponentKey<EngineDef> KEY = ComponentKey.of(EngineDef.class, "engine_def", EngineDef::toTag
            , tag -> EngineDef.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器（可空）。
     * <p>
     * 部件无此组件时返回 {@code null}，调用方必须处理缺失情况。
     * <pre>{@code
     * var dv = EngineDef.view(part);
     * if (dv != null) {
     *     var def = dv.get();
     * }
     * }</pre>
     */
    @Nullable
    public static View<EngineDef> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
