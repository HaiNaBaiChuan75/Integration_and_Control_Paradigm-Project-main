package com.hainabaichuan75.iac_p.util;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * NBT 序列化工具 —— 封装常见类型的 NBT 读写。
 * <p>
 * Vector3d 采用子 {@link CompoundTag} 格式：
 * <pre>{@code
 * "key" → CompoundTag { "x": double, "y": double, "z": double }
 * }</pre>
 * 可空向量在 null 时省略整个键。
 */
public final class NbtUtil {

    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    private NbtUtil() {}

    // ====================================================================
    //  Vector3d 写入
    // ====================================================================

    /**
     * 将 {@link Vector3dc} 写入 {@code tag} 的 {@code key} 子标签。
     * <p>
     * {@code vec} 为 {@code null} 时省略整个键（用于可空字段的 Optional 写入）。
     */
    @Contract(mutates = "param1")
    public static void putVec3d(@NotNull CompoundTag tag, @NotNull String key, @Nullable Vector3dc vec) {
        if (vec == null) return;
        var sub = new CompoundTag();
        sub.putDouble(TAG_X, vec.x());
        sub.putDouble(TAG_Y, vec.y());
        sub.putDouble(TAG_Z, vec.z());
        tag.put(key, sub);
    }

    // ====================================================================
    //  Vector3d 读取
    // ====================================================================

    /**
     * 从 {@code tag} 的 {@code key} 读取子标签并构造 {@link Vector3d}。
     *
     * @throws IllegalArgumentException key 不存在或不是 CompoundTag
     */
    @Contract("_, _ -> new")
    @NotNull
    public static Vector3d getVec3d(@NotNull CompoundTag tag, @NotNull String key) {
        var sub = tag.getCompound(key);
        if (sub.isEmpty()) {
            throw new IllegalArgumentException("Missing vector component key [" + key + "] in NBT");
        }
        return new Vector3d(sub.getDouble(TAG_X), sub.getDouble(TAG_Y), sub.getDouble(TAG_Z));
    }

    /**
     * 从 {@code tag} 读取可空 {@link Vector3d}，key 不存在时返回 {@code fallback}。
     * <p>
     * 配合可空向量的省略写入使用：{@code getVec3d(tag, "aimTarget", null)} → key 缺失时返回 null。
     */
    @Contract("_, _, null -> null; _, _, !null -> !null")
    @Nullable
    public static Vector3d getVec3d(@NotNull CompoundTag tag, @NotNull String key, @Nullable Vector3dc fallback) {
        if (!tag.contains(key, CompoundTag.TAG_COMPOUND)) {
            return fallback != null ? new Vector3d(fallback) : null;
        }
        return getVec3d(tag, key);
    }
}
