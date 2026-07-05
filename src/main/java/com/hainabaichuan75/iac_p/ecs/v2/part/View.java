package com.hainabaichuan75.iac_p.ecs.v2.part;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 类型化组件访问器 —— 持有 {@link Part} 引用和 {@link ComponentKey}。
 * <p>
 * 消除重复传 {@code part} + {@code EngineState.KEY} 的样板代码：
 * <pre>{@code
 * // 无访问器
 * EngineState s = part.getComponent(EngineState.KEY);
 * part.setComponent(EngineState.KEY, s.withTorque(50));
 *
 * // 有访问器
 * View<EngineState> ev = View.of(part, EngineState.KEY);
 * if (ev != null) {
 *     ev.set(ev.get().withTorque(50));
 * }
 * }</pre>
 * <p>
 * 工厂方法 {@link #of(Part, ComponentKey)} 在组件不存在时返回 {@code null}，
 * 而本 record 实例在构造后要求组件存在（{@link #get()} 若不存在则抛出异常）。
 * <p>
 * 多组件批量访问见 {@link #of(Part, ComponentKey, ComponentKey)} 及相关重载。
 *
 * @param part 部件实例
 * @param key  组件键
 * @param <T>  组件类型参数
 */
public record View<T>(@NotNull Part part, @NotNull ComponentKey<T> key) {

    // ====================================================================
    //  工厂：单组件
    // ====================================================================

    /**
     * 创建类型化组件访问器，仅当组件存在时返回非 null。
     * <pre>{@code
     * View.of(part, EngineState.KEY).ifPresent(ev -> {
     *     ev.set(ev.get().withTorque(50));
     * });
     * }</pre>
     *
     * @return 组件访问器，部件无此组件时返回 {@code null}
     */
    @Contract(pure = true)
    @Nullable
    public static <T> View<T> of(@NotNull Part part, @NotNull ComponentKey<T> key) {
        return part.getComponent(key) != null ? new View<>(part, key) : null;
    }

    // ====================================================================
    //  工厂：多组件批量
    // ====================================================================

    /**
     * 同时获取两个组件的类型化访问器。
     * 任一组件缺失时返回 {@code null}，避免逐层 null 检查。
     */
    @Contract(pure = true)
    @Nullable
    public static <T1, T2> Views2<T1, T2> of(@NotNull Part part, @NotNull ComponentKey<T1> key1,
                                             @NotNull ComponentKey<T2> key2) {
        if (part.getComponent(key1) == null || part.getComponent(key2) == null) {
            return null;
        }
        return new Views2<>(new View<>(part, key1), new View<>(part, key2));
    }

    /**
     * 同时获取三个组件的类型化访问器。
     * 任一组件缺失时返回 {@code null}。
     */
    @Contract(pure = true)
    @Nullable
    public static <T1, T2, T3> Views3<T1, T2, T3> of(@NotNull Part part, @NotNull ComponentKey<T1> key1,
                                                     @NotNull ComponentKey<T2> key2, @NotNull ComponentKey<T3> key3) {
        if (part.getComponent(key1) == null || part.getComponent(key2) == null || part.getComponent(key3) == null) {
            return null;
        }
        return new Views3<>(new View<>(part, key1), new View<>(part, key2), new View<>(part, key3));
    }

    /**
     * 同时获取四个组件的类型化访问器。
     * 任一组件缺失时返回 {@code null}。
     */
    @Contract(pure = true)
    @Nullable
    public static <T1, T2, T3, T4> Views4<T1, T2, T3, T4> of(@NotNull Part part, @NotNull ComponentKey<T1> key1,
                                                             @NotNull ComponentKey<T2> key2,
                                                             @NotNull ComponentKey<T3> key3,
                                                             @NotNull ComponentKey<T4> key4) {
        if (part.getComponent(key1) == null || part.getComponent(key2) == null || part.getComponent(key3) == null || part.getComponent(key4) == null) {
            return null;
        }
        return new Views4<>(new View<>(part, key1), new View<>(part, key2), new View<>(part, key3), new View<>(part,
                key4));
    }

    // ====================================================================
    //  集合查找
    // ====================================================================

    /**
     * 从部件列表中查找所有持有指定组件的部件，返回类型化访问器列表。
     * <p>
     * 替代旧架构的 {@code instanceof} 遍历模式：
     * <pre>{@code
     * // 旧
     * for (Part p : parts) {
     *     if (p instanceof DriveWheel dw) {
     *         dw.setTorqueInput(...);
     *     }
     * }
     *
     * // 新
     * for (var dv : View.find(parts, DriveState.KEY)) {
     *     dv.set(dv.get().withTorqueInput(...));
     * }
     * }</pre>
     *
     * @param parts 部件列表
     * @param key   目标组件键
     * @return 持有该组件的部件访问器列表，无匹配时返回空列表
     */
    @NotNull
    public static <T> List<View<T>> find(@NotNull Collection<? extends Part> parts, @NotNull ComponentKey<T> key) {
        var result = new ArrayList<View<T>>();
        for (var part : parts) {
            if (part.getComponent(key) != null) {
                result.add(new View<>(part, key));
            }
        }
        return result;
    }

    /**
     * 从部件列表中查找所有同时持有两个指定组件的部件，返回访问器对列表。
     * <p>
     * 替代旧架构的双重 {@code instanceof} 模式：
     * <pre>{@code
     * // 旧
     * for (Part p : parts) {
     *     if (p instanceof DriveWheel dw && p instanceof SteeringWheel sw) { ... }
     * }
     *
     * // 新
     * for (var vs : View.find(parts, DriveState.KEY, SteeringState.KEY)) {
     *     vs.v1().set(vs.v1().get().withTorqueInput(...));
     *     vs.v2().set(vs.v2().get().withSteeringInput(...));
     * }
     * }</pre>
     *
     * @param parts 部件列表
     * @param key1  第一个组件键
     * @param key2  第二个组件键
     * @return 同时持有两个组件的访问器对列表，无匹配时返回空列表
     */
    @NotNull
    public static <T1, T2> List<Views2<T1, T2>> find(@NotNull Collection<? extends Part> parts,
                                                     @NotNull ComponentKey<T1> key1, @NotNull ComponentKey<T2> key2) {
        var result = new ArrayList<Views2<T1, T2>>();
        for (var part : parts) {
            if (part.getComponent(key1) != null && part.getComponent(key2) != null) {
                result.add(new Views2<>(new View<>(part, key1), new View<>(part, key2)));
            }
        }
        return result;
    }

    /**
     * 从部件列表中查找所有同时持有三个指定组件的部件，返回访问器组列表。
     *
     * @param parts 部件列表
     * @param key1  第一个组件键
     * @param key2  第二个组件键
     * @param key3  第三个组件键
     * @return 同时持有三个组件的访问器组列表，无匹配时返回空列表
     */
    @NotNull
    public static <T1, T2, T3> List<Views3<T1, T2, T3>> find(@NotNull Collection<? extends Part> parts,
                                                             @NotNull ComponentKey<T1> key1,
                                                             @NotNull ComponentKey<T2> key2,
                                                             @NotNull ComponentKey<T3> key3) {
        var result = new ArrayList<Views3<T1, T2, T3>>();
        for (var part : parts) {
            if (part.getComponent(key1) != null && part.getComponent(key2) != null && part.getComponent(key3) != null) {
                result.add(new Views3<>(new View<>(part, key1), new View<>(part, key2), new View<>(part, key3)));
            }
        }
        return result;
    }

    /**
     * 从部件列表中查找所有同时持有四个指定组件的部件，返回访问器组列表。
     *
     * @param parts 部件列表
     * @param key1  第一个组件键
     * @param key2  第二个组件键
     * @param key3  第三个组件键
     * @param key4  第四个组件键
     * @return 同时持有四个组件的访问器组列表，无匹配时返回空列表
     */
    @NotNull
    public static <T1, T2, T3, T4> List<Views4<T1, T2, T3, T4>> find(@NotNull Collection<? extends Part> parts,
                                                                     @NotNull ComponentKey<T1> key1,
                                                                     @NotNull ComponentKey<T2> key2,
                                                                     @NotNull ComponentKey<T3> key3,
                                                                     @NotNull ComponentKey<T4> key4) {
        var result = new ArrayList<Views4<T1, T2, T3, T4>>();
        for (var part : parts) {
            if (part.getComponent(key1) != null && part.getComponent(key2) != null && part.getComponent(key3) != null && part.getComponent(key4) != null) {
                result.add(new Views4<>(new View<>(part, key1), new View<>(part, key2), new View<>(part, key3),
                        new View<>(part, key4)));
            }
        }
        return result;
    }

    // ====================================================================
    //  访问器方法
    // ====================================================================

    /**
     * 获取组件值。
     * <p>
     * 前提：此 View 关联的 Part 持有该组件类型（未持有属于调用方错误）。
     *
     * @throws IllegalStateException 若组件不存在
     */
    public @NotNull T get() {
        var value = part.getComponent(key);
        if (value == null) {
            throw new IllegalStateException("Component [" + key.nbtKey() + "] not present on " + part);
        }
        return value;
    }

    /**
     * 设置组件值（{@code value} 不允许为 null）。
     */
    public void set(@NotNull T value) {
        part.setComponent(key, value);
    }

    // ====================================================================
    //  多组件容器
    // ====================================================================

    /**
     * 两个组件的访问器对
     */
    public record Views2<T1, T2>(@NotNull View<T1> v1, @NotNull View<T2> v2) {}

    /**
     * 三个组件的访问器组
     */
    public record Views3<T1, T2, T3>(@NotNull View<T1> v1, @NotNull View<T2> v2, @NotNull View<T3> v3) {}

    /**
     * 四个组件的访问器组
     */
    public record Views4<T1, T2, T3, T4>(@NotNull View<T1> v1, @NotNull View<T2> v2, @NotNull View<T3> v3,
                                         @NotNull View<T4> v4) {}
}
