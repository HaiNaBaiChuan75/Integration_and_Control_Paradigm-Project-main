package com.hainabaichuan75.iac_p.util;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Sable 物理引擎刚体工具类。
 * <p>
 * 提供常见刚体运动学计算，避免在多个 BlockEntity 中重复相同的向量操作。
 * 所有方法都是纯函数，不修改引擎状态，返回新分配的 {@link Vector3d}。
 * <p>
 * <b>核心公式</b> —— 刚体上某点的世界速度：
 * <pre>
 * v_point = v_linear + ω × (point - com)
 * </pre>
 * 其中 v_linear 是质心线速度，ω 是角速度，com 是质心位置。
 */
public final class RigidBodyUtil {
    private RigidBodyUtil() {}

    // ============================================================
    //  核心数学方法 —— 不依赖 Sable 类型，便于测试
    // ============================================================

    /**
     * 计算刚体上某点的世界速度。
     * <p>
     * {@code v = v_linear + ω × r}，其中 {@code r = worldPoint - centerOfMass}
     *
     * @param linearVelocity  质心线速度 (m/s)
     * @param angularVelocity 角速度 (rad/s)
     * @param worldPoint      目标点的世界坐标
     * @param centerOfMass    刚体质心的世界坐标
     * @return 该点的世界速度
     */
    public static Vector3d getVelocityAtPoint(Vector3dc linearVelocity, Vector3dc angularVelocity,
                                              Vector3dc worldPoint, Vector3dc centerOfMass) {
        Vector3d r = new Vector3d(worldPoint).sub(centerOfMass);
        return angularVelocity.cross(r, new Vector3d()).add(linearVelocity);
    }

    /**
     * 计算施加在刚体某点的力对质心产生的转矩。
     * <p>
     * {@code τ = r × F}，其中 {@code r = worldPoint - centerOfMass}
     *
     * @param force        施加的力 (N)
     * @param worldPoint   力作用点的世界坐标
     * @param centerOfMass 刚体质心的世界坐标
     * @return 转矩向量
     */
    public static Vector3d getTorqueFromForceAtPoint(Vector3dc force, Vector3dc worldPoint, Vector3dc centerOfMass) {
        Vector3d r = new Vector3d(worldPoint).sub(centerOfMass);
        return r.cross(force, new Vector3d());
    }

    /**
     * @return 速度大小 (m/s)
     */
    public static double getSpeed(Vector3dc velocity) {
        return velocity.length();
    }

    /**
     * @return 速度 (km/h)，1 m/s = 3.6 km/h，适用于 HUD 显示
     */
    public static double getSpeedKmh(Vector3dc velocity) {
        return velocity.length() * 3.6;
    }

    // ============================================================
    //  RigidBodyHandle 便利方法 —— 从句柄自动读取速度
    // ============================================================

    /**
     * 从 {@link RigidBodyHandle} 读取线速度和角速度，计算刚体上某点的世界速度。
     * <p>
     * 质心位置需要调用方提供，适用于已知 COM 的场景。
     *
     * @param handle       刚体句柄
     * @param worldPoint   目标点的世界坐标
     * @param centerOfMass 刚体质心的世界坐标
     * @return 该点的世界速度
     */
    public static Vector3d getVelocityAtPoint(RigidBodyHandle handle, Vector3dc worldPoint, Vector3dc centerOfMass) {
        Vector3d linvel = handle.getLinearVelocity(new Vector3d());
        Vector3d angvel = handle.getAngularVelocity(new Vector3d());
        return getVelocityAtPoint(linvel, angvel, worldPoint, centerOfMass);
    }

    /**
     * 从 {@link RigidBodyHandle} 读取速度，同时从 {@link ServerSubLevel} 读取质心位置，
     * 计算刚体上某点的世界速度。
     *
     * @param handle     刚体句柄
     * @param subLevel   SubLevel (用于读取 {@code logicalPose().position()} 作为质心)
     * @param worldPoint 目标点的世界坐标
     * @return 该点的世界速度
     */
    public static Vector3d getVelocityAtPoint(RigidBodyHandle handle, ServerSubLevel subLevel, Vector3dc worldPoint) {
        return getVelocityAtPoint(handle, worldPoint, subLevel.logicalPose().position());
    }

    // ============================================================
    //  ServerSubLevel 便利方法 —— 直接传入 subLevel 一键获取
    // ============================================================

    /**
     * 直接从 {@link ServerSubLevel} 读取最新线速度/角速度/质心位置，
     * 计算刚体上某点的世界速度。
     * <p>
     * 无需 {@link RigidBodyHandle}，内部使用 SubLevel 的
     * {@code latestLinearVelocity}、{code latestAngularVelocity}
     * 和 {@code logicalPose().position()}。
     *
     * @param subLevel   SubLevel
     * @param worldPoint 目标点的世界坐标
     * @return 该点的世界速度
     */
    public static Vector3d getVelocityAtPoint(ServerSubLevel subLevel, Vector3dc worldPoint) {
        return getVelocityAtPoint(subLevel.latestLinearVelocity, subLevel.latestAngularVelocity, worldPoint,
                subLevel.logicalPose().position());
    }

    /**
     * 获取 SubLevel 的当前质心位置 (即 {@code logicalPose().position()})。
     *
     * @param subLevel SubLevel
     * @return 质心世界坐标
     */
    public static Vector3d getCenterOfMass(ServerSubLevel subLevel) {
        return new Vector3d(subLevel.logicalPose().position());
    }

    /**
     * 获取 SubLevel 的当前线速度 (即 {@code latestLinearVelocity})。
     *
     * @param subLevel SubLevel
     * @return 线速度
     */
    public static Vector3d getLinearVelocity(ServerSubLevel subLevel) {
        return new Vector3d(subLevel.latestLinearVelocity);
    }

    /**
     * 获取 SubLevel 的当前角速度 (即 {@code latestAngularVelocity})。
     *
     * @param subLevel SubLevel
     * @return 角速度
     */
    public static Vector3d getAngularVelocity(ServerSubLevel subLevel) {
        return new Vector3d(subLevel.latestAngularVelocity);
    }

    // ============================================================
    //  坐标系转换
    // ============================================================

    /**
     * 将世界坐标系方向上的向量转换到刚体局部坐标系。
     *
     * @param subLevel SubLevel (用于读取朝向)
     * @param worldVec 世界坐标系的向量
     * @return 局部坐标系下的向量
     */
    public static Vector3d worldToLocal(ServerSubLevel subLevel, Vector3dc worldVec) {
        return subLevel.logicalPose().orientation().transformInverse(worldVec, new Vector3d());
    }

    /**
     * 将刚体局部坐标系方向上的向量转换到世界坐标系。
     *
     * @param subLevel SubLevel (用于读取朝向)
     * @param localVec 局部坐标系下的向量
     * @return 世界坐标系下的向量
     */
    public static Vector3d localToWorld(ServerSubLevel subLevel, Vector3dc localVec) {
        return subLevel.logicalPose().orientation().transform(localVec, new Vector3d());
    }
}
