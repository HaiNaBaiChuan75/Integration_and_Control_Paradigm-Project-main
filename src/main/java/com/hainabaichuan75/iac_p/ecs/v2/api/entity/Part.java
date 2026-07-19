package com.hainabaichuan75.iac_p.ecs.v2.api.entity;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 载具部件的核心接口 —— ECS 中的 <b>实体（Entity）</b>。
 * <p>
 * 职责只有两个：
 * <ol>
 *   <li><b>身份</b> — {@link #getBlockEntity()} 此部件是哪个方块实体</li>
 *   <li><b>组件通道</b> — {@link #getComponent(ComponentKey)} /
 *       {@link #setComponent(ComponentKey, Object)} 访问部件持有的数据组件</li>
 * </ol>
 * <p>
 * 一个 Part 是什么角色（引擎、控制器、轮子），完全由它持有哪些组件类型决定。
 * <pre>{@code
 * SomeState s = part.getComponent(SomeState.KEY);
 * if (s != null) {
 *     part.setComponent(SomeState.KEY, s.withValue(...));
 * }
 * }</pre>
 * <p>
 * 类型化访问器见 {@link View#of(Part, ComponentKey)} 及相关重载。
 *
 * @see com.hainabaichuan75.iac_p.ecs.v2.entity.PartBlockEntity 抽象基类，提供组件存储 + NBT 批量序列化
 * @see View 类型化访问器与工厂
 */
@Deprecated(since = "1.0", forRemoval = true)
public interface Part extends BlockEntitySubLevelActor {
    @NotNull BlockEntity getBlockEntity();

    <T> @Nullable T getComponent(@NotNull ComponentKey<T> key);

    <T> void setComponent(@NotNull ComponentKey<T> key, @Nullable T value);
}
