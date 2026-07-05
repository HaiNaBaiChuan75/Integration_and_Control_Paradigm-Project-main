package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.WheelPart;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 悬挂 System —— 通过 3 条弧面射线检测地面，更新悬挂压缩量。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>从轮毂沿 3 条方向（正下、左斜下 45°、右斜下 45°）发射射线</li>
 *   <li>射线长度为轮半径，模拟轮子弧面接触检测</li>
 *   <li>取压缩量最大的射线结果，写入 {@link WheelPart#setSuspensionCompression(double)}</li>
 *   <li>将最佳接触点写入 {@link WheelPart#setContactPointLocal(Vector3dc)}</li>
 * </ol>
 * <p>
 * <b>射线计算</b>：三条射线在 <b>轴向 × 天向</b> 平面内，由
 * {@link WheelPart#getAxialNormal()} 动态推导，无需硬编码方向常量。
 * 射线方向随车轮转向自动调整：
 * <pre>
 *   轴向 = getAxialNormal()  (SubLevel 局部空间)
 *   天向 = (0, 1, 0)
 *
 *   正下   = -天向
 *   左斜下 = 轴向 × sin45° - 天向 × cos45°
 *   右斜下 = -轴向 × sin45° - 天向 × cos45°
 * </pre>
 * <p>
 * <b>压缩量算法</b>：对每条命中射线，使用余弦定理计算垂直穿透量：
 * {@code compression = √(R² - d²·sin²θ) - d·cosθ}，
 * 取最大值。其中 θ 为射线方向与垂直方向的夹角，d 为命中距离。
 * 直下射线（θ=0°）退化为 {@code R - d}；斜向射线因圆几何，
 * 实际垂直穿透大于简单投影 {@code (R-d)·cosθ}。
 * <p>
 * <b>为什么用 20Hz 逻辑 tick 而非物理 tick</b>：
 * 地面高度在 1/20 秒内不会剧烈变化，20Hz 的射线检测足以捕捉悬挂压缩变化。
 * 相比物理步进（~100Hz），可节省约 80% 的射线调用。
 */
public class SuspensionSystem implements VehicleTickSystem {

    /**
     * sin(45°)
     */
    private static final double SIN45 = 0.7071067811865476;
    /**
     * cos(45°)
     */
    private static final double COS45 = SIN45;

    /**
     * 天向单位向量（悬挂沿 Y 轴运动）
     */
    private static final Vector3dc LOCAL_UP = new Vector3d(0, 1, 0);
    /**
     * 正下方方向
     */
    private static final Vector3dc LOCAL_DOWN = new Vector3d(0, -1, 0);

    @Override
    public void onTick(ServerSubLevel subLevel, List<? extends Part> parts) {
        for (Part part : parts) {
            if (part instanceof WheelPart wheel) {
                updateCompression(subLevel, wheel);
            }
        }
    }

    /**
     * 对单个轮子执行 3 条弧面射线扫描，更新悬挂压缩量并写入最佳接触点。
     * <p>
     * 射线方向从 {@link WheelPart#getAxialNormal()} 推导，自动适应车轮转向。
     *
     * @param subLevel 当前 SubLevel
     * @param wheel    目标轮子
     */
    private static void updateCompression(ServerSubLevel subLevel, WheelPart wheel) {
        // ── 1. 轮毂世界坐标 ─────────────────────────────────────
        Vector3dc hub = wheel.getSuspensionAttachmentInWorld();
        double radius = wheel.getRadius();

        // ── 2. 从轴向推导三条射线方向（SubLevel 局部空间） ─────
        Vector3dc axial = wheel.getAxialNormal();
        Vector3dc[] localDirs = computeRayDirections(axial);

        // ── 3. 局部→世界方向变换 ───────────────────────────────

        double bestCompression = 0.0;
        Vector3d bestLocalContact = null;
        boolean anyHit = false;

        // ── 4. 扫描 3 条射线 ────────────────────────────────────
        for (Vector3dc localDir : localDirs) {
            // 局部方向 → 世界方向
            Vector3d worldDir = subLevel.logicalPose().transformNormal(new Vector3d(localDir));

            // 射线起止点 = 轮毂 ~ 轮毂 + 方向 × 半径
            double ex = hub.x() + worldDir.x * radius;
            double ey = hub.y() + worldDir.y * radius;
            double ez = hub.z() + worldDir.z * radius;

            var start = new Vec3(hub.x(), hub.y(), hub.z());
            var end = new Vec3(ex, ey, ez);

            // 执行射线检测
            var ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty());
            BlockHitResult hit = subLevel.getLevel().clip(ctx);

            if (hit.getType() == HitResult.Type.BLOCK) {
                double hitDist = start.distanceTo(hit.getLocation());
                if (hitDist < radius) {
                    // compression = √(R² - d²·sin²θ) - d·cosθ
                    //              = √(R² - d²·D.x²) + d·D.y  (D.y < 0)
                    // 余弦定理：轮子为圆，斜向接触时垂直穿透由圆方程确定
                    double hSq = hitDist * hitDist;
                    double compression =
                            Math.sqrt(radius * radius - hSq * localDir.x() * localDir.x()) + localDir.y() * hitDist;
                    if (compression > bestCompression) {
                        bestCompression = compression;
                        // 接触点在局部空间 = localDir × hitDist
                        bestLocalContact = new Vector3d(localDir).mul(hitDist);
                        anyHit = true;
                    }
                }
            }
        }

        // ── 5. 写入结果 ─────────────────────────────────────────
        wheel.setSuspensionCompression(bestCompression);
        wheel.setContactPointLocal(anyHit ? bestLocalContact : null);
    }

    // ==================================================================
    //  射线方向计算
    // ==================================================================

    /**
     * 根据轴向推导三条弧面射线方向（SubLevel 局部空间）。
     * <p>
     * 方向在 <b>轴向 × 天向</b> 平面内，间距 45°，覆盖车轮底部弧面。
     *
     * @param axial 车轮轴向（单位向量）
     * @return 三条射线方向数组 [左斜下, 正下, 右斜下]
     */
    private static Vector3dc[] computeRayDirections(Vector3dc axial) {
        // 正下 = -天向
        Vector3dc center = LOCAL_DOWN;

        // 左斜下 = axial × sin45 - 天向 × cos45
        Vector3d left = new Vector3d(axial).mul(SIN45).add(new Vector3d(LOCAL_UP).mul(-COS45));

        // 右斜下 = -axial × sin45 - 天向 × cos45
        Vector3d right = new Vector3d(axial).mul(-SIN45).add(new Vector3d(LOCAL_UP).mul(-COS45));

        return new Vector3dc[]{left, center, right};
    }
}
