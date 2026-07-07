package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.PhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import com.hainabaichuan75.iac_p.ecs.v2.component.rotation.PartTransform;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 悬挂 System (V2) —— 3 条弧面射线检测地面，更新悬挂压缩量与接触点。
 * <p>
 * <b>坐标系策略</b>：
 * <ul>
 *   <li>射线检测 → 世界空间（Minecraft {@code clip()} 的要求）</li>
 *   <li>检测完后立即 {@link PartTransform#toRelativePos 拉回 Part 局部空间}</li>
 *   <li>压缩计算 → 全部在 Part 局部空间一致完成，不再混框</li>
 * </ul>
 */
public class SuspensionSystem implements PhysicsSystem {

    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts,
                              @NotNull RigidBodyHandle handle, double timeStep) {
        // ── 跨轮子共享的缓冲区（循环内只写不读，不必每轮子分配） ──
        Vector3d hubWorld = new Vector3d();
        Vector3d worldDir = new Vector3d();
        Vector3d rayLeft = new Vector3d();
        Vector3d rayCenter = new Vector3d();
        Vector3d rayRight = new Vector3d();
        Vector3d hitWorldBuf = new Vector3d();
        Vector3d localHitBuf = new Vector3d();
        Vector3d relVec = new Vector3d();
        Vector3d bestLocalContact = new Vector3d();
        Vector3d wheelCenterLocal = new Vector3d();
        Vector3dc[] rayDirs = new Vector3dc[]{rayLeft, rayCenter, rayRight};

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var wd = defView.get();
            var ws = stateView.get();

            var tx = PartTransform.of(stateView.part());

            // ── Option B：轮心随悬挂动 ──────────────────────────────
            // 轮心(局部) = mountPoint − 压缩量 × 悬挂方向
            // 从 mountPoint 出发，每压缩 1m 轮心沿悬挂方向反向下移 1m。
            // 压缩量 = ws.suspensionCompression() 来自上一帧，正好是
            // "实现检测结果前的 compression" = prevCompression。
            wheelCenterLocal.set(wd.mountPoint());
            wheelCenterLocal.fma(-ws.suspensionCompression(), wd.suspensionDirection());

            // 轮心世界坐标（射线起点）
            tx.fromRelativePos(wheelCenterLocal, hubWorld);

            // 三条射线方向（Part 局部空间，方向量，单位长度）
            computeRayDirections(wd.mountDirection(), wd.suspensionDirection(), rayLeft, rayCenter, rayRight);

            double bestCompression = 0.0;
            boolean anyHit = false;

            // 射线起点（世界空间，三条射线共用——均为轮心）
            var start = new Vec3(hubWorld.x, hubWorld.y, hubWorld.z);

            for (Vector3dc localDir : rayDirs) {
                // 局部方向 → 世界方向（复用 worldDir）
                tx.fromRelativeNormal(localDir, worldDir);

                double R = wd.radius();
                var end = new Vec3(hubWorld.x + worldDir.x * R, hubWorld.y + worldDir.y * R,
                        hubWorld.z + worldDir.z * R);

                BlockHitResult hit = subLevel.getLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, CollisionContext.empty()));

                if (hit.getType() == HitResult.Type.BLOCK) {
                    // ★ 命中点从世界 → Part 局部空间（复用 hitWorldBuf / localHitBuf）
                    Vec3 hitLoc = hit.getLocation();
                    hitWorldBuf.set(hitLoc.x, hitLoc.y, hitLoc.z);
                    tx.toRelativePos(hitWorldBuf, localHitBuf);

                    // 轮心 → 命中点的局部偏移向量
                    relVec.set(localHitBuf).sub(wheelCenterLocal);
                    double dSq = relVec.lengthSquared();
                    double RSq = R * R;

                    // 只在命中点在轮子球面内部时计算压缩
                    if (dSq < RSq) {
                        // 沿悬挂方向的投影 = 轮心到地面的垂直距离
                        // compression = R + 垂直分量
                        // 垂直分量为负（地面在轮心下方），所以 compression = R − |垂直分量|
                        double alongSusp = relVec.dot(wd.suspensionDirection());
                        double compression = R + alongSusp;

                        if (compression > bestCompression) {
                            bestCompression = compression;
                            bestLocalContact.set(localHitBuf);
                            anyHit = true;
                        }
                    }
                }
            }

            stateView.set(ws.withCompressionUpdate(bestCompression, anyHit ? bestLocalContact : null));
        }
    }

    // ==================================================================
    //  射线方向：以悬挂方向为轴心，绕轮轴 ±45° 扇形展开
    //  全部在 Part 局部空间，单位向量
    // ==================================================================

    private static void computeRayDirections(@NotNull Vector3dc mountDirection,
                                             @NotNull Vector3dc suspensionDirection, @NotNull Vector3d destLeft,
                                             @NotNull Vector3d destCenter, @NotNull Vector3d destRight) {
        // 中心射线 = 悬挂方向的反向（指向地面）
        destCenter.set(suspensionDirection).negate();

        double angleRad = Math.toRadians(45);
        new Quaterniond().rotateAxis(angleRad, mountDirection).transform(destCenter, destLeft);
        new Quaterniond().rotateAxis(-angleRad, mountDirection).transform(destCenter, destRight);
    }
}
