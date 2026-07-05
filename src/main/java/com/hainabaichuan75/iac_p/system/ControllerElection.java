package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.Controller;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 控制器选举工具 —— 在多控制器列表中按 BlockPos 字典序选举主控。
 * <p>
 * 多个 {@link VehicleTickSystem}（{@link SteeringSystem}、{@link WeaponAimSystem}）
 * 都需要从 Part 列表中找出主控制器，本类提供统一的静态方法避免重复。
 * <p>
 * <b>选举策略</b>：BlockPos 字典序（x→y→z）选最小者。
 * <ul>
 *   <li>0 控制器 → 返回 {@code null}，调用方应跳过本 tick</li>
 *   <li>1 控制器 → 直接作为主控</li>
 *   <li>&gt;1 控制器 → 按 BlockPos 字典序选最小者</li>
 * </ul>
 */
public final class ControllerElection {

    /**
     * 从 Part 列表中按 BlockPos 字典序选举主控制器。
     *
     * @param parts Part 列表（通常来自 {@code VehicleSystemRegistry.collectParts()}）
     * @return 主控制器，或 {@code null} 表示无可用控制器
     */
    @Nullable
    public static Controller findPrimary(List<? extends Part> parts) {
        Controller primary = null;
        for (Part part : parts) {
            if (part instanceof Controller ctrl) {
                if (primary == null || comparePos(ctrl, primary) < 0) {
                    primary = ctrl;
                }
            }
        }
        return primary;
    }

    private static int comparePos(Controller a, Controller b) {
        var pa = a.getBlockEntity().getBlockPos();
        var pb = b.getBlockEntity().getBlockPos();
        int cmp = Integer.compare(pa.getX(), pb.getX());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(pa.getY(), pb.getY());
        if (cmp != 0) return cmp;
        return Integer.compare(pa.getZ(), pb.getZ());
    }

    private ControllerElection() {}
}
