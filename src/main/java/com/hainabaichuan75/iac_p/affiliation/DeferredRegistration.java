package com.hainabaichuan75.iac_p.affiliation;

import com.hainabaichuan75.iac_p.IACP;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 延迟注册管理器——处理因 SubLevel 容器未就绪而失败的 Component 注册重试。
 * <p>
 * 当 {@link ComponentHost#registerComponent} 中
 * {@code Sable.HELPER.getContaining(be)} 返回 null 时（BE 刚创建，SubLevel 容器
 * 尚未同步），条目被暂存于此。实现类在 {@code tick()} 中调用
 * {@link #tick(BlockEntity)} 触发重试。
 * <p>
 * 线程安全：所有操作通过 {@code DEFERRED_LOCK} 同步。
 */
public final class DeferredRegistration {

    private DeferredRegistration() {
    }

    // ==================================================================
    //  延迟注册队列
    // ==================================================================
    /**
     * BlockPos → 延迟注册条目。当 BE.onLoad() 中 SubLevel 未就绪时暂存。
     */
    private static final Map<BlockPos, DeferredEntry> DEFERRED = new HashMap<>();

    private static final Object LOCK = new Object();

    /**
     * 最大重试 tick 数。超过此值仍未成功则放弃。
     */
    static final int MAX_RETRY_TICKS = 100;

    private record DeferredEntry(ComponentRole role, int ticksRemaining) {
        DeferredEntry withTicksRemaining(int newTicks) {
            return new DeferredEntry(this.role, newTicks);
        }
    }

    // ==================================================================
    //  API
    // ==================================================================
    /**
     * 添加一个延迟注册条目。
     */
    public static void add(BlockPos pos, ComponentRole role) {
        synchronized (LOCK) {
            if (!DEFERRED.containsKey(pos)) {
                DEFERRED.put(pos, new DeferredEntry(role, MAX_RETRY_TICKS));
            }
        }
    }

    /**
     * 移除所有延迟注册条目。
     */
    public static void clearAll() {
        synchronized (LOCK) {
            DEFERRED.clear();
        }
    }

    /**
     * 移除指定位置的延迟注册条目（注销时调用）。
     */
    public static void remove(BlockPos pos) {
        synchronized (LOCK) {
            DEFERRED.remove(pos);
        }
    }

    /**
     * 每 tick 调用一次，重试之前因 SubLevel 未就绪而失败的注册。
     *
     * @param be 当前方块实体
     */
    public static void tick(BlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        BlockPos pos = be.getBlockPos();

        DeferredEntry def;
        synchronized (LOCK) {
            def = DEFERRED.get(pos);
            if (def == null) {
                return; // 无待重试的注册
            }
        }

        SubLevel subLevel = Sable.HELPER.getContaining(be);
        if (subLevel != null) {
            // ── 重试成功 ──
            UUID subUUID = subLevel.getUniqueId();
            ComponentEntry entry = new ComponentEntry(subUUID, pos, def.role(), be);
            ComponentRegistry.register(entry);
            synchronized (LOCK) {
                DEFERRED.remove(pos);
            }
            IACP.LOGGER.info("[DeferredRegistration] 延迟注册成功: {} role={}", pos, def.role());
            return;
        }

        // ── 重试失败，递减计数器 ──
        int remaining = def.ticksRemaining() - 1;
        if (remaining <= 0) {
            synchronized (LOCK) {
                DEFERRED.remove(pos);
            }
            IACP.LOGGER.warn("[DeferredRegistration] 注册失败（已达最大重试次数）: {} role={}", pos, def.role());
        } else {
            synchronized (LOCK) {
                DEFERRED.put(pos, def.withTicksRemaining(remaining));
            }
        }
    }
}
