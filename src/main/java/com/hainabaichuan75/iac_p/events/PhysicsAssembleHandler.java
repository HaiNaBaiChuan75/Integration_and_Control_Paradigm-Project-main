package com.hainabaichuan75.iac_p.events;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.hainabaichuan75.iac_p.content.blocks.assembly_barrier.AssemblyBarrierBlock;
import com.hainabaichuan75.iac_p.block.cockpit.CockpitBlock;
import com.hainabaichuan75.iac_p.util.SubLevelUtil;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 物理装配处理器 —— 将主世界中的方块群装配到 SubLevel（物理化），或拆解回主世界。
 * <p>
 * 装配：Ctrl+右键射线检测座舱 → BFS 扫描相连方块 → 创建 SubLevel。 拆解：创建 FreeConstraint 用 PD
 * 伺服逐步对齐到整格 → 安全检查 → 放回主世界。
 * <p>
 * 拆解逻辑完全参照 Simulated 模组的 Physics Assembler 实现。
 */
@EventBusSubscriber(modid = IACP.MODID)
public class PhysicsAssembleHandler {

    // ==================================================================
    //  配置常量
    // ==================================================================
    private static final double DISASSEMBLY_DEGREE_TOLERANCE = 1.0;
    private static final double DISASSEMBLY_DISTANCE_TOLERANCE = 0.2;
    private static final int MAX_DISASSEMBLY_TICKS = 100;
    private static final boolean DISALLOW_MID_AIR_DISASSEMBLY = true;
    private static final float MAX_DISASSEMBLY_VELOCITY = 5.0f;
    private static final float MAX_DISASSEMBLY_ANGULAR_VELOCITY = 2.0f;

    private static final double LINEAR_STIFFNESS = 1000.0;
    private static final double LINEAR_DAMPING = 50.0;
    private static final double ANGULAR_STIFFNESS = 13000.0;
    private static final double ANGULAR_DAMPING = 1000.0;

    private static final int MAX_RADIUS = 16;
    private static final int MAX_BLOCKS = 30000;

    /**
     * 玩家操作冷却（tick），防止拆解后立即再次装配
     */
    private static final int PLAYER_COOLDOWN_TICKS = 20;

    // ==================================================================
    //  玩家冷却追踪
    // ==================================================================
    private static final Map<UUID, Integer> PLAYER_COOLDOWNS = new HashMap<>();

    // ==================================================================
    //  活跃拆解任务追踪
    // ==================================================================
    private static final Map<UUID, DisassemblyState> ACTIVE_DISASSEMBLIES = new HashMap<>();

    private record DisassemblyState(
            ServerLevel level,
            SubLevel subLevel,
            BlockPos triggerPos,
            PhysicsConstraintHandle constraint,
            Quaterniondc targetOrientation,
            int disassemblyAngle,
            int ticksElapsed,
            int readyTicks,
            Vector3d pivotLocal          // 局部空间的约束枢轴点（与 startDisassembling 一致）
            ) {

    }

    // ==================================================================
    //  服务端 tick — 处理活跃拆解 + 冷却递减
    // ==================================================================
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 玩家冷却递减
        PLAYER_COOLDOWNS.values().removeIf(ticks -> ticks <= 1);
        PLAYER_COOLDOWNS.replaceAll((uuid, ticks) -> ticks - 1);

        if (ACTIVE_DISASSEMBLIES.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, DisassemblyState>> it = ACTIVE_DISASSEMBLIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DisassemblyState> entry = it.next();
            DisassemblyState ds = entry.getValue();

            try {
                // 检查 SubLevel 是否仍然存在
                if (ds.subLevel().getPlot() == null || ds.subLevel().getPlot().getSubLevel() == null) {
                    IACP.LOGGER.warn("[PhysicsAssemble] SubLevel 已失效，清理约束");
                    cleanupConstraint(ds.constraint());
                    it.remove();
                    continue;
                }

                // 检查约束句柄是否仍然有效（物理引擎可能在外部使其失效）
                if (!ds.constraint().isValid()) {
                    IACP.LOGGER.warn("[PhysicsAssemble] 约束句柄已失效，清理拆解状态");
                    cleanupConstraint(ds.constraint());
                    it.remove();
                    continue;
                }

                DisassemblyState next = tickDisassembling(ds);
                if (next == null) {
                    cleanupConstraint(ds.constraint());
                    it.remove();
                } else {
                    entry.setValue(next);
                }
            } catch (Exception e) {
                IACP.LOGGER.error("[PhysicsAssemble] 拆解 tick 异常: {}", e.getMessage());
                cleanupConstraint(ds.constraint());
                it.remove();
            }
        }
    }

    // ==================================================================
    //  公开入口
    // ==================================================================
    /**
     * @return true 表示位置有效并执行了装配或拆解；false 表示位置无效，调用方可回退到其他检测手段
     */
    public static boolean handleAssembleOrDisassemble(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();

        if (!player.blockPosition().closerThan(pos, 10.0)) {
            IACP.LOGGER.warn("[PhysicsAssemble] 玩家距离太远");
            return false;
        }

        // 第一步：检查该位置是否已处于 SubLevel 中（装配过的结构，方块已被移走）
        SubLevel existingSubLevel = SubLevelUtil.getSubLevelAt(level, pos);
        if (existingSubLevel != null) {
            IACP.LOGGER.info("[PhysicsAssemble] 开始拆解 SubLevel @ {}", pos);
            tryDisassemble(level, existingSubLevel, pos);
            return true;
        }

        // 第二步：检查该位置是否有驾驶舱方块（尚未装配）
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CockpitBlock || block instanceof BaseCabinBlock) {
            IACP.LOGGER.info("[PhysicsAssemble] 开始装配 SubLevel @ {}", pos);
            assembleFromCockpit(level, pos);
            return true;
        }

        IACP.LOGGER.warn("[PhysicsAssemble] 目标位置无效（不在 SubLevel 中也未找到驾驶舱）: {}", pos);
        return false;
    }

    /**
     * 收到客户端 Ctrl+右键信号后，处理装配或拆解。
     * <p>
     * 有命中位置（hitPos != null）→ 直接交给 {@link #handleAssembleOrDisassemble}，
     * 客户端已提供精确的座舱坐标，无需服务端重复射线检测。 无命中位置（hitPos == null）→ 回退到 SubLevel
     * 感知射线检测（拆解已装配结构）。
     */
    public static void handleAssembleSignal(ServerPlayer player, @Nullable BlockPos hitPos) {
        // 冷却检查
        if (PLAYER_COOLDOWNS.containsKey(player.getUUID())) {
            IACP.LOGGER.debug("[PhysicsAssemble] 玩家 {} 操作冷却中，忽略", player.getName().getString());
            return;
        }

        // 设置冷却（不管是装配还是拆解，操作后都会阻止短时间内再次触发）
        PLAYER_COOLDOWNS.put(player.getUUID(), PLAYER_COOLDOWN_TICKS);

        if (hitPos != null) {
            // 客户端提供了命中位置 → 先尝试直接处理（装配或拆解）
            if (handleAssembleOrDisassemble(player, hitPos)) {
                return; // 成功处理，无需进一步检测
            }
            // 直接路径失败（如 mc.hitResult 穿透 SubLevel 命中了后面的方块）→
            // 回退到 SubLevel 感知射线检测（拆解），行为与旧代码一致
            IACP.LOGGER.debug("[PhysicsAssemble] hitPos {} 无效，回退到 SubLevel 射线检测", hitPos);
        }

        // hitPos == null 或直接路径失败：服务端做 SubLevel 感知射线检测（拆解）
        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(3.0));

        BlockHitResult subHit = SableBlockHelper.rayTraceSubLevels(level, eyePos, endPos);
        if (subHit != null && subHit.getType() == HitResult.Type.BLOCK) {
            // 在世界空间中检查命中点所在的 SubLevel
            SubLevel subLevel = SubLevelUtil.getSubLevelAt(level, subHit.getBlockPos());
            if (subLevel == null) {
                // 用浮点坐标再试一次
                var container = ServerSubLevelContainer.getContainer(level);
                if (container != null) {
                    for (SubLevel sl : container.getAllSubLevels()) {
                        if (sl.isRemoved()) {
                            continue;
                        }
                        var bb = sl.boundingBox();
                        if (bb != null && subHit.getLocation().x >= bb.minX() && subHit.getLocation().x <= bb.maxX()
                                && subHit.getLocation().y >= bb.minY() && subHit.getLocation().y <= bb.maxY()
                                && subHit.getLocation().z >= bb.minZ() && subHit.getLocation().z <= bb.maxZ()) {
                            subLevel = sl;
                            break;
                        }
                    }
                }
            }
            if (subLevel != null) {
                BlockPos triggerPos = BlockPos.containing(subHit.getLocation());
                IACP.LOGGER.info("[PhysicsAssemble] SubLevel 射线命中 @ {} → 拆解 SubLevel={}", triggerPos, subLevel.getUniqueId());
                tryDisassemble(level, subLevel, triggerPos);
                return;
            }
        }

        IACP.LOGGER.warn("[PhysicsAssemble] 未找到可装配/拆解的目标（hitPos=null，SubLevel 射线无命中）");
    }

    // ==================================================================
    //  装配：BFS 扫描 → 创建 SubLevel
    // ==================================================================
    private static void assembleFromCockpit(ServerLevel level, BlockPos startPos) {
        Set<BlockPos> blocks = bfsConnectedBlocks(level, startPos);
        if (blocks.isEmpty()) {
            IACP.LOGGER.warn("[PhysicsAssemble] BFS 未收集到任何方块 @ {}", startPos);
            return;
        }

        BoundingBox3i bounds = computeBounds(blocks);
        if (bounds == null) {
            return;
        }

        IACP.LOGGER.info("[PhysicsAssemble] 收集到 {} 个方块，包围盒: {}", blocks.size(), bounds);

        SubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, startPos, blocks, bounds);
        if (subLevel == null) {
            IACP.LOGGER.error("[PhysicsAssemble] assembleBlocks 返回 null @ {}", startPos);
            return;
        }

        IACP.LOGGER.info("[PhysicsAssemble] 装配成功: SubLevel UUID={}", subLevel.getUniqueId());
    }

    // ==================================================================
    //  拆解：安全检查 → PD 伺服对齐 → 放回世界
    // ==================================================================
    private static void tryDisassemble(ServerLevel level, SubLevel subLevel, BlockPos triggerPos) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            IACP.LOGGER.warn("[PhysicsAssemble] SubLevel 不是 ServerSubLevel");
            return;
        }

        String safetyError = checkSafety(level, serverSubLevel);
        if (safetyError != null) {
            IACP.LOGGER.warn("[PhysicsAssemble] 安全检查失败: {}", safetyError);
            return;
        }

        startDisassembling(level, serverSubLevel, subLevel, triggerPos);
    }

    @Nullable
    private static String checkSafety(ServerLevel level, ServerSubLevel serverSubLevel) {
        final BoundingBox3dc bounds = serverSubLevel.boundingBox();

        // 1. 超出世界高度
        if (bounds.maxY() > level.getMaxBuildHeight() || bounds.minY() < level.getMinBuildHeight()) {
            return "超出世界高度";
        }

        // 2. 速度过快
        final RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        double linearVelSq = handle.getLinearVelocity(new Vector3d()).lengthSquared();
        double angularVelSq = handle.getAngularVelocity(new Vector3d()).lengthSquared();
        if (linearVelSq > Mth.square(MAX_DISASSEMBLY_VELOCITY)
                || angularVelSq > Mth.square(MAX_DISASSEMBLY_ANGULAR_VELOCITY)) {
            return "速度过快";
        }

        // 3. 半空检查
        if (DISALLOW_MID_AIR_DISASSEMBLY) {
            final BoundingBox3i chunkBounds = new BoundingBox3i(
                    (Mth.floor(bounds.minX()) >> 4) - 1,
                    (Mth.floor(bounds.minY()) >> 4) - 1,
                    (Mth.floor(bounds.minZ()) >> 4) - 1,
                    (Mth.floor(bounds.maxX()) >> 4) + 1,
                    (Mth.floor(bounds.maxY()) >> 4) + 1,
                    (Mth.floor(bounds.maxZ()) >> 4) + 1
            );
            boolean nearGround = false;
            scanSectionsLoop:
            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    final LevelChunk chunk = level.getChunk(x, z);
                    for (int y = chunkBounds.minY(); y <= chunkBounds.maxY(); y++) {
                        final int index = chunk.getSectionIndexFromSectionY(y);
                        if (index < 0 || index >= chunk.getSectionsCount()) {
                            continue;
                        }
                        if (!chunk.getSection(index).hasOnlyAir()) {
                            nearGround = true;
                            break scanSectionsLoop;
                        }
                    }
                }
            }
            if (!nearGround) {
                return "在半空中，无法拆解";
            }
        }

        return null;
    }

    private static void startDisassembling(ServerLevel level, ServerSubLevel serverSubLevel,
            SubLevel subLevel, BlockPos triggerPos) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            IACP.LOGGER.error("[PhysicsAssemble] 无法获取 SubLevelContainer");
            return;
        }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (pipeline == null) {
            IACP.LOGGER.error("[PhysicsAssemble] 无法获取 PhysicsPipeline");
            return;
        }

        // 计算最近 90° 倍数旋转
        final Pose3d pose = subLevel.logicalPose();
        final double closestYaw = getClosestYaw(pose.orientation());
        final double ninety = Math.PI / 2.0;
        final int turns = -(Mth.floor(closestYaw / ninety + 0.5));

        final Vector3d com = new Vector3d(serverSubLevel.getMassTracker().getCenterOfMass());
        final Vector3d pivot = new Vector3d(com).floor().add(0.5, 0.5, 0.5);
        final Quaterniondc targetOrientation = new Quaterniond().rotateY(turns * ninety);

        final FreeConstraintConfiguration config = new FreeConstraintConfiguration(
                new Vector3d(), pivot, targetOrientation);
        PhysicsConstraintHandle constraint = pipeline.addConstraint(null, serverSubLevel, config);
        if (constraint == null) {
            IACP.LOGGER.error("[PhysicsAssemble] 创建 FreeConstraint 失败");
            return;
        }

        constraint.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);

        constraint.setMotor(ConstraintJointAxis.LINEAR_X, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);

        ACTIVE_DISASSEMBLIES.put(subLevel.getUniqueId(), new DisassemblyState(
                level, subLevel, triggerPos, constraint, targetOrientation, turns, 0, 0, pivot));

        IACP.LOGGER.info("[PhysicsAssemble] 拆解对齐启动: turns={}, pivot={}", turns, pivot);
    }

    @Nullable
    private static DisassemblyState tickDisassembling(DisassemblyState ds) {
        int newTicks = ds.ticksElapsed() + 1;
        if (newTicks >= MAX_DISASSEMBLY_TICKS) {
            IACP.LOGGER.warn("[PhysicsAssemble] 拆解超时");
            return null;
        }

        final SubLevel subLevel = ds.subLevel();
        if (!(subLevel instanceof ServerSubLevel)) {
            return null;
        }

        // 安全检查：约束句柄可能在上次检查后变为无效（物理学引擎外部失效）
        if (!ds.constraint().isValid()) {
            IACP.LOGGER.warn("[PhysicsAssemble] tick 中约束句柄已失效，终止拆解");
            return null;
        }

        final Pose3d pose = subLevel.logicalPose();

        // ===== 角度对齐 =====
        // 持续更新角度 PD 伺服，维持目标朝向
        ds.constraint().setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);

        final double angle = pose.orientation().div(ds.targetOrientation(), new Quaterniond()).angle();
        double angleDeg = Math.toDegrees(Math.abs(angle));

        // ===== 位置对齐 =====
        // 使用与约束一致的枢轴点（存储的 pivotLocal）计算当前世界位置和对齐目标
        // 修复：不再使用 pose.rotationPoint()（可能与约束枢轴不同）
        final Vector3d current = pose.transformPosition(ds.pivotLocal(), new Vector3d());
        final Vector3d goal = current.floor(new Vector3d()).add(0.5, 0.5, 0.5);

        // 修复：线性马达目标直接用世界坐标 goal。
        // 约束锚点在 (0,0,0) 且无朝向偏移，约束坐标 = 世界坐标。
        // 之前错误地用 targetOrientation.transformInverse(goal) 旋转了目标，
        // 导致非 0° 旋转时指向错误位置。
        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_X, goal.x, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_Y, goal.y, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_Z, goal.z, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);

        // ===== 对齐就绪判定 =====
        double distance = current.distance(goal);
        int newReadyTicks = (angleDeg <= DISASSEMBLY_DEGREE_TOLERANCE && distance < DISASSEMBLY_DISTANCE_TOLERANCE)
                ? ds.readyTicks() + 1 : 0;

        IACP.LOGGER.debug("[PhysicsAssemble] 对齐: angle={}°, dist={}, ready={}/5",
                String.format("%.2f", angleDeg), String.format("%.3f", distance), newReadyTicks);

        if (newReadyTicks > 5) {
            // 尝试放回世界；若失败则继续对齐而不是直接放弃
            if (placeIntoWorld(ds)) {
                return null; // 成功，清理约束
            }
            IACP.LOGGER.warn("[PhysicsAssemble] 放回世界失败，继续对齐重试");
            newReadyTicks = 0; // 重置就绪计数重新尝试
        }

        return new DisassemblyState(ds.level(), ds.subLevel(), ds.triggerPos(),
                ds.constraint(), ds.targetOrientation(), ds.disassemblyAngle(), newTicks, newReadyTicks,
                ds.pivotLocal());
    }

    /**
     * @return true 放回世界成功；false 放回失败（调用方应继续对齐重试）
     */
    private static boolean placeIntoWorld(DisassemblyState ds) {
        final SubLevel subLevel = ds.subLevel();
        try {
            if (subLevel instanceof ServerSubLevel serverSubLevel) {
                // 最终安全检查：只检查世界高度和地面，跳过速度检查。
                // PD 伺服维持阶段可能有微小抖动，速度检查会误杀。
                String safetyError = checkSafetyLenient(ds.level(), serverSubLevel);
                if (safetyError != null) {
                    IACP.LOGGER.warn("[PhysicsAssemble] 最终安全检查失败: {}，将重试", safetyError);
                    return false;
                }
            }

            // 使用 plot 中心作为锚点坐标（类比 Simulated 中组装器方块的固定坐标）
            final BlockPos anchor = subLevel.getPlot().getCenterBlock();
            final BlockPos goal = BlockPos.containing(
                    subLevel.logicalPose().transformPosition(Vec3.atCenterOf(anchor)));
            final Rotation rotation = rotationFrom90DegRots(ds.disassemblyAngle());
            performDisassembly(ds.level(), subLevel, anchor, goal, rotation);
            IACP.LOGGER.info("[PhysicsAssemble] 拆解完成: anchor={} → goal={}, rotation={}",
                    anchor, goal, rotation);
            return true;
        } catch (Exception e) {
            IACP.LOGGER.error("[PhysicsAssemble] 拆解异常", e);
            return false;
        }
    }

    /**
     * 宽松版安全检查：仅检查世界高度和地面，不检查速度。
     * 用于最终放回世界之前的检查，此时 PD 伺服正在维持位置，速度检测可能误杀。
     */
    @Nullable
    private static String checkSafetyLenient(ServerLevel level, ServerSubLevel serverSubLevel) {
        final BoundingBox3dc bounds = serverSubLevel.boundingBox();

        // 1. 超出世界高度
        if (bounds.maxY() > level.getMaxBuildHeight() || bounds.minY() < level.getMinBuildHeight()) {
            return "超出世界高度";
        }

        // 2. 半空检查（同 checkSafety）
        if (DISALLOW_MID_AIR_DISASSEMBLY) {
            final BoundingBox3i chunkBounds = new BoundingBox3i(
                    (Mth.floor(bounds.minX()) >> 4) - 1,
                    (Mth.floor(bounds.minY()) >> 4) - 1,
                    (Mth.floor(bounds.minZ()) >> 4) - 1,
                    (Mth.floor(bounds.maxX()) >> 4) + 1,
                    (Mth.floor(bounds.maxY()) >> 4) + 1,
                    (Mth.floor(bounds.maxZ()) >> 4) + 1
            );
            boolean nearGround = false;
            scanSectionsLoop:
            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    final LevelChunk chunk = level.getChunk(x, z);
                    for (int y = chunkBounds.minY(); y <= chunkBounds.maxY(); y++) {
                        final int index = chunk.getSectionIndexFromSectionY(y);
                        if (index < 0 || index >= chunk.getSectionsCount()) {
                            continue;
                        }
                        if (!chunk.getSection(index).hasOnlyAir()) {
                            nearGround = true;
                            break scanSectionsLoop;
                        }
                    }
                }
            }
            if (!nearGround) {
                return "在半空中，无法拆解";
            }
        }

        return null;
    }

    /**
     * 完全参照 SimAssemblyHelper.disassembleSubLevel 的拆卸逻辑。
     * <p>
     * ⚠️ 不调用 removeSubLevel。Simulated 的 Physics Assembler 在拆卸后从不移除 SubLevel， 空的
     * SubLevel 会继续存在但不影响后续操作。 当车辆已经移动/旋转后，moveBlocks 会将方块放置到世界区块的正确新位置，方块保持可见。
     */
    private static void performDisassembly(@NotNull Level level, @NotNull SubLevel subLevel,
            @NotNull BlockPos subLevelAnchor, @NotNull BlockPos disassemblyGoal,
            @NotNull Rotation rotation) {
        final BoundingBox3i plotBounds = new BoundingBox3i(subLevel.getPlot().getBoundingBox());
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                subLevelAnchor, disassemblyGoal,
                rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal()),
                rotation, (ServerLevel) level);

        final ObjectArrayList<BlockPos> blocks = SubLevelUtil.collectBlocks(level, subLevel);
        if (!blocks.isEmpty()) {
            ((ServerLevelPlot) subLevel.getPlot()).kickAllEntities();
            SubLevelAssemblyHelper.moveBlocks((ServerLevel) level, transform, blocks);
        }
        SubLevelAssemblyHelper.moveTrackingPoints((ServerLevel) level, plotBounds, null, transform);

        // ⚠️ 不调用 removeSubLevel，与 Simulated 行为完全一致。
        // removeSubLevel 会执行 plot.onRemove() → serverLevel.unload()，导致所有区块被卸载，
        // 其中的方块会丢失。Simulated 依赖空的 SubLevel 留在原地。
    }

    // ==================================================================
    //  工具方法
    // ==================================================================
    private static void cleanupConstraint(@Nullable PhysicsConstraintHandle constraint) {
        if (constraint != null && constraint.isValid()) {
            constraint.remove();
        }
    }

    /**
     * 从四元数提取 Y 轴偏航角（弧度）
     */
    private static double getClosestYaw(Quaterniondc q) {
        double siny_cosp = 2.0 * (q.w() * q.y() + q.x() * q.z());
        double cosy_cosp = 1.0 - 2.0 * (q.y() * q.y() + q.z() * q.z());
        return Math.atan2(siny_cosp, cosy_cosp);
    }

    /**
     * 90° 倍数 → Rotation 枚举
     */
    private static Rotation rotationFrom90DegRots(int rots) {
        return switch ((rots % 4 + 4) % 4) {
            case 1 ->
                Rotation.CLOCKWISE_90;
            case 2 ->
                Rotation.CLOCKWISE_180;
            case 3 ->
                Rotation.COUNTERCLOCKWISE_90;
            default ->
                Rotation.NONE;
        };
    }

    // ==================================================================
    //  BFS
    // ==================================================================
    private static Set<BlockPos> bfsConnectedBlocks(ServerLevel level, BlockPos startPos) {
        Set<BlockPos> collected = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        if (!level.getBlockState(startPos).isAir()) {
            queue.add(startPos);
            collected.add(startPos);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                int dx = Math.abs(neighbor.getX() - startPos.getX());
                int dy = Math.abs(neighbor.getY() - startPos.getY());
                int dz = Math.abs(neighbor.getZ() - startPos.getZ());
                if (dx > MAX_RADIUS || dy > MAX_RADIUS || dz > MAX_RADIUS) {
                    continue;
                }
                if (!collected.add(neighbor)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                // 跳过空气方块和装配屏障方块（车库地板等不应成为车体一部分）
                if (!neighborState.isAir() && !(neighborState.getBlock() instanceof AssemblyBarrierBlock)) {
                    queue.add(neighbor);
                    if (collected.size() > MAX_BLOCKS) {
                        IACP.LOGGER.warn("[PhysicsAssemble] BFS 超量");
                        break;
                    }
                }
            }
        }

        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : collected) {
            BlockState state = level.getBlockState(pos);
            // 排除空气和装配屏障方块
            if (!state.isAir() && !(state.getBlock() instanceof AssemblyBarrierBlock)) {
                result.add(pos);
            }
        }
        return result;
    }

    private static BoundingBox3i computeBounds(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            if (pos.getX() < minX) {
                minX = pos.getX();
            }
            if (pos.getY() < minY) {
                minY = pos.getY();
            }
            if (pos.getZ() < minZ) {
                minZ = pos.getZ();
            }
            if (pos.getX() > maxX) {
                maxX = pos.getX();
            }
            if (pos.getY() > maxY) {
                maxY = pos.getY();
            }
            if (pos.getZ() > maxZ) {
                maxZ = pos.getZ();
            }
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
