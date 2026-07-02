package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.part.WheelPart;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 悬挂物理 System —— 弹簧/阻尼力、轮胎摩擦圆、Binary Grip 抓地、差速器扭矩偏置。
 * <p>
 * 来源：{@link SuspensionTestBlockEntity#sable$physicsTick(ServerSubLevel, RigidBodyHandle, double)}
 * 中的悬挂物理逻辑（~400 行）。
 * <p>
 * <b>数据流</b>：
 * <ol>
 *   <li>读取 {@link WheelPart#getTorqueInput()}（从 EnginePowerSystem 上一逻辑 tick 写入）</li>
 *   <li>读取 {@link WheelPart#getSteeringAngle()}（从 SteeringSystem 写入）</li>
 *   <li>弹簧/阻尼/摩擦力计算，施加到刚体</li>
 *   <li>写回物理状态字段（currentWheelRpm 等）</li>
 * </ol>
 * <p>
 * <b>设计说明</b>：当前版本对物理内部状态（悬挂伸展量、抓地状态等）使用具体
 * {@link SuspensionTestBlockEntity} 类型直接访问。跨 System 数据（扭矩、转向角）
 * 通过 {@link WheelPart} 接口传递。后续可将物理状态也抽象到接口中。
 */
public class SuspensionPhysicsSystem implements VehiclePhysicsSystem {

    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts,
                               @NotNull RigidBodyHandle handle, double dt) {
     /*   // 提取 EnginePart 引用（供运动学约束）
        EnginePart engine = null;
        BlockPos cockpitPos = null;
        for (Part p : parts) {
            if (p instanceof EnginePart ep) {
                engine = ep;
                cockpitPos = p.getBlockPos();
                break;
            }
        }

        for (Part part : parts) {
            if (!(part instanceof WheelPart)) continue;
            if (!(part instanceof SuspensionTestBlockEntity sw)) continue;

            // ── 读取接口数据 ──
            double torqueInput = ((WheelPart) sw).getTorqueInput();
            double steeringAngle = ((WheelPart) sw).getSteeringAngle();
            boolean braking = sw.isBraking();
            boolean throttleForward = sw.isThrottleForward();
            boolean throttleBackward = sw.isThrottleBackward();

            // ── 读取物理输入 ──
            ItemStack heldItem = sw.getHeldItem();
            TireLike tire = heldItem.get(OffroadDataComponents.TIRE);
            if (tire == null) continue;

            BlockPos bp = part.getBlockPos();
            float rad = tire.radius();
            double rest = MAX_EXT;
            MassData md = subLevel.getMassTracker();
            BlockState blockState = sw.getBlockState();
            Direction f = blockState.getValue(SuspensionTestBlock.HORIZONTAL_FACING);
            Vec3 lp = bp.relative(f).getCenter();
            Vector3d forcePos = new Vector3d(lp.x, lp.y, lp.z);

            double nm = 1.0 / md.getInverseNormalMass(forcePos, new Vector3d(0, 1, 0));
            double limitedNm = Math.min(nm, SUSPENSION_MASS_THRESHOLD);
            double springK = limitedNm * SPRING_STIFFNESS_PER_NM;
            double dampingC = limitedNm * DAMPING_COEFF_PER_NM;

            Pose3dc pose = subLevel.logicalPose();
            Direction.Axis axis = f.getAxis();

            // 转向方向的力矢量旋转
            Vector3dc sideD, fwdD;
            if (Math.abs(steeringAngle) > 1e-8) {
                Vector3d baseSide = axis == Direction.Axis.X
                        ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
                Vector3d baseFwd = axis == Direction.Axis.X
                        ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
                sideD = baseSide.rotateY(steeringAngle);
                fwdD = baseFwd.rotateY(steeringAngle);
            } else {
                sideD = axis == Direction.Axis.X ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
                fwdD = axis == Direction.Axis.X ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
            }

            // 轮位计算
            double localPosX = 0, localPosZ = 0;
            if (cockpitPos != null) {
                double worldDx = bp.getX() - cockpitPos.getX();
                double worldDz = bp.getZ() - cockpitPos.getZ();
                localPosZ = worldDx * fwdD.x() + worldDz * fwdD.z();
                localPosX = worldDx * sideD.x() + worldDz * sideD.z();
            }

            // 地形碰撞
            var terr = CollisionHandler.rayTerrain(
                    subLevel.getLevel(), bp, f, fwdD, pose, sw.getSubLevel());
            double me = terr.maxExtension();
            sw.setExtension(Mth.lerp(1.0, sw.getExtension(), me));
            if (me > rest + rad + 0.25) {
                sw.setExtension(rest);
                continue;
            }

            double d = (rest / 6.0) + sw.getExtension();
            double slen = Mth.clamp(d - rad, 0.0, rest);
            Vector3d vel = Sable.HELPER.getVelocity(subLevel.getLevel(), JOMLConversion.toJOML(lp));
            Vector3d lv = pose.transformNormalInverse(vel);

            double df = -lv.y * dampingC;
            double sf = ((rest - slen) * springK + df) * dt;

            Vec3i hn = terr.normal().getNormal();
            Vec3 lf = new Vec3(sf * hn.getX(), sf * hn.getY(), sf * hn.getZ());
            if (terr.subLevel() != null) {
                lf = terr.subLevel().logicalPose().transformNormal(lf);
            }
            lf = pose.transformNormalInverse(lf);
            Vector3d forceVec = new Vector3d(lf.x, lf.y, lf.z);

            // ===== 摩擦圆模型 =====
            {
                double touchFriction;
                if (terr.minInteractingBlock() != null) {
                    touchFriction = CollisionHandler.fudgeGroundFriction(
                            PhysicsBlockPropertyHelper.getFriction(
                                    subLevel.getLevel().getBlockState(terr.minInteractingBlock())));
                } else {
                    touchFriction = 1.0;
                }

                double mu = TIRE_FRICTION_COEFFICIENT * touchFriction;
                double springImpulse = Math.abs((rest - slen) * springK * dt);
                double minImpulse = nm * dt * MIN_IMPULSE_MULTIPLIER;
                double frictionBasis = Math.max(springImpulse, minImpulse);
                double frictionBudget = mu * frictionBasis;

                double forwardSpeed = lv.dot(fwdD);
                double lateralSpeed = lv.dot(sideD);
                double longForce = 0;
                double latForce = 0;

                double nominalPressure = sw.getNominalPressure();
                double effectivePressure = nominalPressure;
                double tireDeflection = 0;

                if (braking) {
                    var brakeResult = BrakeHandler.compute(
                            forwardSpeed, lateralSpeed, mu, springImpulse, BRAKE_STRENGTH);
                    longForce = brakeResult.longForce();
                    latForce = brakeResult.latForce();
                } else {
                    // Binary Grip 直驱
                    double torqueMag = Math.abs(torqueInput);
                    double direction = 0;
                    if (throttleForward && !throttleBackward) direction = 1.0;
                    else if (throttleBackward && !throttleForward) direction = -1.0;

                    double signedTorque = torqueMag * direction;
                    double driveForceN = signedTorque / Math.max(rad, 0.01);
                    double driveImpulse = driveForceN * PowertrainConstants.DT;
                    double maxGripImpulse = mu * frictionBasis;
                    double absDriveImpulse = Math.abs(driveImpulse);
                    boolean gripStatus = absDriveImpulse <= maxGripImpulse;
                    double actualImpulse = gripStatus
                            ? driveImpulse
                            : Math.signum(driveImpulse) * maxGripImpulse;

                    // 差速器
                    double normX = Mth.clamp(localPosX / HALF_TRACK, -1.0, 1.0);
                    double diffFactor = 1.0 + steeringAngle * normX * DIFFERENTIAL_RATIO;
                    diffFactor = Mth.clamp(diffFactor, 0.5, 1.5);
                    longForce += actualImpulse * diffFactor;

                    // 滚动阻力
                    double normalForce = springImpulse / dt;
                    if (tire != null && normalForce > 0) {
                        var deflectionResult = TirePhysicsCalculator.calculateTireDeflection(
                                normalForce, nominalPressure, DEFAULT_TREAD_WIDTH, rad);
                        tireDeflection = deflectionResult.tireDeflection();
                        effectivePressure = deflectionResult.effectivePressure();
                    }
                    var rrResult = TirePhysicsCalculator.calculateRollingResistance(
                            forwardSpeed, nm, dt, tireDeflection, rad,
                            nominalPressure, effectivePressure,
                            DEFAULT_CRR_BASE, DEFAULT_CRR_DEFORMATION_GAIN);
                    longForce += rrResult.rrForce();

                    // 静态摩擦锁止
                    if (Math.abs(forwardSpeed) < 0.1) {
                        double staticMax = (mu + DEFAULT_CRR_BASE) * normalForce * dt;
                        if (Math.abs(longForce) < staticMax) longForce = 0;
                    }

                    // 横向摩擦
                    double gripBudget = mu * frictionBasis;
                    double latDemand = -lateralSpeed * SIDE_SLIP_DAMPING * nm * dt;
                    latForce = Mth.clamp(latDemand, -gripBudget, gripBudget);
                }

                // 摩擦圆总预算
                double totalDemand = Math.sqrt(longForce * longForce + latForce * latForce);
                if (totalDemand > frictionBudget && totalDemand > 1e-10) {
                    double scale = frictionBudget / totalDemand;
                    longForce *= scale;
                    latForce *= scale;
                }

                forceVec.fma(longForce, fwdD);
                forceVec.fma(latForce, sideD);

                // 轮端 RPM（供 EnginePowerSystem 下轮使用）
                sw.setCurrentWheelRpm(forwardSpeed / (Math.PI * 2.0 * rad) * 60.0);

                // 爆胎
                if (!heldItem.isEmpty()) {
                    double altitude = bp.getY() - 63.0;
                    var burstResult = TirePhysicsCalculator.checkBurst(
                            effectivePressure, DEFAULT_MAX_PRESSURE, altitude);
                    if (burstResult.burst()) {
                        sw.setHeldItem(ItemStack.EMPTY);
                        IACP.LOGGER.info("[SuspensionPhysics] 轮胎爆裂 at {}", bp);
                    }
                }
            }

            // 施加冲量
            ForceTotal forceTotal = new ForceTotal();
            forceTotal.applyImpulseAtPoint(subLevel, forcePos, forceVec);
            handle.applyForcesAndReset(forceTotal);*/
        //        }
    }
}
