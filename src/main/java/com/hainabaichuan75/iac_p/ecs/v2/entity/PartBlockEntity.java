package com.hainabaichuan75.iac_p.ecs.v2.entity;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部件方块实体的抽象基类 —— ECS 中 <b>实体（Entity）</b>的默认实现，
 * 提供组件存储与 NBT 批量序列化。
 * <p>
 * 继承此类即可按 {@link ComponentKey} 存取组件。组件定义在 state record 上
 * （如 {@code EngineState.KEY}），包含 NBT 键名和编解码器。
 * <p>
 * NBT 生命周期（{@link #saveAdditional}、{@link #loadAdditional}、
 * {@link #getUpdatePacket}）已在此类中覆写完毕，子类无需再处理。
 * <p>
 * <b>子类典型结构</b>：
 * <pre>{@code
 * public class MyPartBE extends PartBlockEntity {
 *     public MyPartBE(BlockPos pos, BlockState state) {
 *         super(ModTypes.MY_PART.get(), pos, state);
 *         setComponent(SomeState.KEY, SomeState.createDefault());
 *         setComponent(AnotherState.KEY, AnotherState.IDLE);
 *     }
 *     // ← 没有 saveAdditional / getUpdatePacket
 *     // ← 只有 BE 特有的逻辑
 * }
 * }</pre>
 */
public abstract class PartBlockEntity extends BlockEntity implements Part {
    private static final String NBT_KEY = "vehicle_parts";

    /**
     * 组件存储（有序，保证 NBT 顺序稳定）
     */
    private final Map<ComponentKey<?>, Object> components = new LinkedHashMap<>();

    public PartBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(type, pos, blockState);
    }

    // ====================================================================
    //  Part 接口实现
    // ====================================================================

    @Override
    public @NotNull BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getComponent(@NotNull ComponentKey<T> key) {
        return (T) components.get(key);
    }

    @Override
    public <T> void setComponent(@NotNull ComponentKey<T> key, @Nullable T value) {
        if (value != null) {
            components.put(key, value);
        } else {
            components.remove(key);
        }
    }

    // ====================================================================
    //  Minecraft 生命周期（批量处理全部已注册组件）
    // ====================================================================

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveComponents(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadComponents(tag);
    }

    @Override
    @NotNull
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ====================================================================
    //  批量 NBT 序列化
    // ====================================================================

    /**
     * 写出本 BE 持有的所有组件（遍历自身 map，各 key 自带 encoder）。
     */
    @SuppressWarnings("unchecked")
    public void saveComponents(@NotNull CompoundTag tag) {
        CompoundTag payload = new CompoundTag();
        for (var entry : components.entrySet()) {
            ComponentKey<Object> key = (ComponentKey<Object>) entry.getKey();
            payload.put(key.nbtKey(), key.encoder().apply(entry.getValue()));
        }
        tag.put(NBT_KEY, payload);
    }

    /**
     * 从 NBT 读取组件（遍历 NBT 键名，去全局注册表查找对应组件键后解码存入）。
     */
    @SuppressWarnings("unchecked")
    public void loadComponents(@NotNull CompoundTag tag) {
        components.clear();
        CompoundTag payload = tag.getCompound(NBT_KEY);
        for (String nbtKey : payload.getAllKeys()) {
            ComponentKey<Object> key = (ComponentKey<Object>) ComponentKey.byNbtKey(nbtKey);
            if (key == null) {
                IACP.LOGGER.warn("Unknown component key in NBT: [{}] on {}, skipped", nbtKey, this);
                continue;
            }
            Tag nbt = payload.get(nbtKey);
            Object decoded = key.decoder().apply(nbt);
            if (decoded == null) {
                IACP.LOGGER.warn("Decoder returned null for component [{}] on {}", nbtKey, this);
                continue;
            }
            components.put(key, decoded);
        }
    }
}
