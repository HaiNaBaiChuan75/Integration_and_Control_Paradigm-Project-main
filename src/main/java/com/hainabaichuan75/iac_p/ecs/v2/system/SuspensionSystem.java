package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 悬挂 System (V2) —— 3 条弧面射线检测地面，更新悬挂压缩量与接触点。
 * <p>
 * <b>与 V1 区别</b>：
 * <ul>
 *   <li>压缩量公式修正为通用 3D 几何，支持任意安装轴向</li>
 *   <li>挂载 & 悬挂参数来自 {@link WheelDef} 组件</li>
 * </ul>
 */
public class SuspensionSystem implements TickSystem {

    private static final double SIN45 = 0.7071067811865476;
    private static final double COS45 = SIN45;
    private static final Vector3dc LOCAL_UP = new Vector3d(0, 1, 0);
    private static final Vector3dc LOCAL_DOWN = new Vector3d(0, -1, 0);

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var wd = defView.get();
            var ws = stateView.get();

            // 轮毂世界坐标
            Vector3dc hubWorld = computeHubWorld(subLevel, stateView.part(), wd.mountPoint());

            // 三条射线方向
            Vector3dc[] localDirs = computeRayDirections(wd.mountDirection());

            double bestCompression = 0.0;
            Vector3d bestLocalContact = null;
            boolean anyHit = false;

            for (Vector3dc localDir : localDirs) {
                Vector3d worldDir = subLevel.logicalPose().transformNormal(new Vector3d(localDir));

                var start = new Vec3(hubWorld.x(), hubWorld.y(), hubWorld.z());
                var end = new Vec3(hubWorld.x() + worldDir.x * wd.radius(), hubWorld.y() + worldDir.y * wd.radius(),
                        hubWorld.z() + worldDir.z * wd.radius());

                var ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        CollisionContext.empty());
                BlockHitResult hit = subLevel.getLevel().clip(ctx);

                if (hit.getType() == HitResult.Type.BLOCK) {
                    double hitDist = start.distanceTo(hit.getLocation());
                    if (hitDist < wd.radius()) {
                        // 通用 3D 压缩量：compression = √(R² - d²·(x²+z²)) + d·y
                        double hSq = hitDist * hitDist;
                        double sinSqTheta = localDir.x() * localDir.x() + localDir.z() * localDir.z();
                        double compression =
                                Math.sqrt(wd.radius() * wd.radius() - hSq * sinSqTheta) + localDir.y() * hitDist;
                        if (compression > bestCompression) {
                            bestCompression = compression;
                            bestLocalContact = new Vector3d(localDir).mul(hitDist);
                            anyHit = true;
                        }
                    }
                }
            }

            stateView.set(new WheelState(ws.angularVelocity(), bestCompression, ws.steeringAngle(), ws.torque(),
                    ws.braking(), anyHit ? bestLocalContact : null));
        }
    }

    // ==================================================================
    //  射线方向：轴向 × 天向平面，间距 45°
    // ==================================================================

    private static Vector3dc[] computeRayDirections(Vector3dc axial) {
        Vector3dc center = LOCAL_DOWN;
        Vector3d left = new Vector3d(axial).mul(SIN45).add(new Vector3d(LOCAL_UP).mul(-COS45));
        Vector3d right = new Vector3d(axial).mul(-SIN45).add(new Vector3d(LOCAL_UP).mul(-COS45));
        return new Vector3dc[]{left, center, right};
    }

    // ==================================================================
    //  轮毂世界坐标：blockCenter + mountPoint → SubLevel 变换
    // ==================================================================

    @NotNull
    private static Vector3dc computeHubWorld(@NotNull ServerSubLevel subLevel, @NotNull Part part,
                                             @NotNull Vector3dc mountPoint) {
        Vector3d local = JOMLConversion.atCenterOf(part.getBlockEntity().getBlockPos());
        local.add(mountPoint.x(), mountPoint.y(), mountPoint.z());
        return subLevel.logicalPose().transformPosition(local, new Vector3d());
    }
}
