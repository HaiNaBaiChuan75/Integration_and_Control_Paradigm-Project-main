package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.block.suspension_test.BrakeHandler;
import com.hainabaichuan75.iac_p.block.suspension_test.CollisionHandler;
import com.hainabaichuan75.iac_p.block.suspension_test.TirePhysicsCalculator;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.PhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.ControlState;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import com.hainabaichuan75.iac_p.ecs.v2.component.rotation.PartTransform;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.List;

/**
 * Old-Style 轮子物理 System (V2) — 复刻 main 分支 SuspensionTestBlockEntity 的完整物理管线。
 *
 * <h3>坐标约定</h3>
 * <ul>
 *   <li><b>forcePos</b> — 世界坐标（Minecraft Level）</li>
 *   <li><b>fwdD / sideD</b> — SubLevel 局部空间方向，由 facing 推导，再按转向角绕 Y 旋转</li>
 *   <li><b>lv（局部速度）</b> — {@code pose.transformNormalInverse(worldVel)}</li>
 *   <li><b>弹簧力</b> — 竖向冲量沿 hit.normal 分解，经 SubLevel pose 变换后回到 SubLevel 局部</li>
 *   <li><b>水平力</b> — 全部在 SubLevel 局部空间计算，力方向与世界空间的 forcePos 一并施加</li>
 * </ul>
 *
 * <h3>与旧版的对应关系</h3>
 * <pre>
 * main 分支                                   OldWheelSystem
 * ────────────────────────────────────────    ──────────────────────
 * extension                                  WheelState.suspensionCompression
 * chasingYaw                                 WheelState.steeringAngle (°→rad)
 * torqueInput                                WheelState.torque
 * braking                                    WheelState.braking
 * CollisionHandler.rayTerrain()              CollisionHandler.rayTerrain()（直接调用）
 * TireLike.radius()                          WheelDef.radius
 * TIRE_FRICTION_COEFFICIENT                  WheelDef.gripForward（纵向）
 * </pre>
 */
public class OldWheelSystem implements PhysicsSystem {

    // ═══════════════════════════════════════════════════════════════════
    //  弹簧参数（质量自适应：由 System 自行计算）
    // ═══════════════════════════════════════════════════════════════════
    private static final double SPRING_STIFFNESS_PER_NM = 400.0;
    private static final double DAMPING_COEFF_PER_NM = 10.0;
    private static final double SUSPENSION_MASS_THRESHOLD = 5.0;
    private static final double MAX_EXT = 0.65;

    // ═══════════════════════════════════════════════════════════════════
    //  摩擦 / 刹车 / 转向参数（对齐 SuspensionConstants 3.0 文档）
    // ═══════════════════════════════════════════════════════════════════
    /** 轮胎摩擦系数（替代 WheelDef.gripForward/gripLateral） */
    private static final double TIRE_FRICTION_COEFFICIENT = 1.2;
    private static final double MIN_IMPULSE_MULTIPLIER = 30;
    private static final double BRAKE_STRENGTH = 0.5;
    private static final double SIDE_SLIP_DAMPING = 6.0;
    private static final double DIFFERENTIAL_RATIO = 0.37;
    private static final double HALF_TRACK = 1.0;

    // ═══════════════════════════════════════════════════════════════════
    //  轮胎材料常数（对齐 SuspensionConstants 3.0 文档）
    // ═══════════════════════════════════════════════════════════════════
    private static final double NOMINAL_PRESSURE = 220000.0;
    private static final double TREAD_WIDTH = 0.25;
    private static final double MAX_PRESSURE = 350000.0;
    private static final double DEFAULT_CRR_BASE = 0.015;
    private static final double DEFAULT_CRR_DEFORMATION_GAIN = 0.08;

    // ═══════════════════════════════════════════════════════════════════
    //  PART 朝向解析
    // ═══════════════════════════════════════════════════════════════════

    @NotNull
    private static Orientation resolve(@NotNull Part part) {
        Quaterniondc q = PartTransform.resolveOrientation(part);
        return new Orientation(quatToForward(q), q);
    }

    /** 局部 Z- → 世界 Direction（就近匹配 6 轴） */
    @NotNull
    private static Direction quatToForward(@NotNull Quaterniondc q) {
        var v = new Vector3d(0, 0, -1);
        q.transform(v);
        return Direction.getNearest(v.x(), v.y(), v.z());
    }

    private record Orientation(@NotNull Direction forward, @NotNull Quaterniondc quaternion) {
        @NotNull Vector3d fwd() { var v = new Vector3d(0, 0, -1); quaternion.transform(v); return v; }
        @NotNull Vector3d side() { var v = new Vector3d(1, 0, 0); quaternion.transform(v); return v; }
        @NotNull Vector3d up() { var v = new Vector3d(0, 1, 0); quaternion.transform(v); return v; }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PhysicsSystem
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onPhysicsTick(@NotNull dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel,
                              @NotNull List<? extends Part> parts,
                              @NotNull RigidBodyHandle handle, double dt) {

        Pose3dc pose = subLevel.logicalPose();
        MassData md = subLevel.getMassTracker();
        Level level = subLevel.getLevel();

        // ── 全局刹车（来自 ControlState） ──
        View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
        boolean globalBraking = cv != null && cv.get().braking();

        // ── 跨轮共享缓冲 ──
        ForceTotal forces = new ForceTotal();
        Vector3d forcePos = new Vector3d();
        Vector3d forceVec = new Vector3d();
        Vector3d fwdBuf = new Vector3d();
        Vector3d sideBuf = new Vector3d();
        Vector3d upBuf = new Vector3d();
        Quaterniond steerRot = new Quaterniond();

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var wd = defView.get();
            var ws = stateView.get();

            double rad = wd.radius();
            if (rad <= 0) continue;

            var part = stateView.part();
            BlockPos bp = part.getBlockEntity().getBlockPos();

            // ── 朝向解析（Rotation → CubeRotation → HORIZONTAL_FACING → NORTH） ──
            var orient = resolve(part);
            orient.fwd().div(orient.fwd().length(), fwdBuf);
            orient.side().div(orient.side().length(), sideBuf);
            orient.up().div(orient.up().length(), upBuf);

            // ── 1. 施力点（世界坐标，沿 world-grid forward 偏移一格） ──
            Vec3 lp = bp.relative(orient.forward()).getCenter();
            forcePos.set(lp.x, lp.y, lp.z);

            // ── 2. 转向角（绕 Part 的 up 轴旋转力方向） ──
            double chasingYaw = Math.toRadians(ws.steeringAngle());
            if (Math.abs(chasingYaw) > 1e-8) {
                steerRot.rotateAxis(chasingYaw, upBuf);
                steerRot.transform(fwdBuf);
                steerRot.transform(sideBuf);
                steerRot.identity();
            }

            // ── 3. 质量自适应弹簧 ──
            double nm = 1.0 / md.getInverseNormalMass(forcePos, new Vector3d(0, 1, 0));
            double limitedNm = Math.min(nm, SUSPENSION_MASS_THRESHOLD);
            double springK = limitedNm * SPRING_STIFFNESS_PER_NM;
            double dampingC = limitedNm * DAMPING_COEFF_PER_NM;

            // ── 4. rayTerrain ──
            var terr = CollisionHandler.rayTerrain(level, bp, orient.forward(),
                    fwdBuf, pose, subLevel);

            double me = terr.maxExtension();
            double ext = ws.suspensionCompression();

            // ── 5. 平滑 extension（对齐旧版 lerp） ──
            me = Mth.lerp(1.0, ext, me);

            if (me > MAX_EXT + rad + 0.25) {
                stateView.set(ws.withCompression(MAX_EXT));
                continue;
            }

            // ── 6. 弹簧长度与压缩量 ──
            double d = (MAX_EXT / 6.0) + me;
            double slen = Mth.clamp(d - rad, 0.0, MAX_EXT);

            // ── 7. SubLevel 局部速度 ──
            Vector3d vel = Sable.HELPER.getVelocity(level, new Vector3d(forcePos));
            Vector3d lv = pose.transformNormalInverse(vel);

            // ── 8. 弹簧力（垂直方向） ──
            double df = -lv.y * dampingC;
            double sf = ((MAX_EXT - slen) * springK + df) * dt;

            Vec3i hn = terr.normal().getNormal();
            Vec3 lf = new Vec3(sf * hn.getX(), sf * hn.getY(), sf * hn.getZ());
            if (terr.subLevel() != null) {
                lf = terr.subLevel().logicalPose().transformNormal(lf);
            }
            lf = pose.transformNormalInverse(lf);
            forceVec.set(lf.x, lf.y, lf.z);

            // ── 9. 摩擦圆模型 ──
            double touchFriction;
            if (terr.minInteractingBlock() != null) {
                touchFriction = CollisionHandler.fudgeGroundFriction(
                        PhysicsBlockPropertyHelper.getFriction(
                                level.getBlockState(terr.minInteractingBlock())));
            } else {
                touchFriction = 1.0;
            }

            double mu = TIRE_FRICTION_COEFFICIENT * touchFriction;
            double springImpulse = Math.abs((MAX_EXT - slen) * springK * dt);
            double minImpulse = nm * dt * MIN_IMPULSE_MULTIPLIER;
            double frictionBasis = Math.max(springImpulse, minImpulse);
            double frictionBudget = mu * frictionBasis;

            double forwardSpeed = lv.dot(fwdBuf);
            double lateralSpeed = lv.dot(sideBuf);
            double longForce = 0;
            double latForce = 0;

            boolean braking = globalBraking || ws.braking();

            if (braking) {
                // ── 手刹：沿总速度反方向滑动摩擦 ──
                var brakeResult = BrakeHandler.compute(
                        forwardSpeed, lateralSpeed, mu, springImpulse, BRAKE_STRENGTH);
                longForce = brakeResult.longForce();
                latForce = brakeResult.latForce();
            } else {
                // ── Binary Grip 驱动 ──
                double torque = ws.torque();
                double driveImpulse = torque / Math.max(rad, 0.01) * dt;
                double absDrive = Math.abs(driveImpulse);
                double maxGripImpulse = mu * frictionBasis;
                double actualImpulse = absDrive <= maxGripImpulse
                        ? driveImpulse
                        : Math.signum(driveImpulse) * maxGripImpulse;

                // ── 差速器扭矩偏置 ──
                double normX = Mth.clamp(wd.mountPoint().x() / HALF_TRACK, -1.0, 1.0);
                double diffFactor = 1.0 + chasingYaw * normX * DIFFERENTIAL_RATIO;
                diffFactor = Mth.clamp(diffFactor, 0.5, 1.5);
                longForce += actualImpulse * diffFactor;

                // ── 滚动阻力（轮胎形变模型） ──
                double normalForce = springImpulse / dt;
                double tireDeflection = 0.0;
                if (normalForce > 0) {
                    var deflect = TirePhysicsCalculator.calculateTireDeflection(
                            normalForce, NOMINAL_PRESSURE, TREAD_WIDTH, rad);
                    tireDeflection = deflect.tireDeflection();
                }
                var rr = TirePhysicsCalculator.calculateRollingResistance(
                        forwardSpeed, nm, dt, tireDeflection, rad,
                        NOMINAL_PRESSURE, NOMINAL_PRESSURE /* 无亏气 */,
                        DEFAULT_CRR_BASE, DEFAULT_CRR_DEFORMATION_GAIN);
                longForce += rr.rrForce();

                // ── 静态摩擦锁止（仅无驱动力矩时锁止零速漂移） ──
                if (Math.abs(forwardSpeed) < 0.1 && Math.abs(torque) < 1e-6) {
                    double staticMax = (mu + DEFAULT_CRR_BASE) * normalForce * dt;
                    if (Math.abs(longForce) < staticMax) {
                        longForce = 0;
                    }
                }

                // ── 侧滑阻尼 ──
                double gripBudget = mu * frictionBasis;
                double latDemand = -lateralSpeed * SIDE_SLIP_DAMPING * nm * dt;
                latForce = Mth.clamp(latDemand, -gripBudget, gripBudget);
            }

            // ── 10. 摩擦圆约束 ──
            double totalDemand = Math.sqrt(longForce * longForce + latForce * latForce);
            if (totalDemand > frictionBudget && totalDemand > 1e-10) {
                double scale = frictionBudget / totalDemand;
                longForce *= scale;
                latForce *= scale;
            }

            // ── 11. 施加水平力（SubLevel 局部） ──
            forceVec.fma(longForce, fwdBuf);
            forceVec.fma(latForce, sideBuf);

            // ── 12. 更新 State（存储弹簧压缩量 slen 而非射线距离 me） ──
            double newAngVel = forwardSpeed / rad;
            stateView.set(new WheelState(
                    newAngVel, slen,
                    ws.steeringAngle(), ws.torque(), ws.braking(),
                    null,
                    slen - ext));

            forces.applyImpulseAtPoint(subLevel, forcePos, forceVec);
            forceVec.zero();
        }

        handle.applyForcesAndReset(forces);
    }
}
