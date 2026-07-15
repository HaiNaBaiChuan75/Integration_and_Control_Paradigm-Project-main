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
 * 轮子定义 —— ECS <b>组件（Component）</b>，不可变的轮位参数。
 * <p>
 * 只包含旧版物理实际消费的参数。抓地系数、转向限位、弹簧刚度等
 * 由 {@code OldWheelSystem} 内置常量或上游 System 控制，不在此存放。
 * </p>
 *
 * @param radius     轮胎半径（m）
 * @param mountPoint 0 压缩时的轮毂挂载点（局部空间位置）
 */
public record WheelDef(double radius, @NotNull Vector3dc mountPoint) {

    public static final WheelDef DEFAULT = new WheelDef(1, new Vector3d(0.5, 0, 0.5));

    /**
     * 紧凑构造：无效值回退；入口防御性拷贝。
     */
    public WheelDef {
        mountPoint = new Vector3d(mountPoint);
        if (!Double.isFinite(mountPoint.lengthSquared())) mountPoint = new Vector3d(0.5, 0, 0.5);
        if (!Double.isFinite(radius)) radius = 1.0;
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    @NotNull
    public CompoundTag toTag() {
        var t = new CompoundTag();
        t.putDouble("radius", radius);
        NbtUtil.putVec3d(t, "mountPoint", mountPoint);
        return t;
    }

    @NotNull
    public static WheelDef fromTag(@NotNull CompoundTag t) {
        return new WheelDef(t.getDouble("radius"), NbtUtil.getVec3d(t, "mountPoint"));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    public static final ComponentKey<WheelDef> KEY = ComponentKey.of(WheelDef.class, "wheel_def", WheelDef::toTag,
            t -> WheelDef.fromTag((CompoundTag) t));

    @Nullable
    public static View<WheelDef> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
