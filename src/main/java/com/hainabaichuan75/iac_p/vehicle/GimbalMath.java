package com.hainabaichuan75.iac_p.vehicle;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 云台数学 —— 纯工具，无数据。
 */
public class GimbalMath {

    public static record YawPitch(double yaw, double pitch) {}

    /**
     * 将世界空间目标方向转为局部 yaw/pitch。
     */
    public static YawPitch aimAt(Vector3dc targetDir) {
        if (targetDir.lengthSquared() < 1e-10) return new YawPitch(0, 0);
        double yaw = Math.atan2(targetDir.x(), -targetDir.z());
        double horizontal = Math.sqrt(targetDir.x() * targetDir.x() + targetDir.z() * targetDir.z());
        double pitch = -Math.atan2(targetDir.y(), horizontal);
        return new YawPitch(yaw, pitch);
    }

    public static YawPitch aimAt(Vector3dc targetPos, Vector3dc origin, Quaterniondc orientation) {
        var local = new Vector3d(targetPos).sub(origin);
        new Quaterniond(orientation).conjugate().transform(local);
        return aimAt(local);
    }

    public static double clamp(double angle, double min, double max) {
        return Math.clamp(angle, min, max);
    }
}
