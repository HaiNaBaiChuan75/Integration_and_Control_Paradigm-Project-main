package com.hainabaichuan75.iac_p.ecs.v2.api.component;

import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 组件键 —— ECS 中 <b>组件（Component）</b>的类型安全标识符，持有 NBT 元数据。
 * <p>
 * 对比 {@link Class} 作为键：Class 只描述"是什么类型"，不包含
 * NBT 键名、序列化方法这些持久化所需的信息。ComponentKey 把三者绑定在一起：
 * <ul>
 *   <li>{@code type} — 存储时的 Class 引用（泛型令牌）</li>
 *   <li>{@code nbtKey} — NBT 中的标签名（稳定的字符串，不受类重命名影响）</li>
 *   <li>{@code encoder/decoder} — 序列化与反序列化函数</li>
 * </ul>
 * <p>
 * 编码器返回 {@link Tag} 而非 {@link net.minecraft.nbt.CompoundTag}：
 * 简单类型（枚举等）可直接用 {@link net.minecraft.nbt.StringTag}，避免不必要的包裹。
 * <p>
 * 每次 {@link #of} 调用时自动注册到全局表，{@link com.hainabaichuan75.iac_p.ecs.v2.entity.PartBlockEntity}
 * 的批量 NBT 操作遍历此全局表，不依赖 BE 实例的初始化状态。
 *
 * @param <T> 组件值的类型
 */
public final class ComponentKey<T> {

    private static final Map<String, ComponentKey<?>> REGISTRY = new LinkedHashMap<>();

    /**
     * 按 NBT 键名查找已注册的组件键，未注册返回 {@code null}。
     */
    @Contract(pure = true)
    public static @Nullable ComponentKey<?> byNbtKey(@NotNull String nbtKey) {
        return REGISTRY.get(nbtKey);
    }

    private final @NotNull Class<T> type;
    private final @NotNull String nbtKey;
    private final @NotNull Function<@NotNull T, @NotNull Tag> encoder;
    private final @NotNull Function<@NotNull Tag, @Nullable T> decoder;

    private ComponentKey(@NotNull Class<T> type, @NotNull String nbtKey,
                         @NotNull Function<@NotNull T, @NotNull Tag> encoder, @NotNull Function<@NotNull Tag,
                    @Nullable T> decoder) {
        this.type = type;
        this.nbtKey = nbtKey;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    // ====================================================================
    //  工厂（自动注册到全局表）
    // ====================================================================

    @Contract("_, _, _, _ -> new")
    public static <T> @NotNull ComponentKey<T> of(@NotNull Class<T> type, @NotNull String nbtKey,
                                                  @NotNull Function<@NotNull T, @NotNull Tag> encoder,
                                                  @NotNull Function<@NotNull Tag, @Nullable T> decoder) {
        var key = new ComponentKey<>(type, nbtKey, encoder, decoder);
        REGISTRY.put(nbtKey, key);
        return key;
    }

    // ====================================================================
    //  访问
    // ====================================================================

    public @NotNull Class<T> type() {return type;}

    public @NotNull String nbtKey() {return nbtKey;}

    @Contract(pure = true)
    public @NotNull Function<@NotNull T, @NotNull Tag> encoder() {return encoder;}

    @Contract(pure = true)
    public @NotNull Function<@NotNull Tag, @Nullable T> decoder() {return decoder;}

    // ====================================================================
    //  Object
    // ====================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentKey<?> that)) return false;
        return nbtKey.equals(that.nbtKey);
    }

    @Override
    public int hashCode() {
        return nbtKey.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return "Key[" + nbtKey + "]";
    }
}
