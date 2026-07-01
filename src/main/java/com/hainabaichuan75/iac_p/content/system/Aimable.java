package com.hainabaichuan75.iac_p.content.system;

import net.minecraft.world.phys.Vec3;

/**
 * 可被瞄准系统驱动的武器部件接口。
 * <p>
 * 实现此接口的 BlockEntity 可通过 {@link #aimAt(Vec3)} 统一驱动，
 * 由 {@link WeaponAimSystem} 每 tick 调用。
 * <p>
 * 当前实现类：
 * <ul>
 *   <li>{@code MachineGunBaseBlockEntity} — 通过 {@code driveImmediate(float, float)} 驱动</li>
 *   <li>{@code ShotgunBaseBlockEntity} — 通过 {@code driveImmediate(float, float)} 驱动</li>
 *   <li>{@code TurretTestBlockEntity} — 通过 {@code driveImmediate(float, float)} 驱动</li>
 * </ul>
 */
public interface Aimable {

    /**
     * 设置瞄准目标点（世界坐标）。
     * <p>
     * 实现类应将世界坐标转换为载具局部空间，计算 yaw/pitch 角度，
     * 并通过 {@code driveImmediate()} 立即驱动。
     *
     * @param targetAbsPoint 目标点的世界坐标
     */
    void aimAt(Vec3 targetAbsPoint);
}
