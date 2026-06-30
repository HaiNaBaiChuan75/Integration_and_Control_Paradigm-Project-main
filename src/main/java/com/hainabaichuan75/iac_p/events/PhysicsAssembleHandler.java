package com.hainabaichuan75.iac_p.events;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.hainabaichuan75.iac_p.content.blocks.cockpit.CockpitBlock;
import com.hainabaichuan75.iac_p.core.util.SubLevelUtil;
import com.hainabaichuan75.iac_p.events.SableBlockHelper;
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
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.*;

/**
 * 物理装配处理器 —— 将主世界中的方块群装配到 SubLevel（物理化），或拆解回主世界。
 * <p>
 * 装配：Ctrl+右键射线检测座舱 → BFS 扫描相连方块 → 创建 SubLevel。
 * 拆解：创建 FreeConstraint 用 PD 伺服逐步对齐到整格 → 安全检查 → 放回主世界。
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

    /** 玩家操作冷却（tick），防止拆解后立即再次装配 */
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
            int readyTicks
    ) {}

    // ==================================================================
    //  服务端 tick — 处理活跃拆解 + 冷却递减
    // ==================================================================
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 玩家冷却递减
        PLAYER_COOLDOWNS.values().removeIf(ticks -> ticks <= 1);
        PLAYER_COOLDOWNS.replaceAll((uuid, ticks) -> ticks - 1);

        if (ACTIVE_DISASSEMBLIES.isEmpty()) return;

        Iterator<Map.Entry<UUID, DisassemblyState>> it = ACTIVE_DISASSEMBLIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DisassemblyState> entry = it.next();
            DisassemblyState ds = entry.getValue();

            if (ds.subLevel().getPlot() == null || ds.subLevel().getPlot().getSubLevel() == null) {
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
        }
    }

    // ==================================================================
    //  公开入口
    // ==================================================================

    public static void handleAssembleOrDisassemble(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();

        if (!player.blockPosition().closerThan(pos, 10.0)) {
            IACP.LOGGER.warn("[PhysicsAssemble] 玩家距离太远");
            return;
        }

        // 第一步：检查该位置是否已处于 SubLevel 中（装配过的结构，方块已被移走）
        SubLevel existingSubLevel = SubLevelUtil.getSubLevelAt(level, pos);
        if (existingSubLevel != null) {
            IACP.LOGGER.info("[PhysicsAssemble] 开始拆解 SubLevel @ {}", pos);
            tryDisassemble(level, existingSubLevel, pos);
            return;
        }

        // 第二步：检查该位置是否有驾驶舱方块（尚未装配）
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CockpitBlock || block instanceof BaseCabinBlock) {
            IACP.LOGGER.info("[PhysicsAssemble] 开始装配 SubLevel @ {}", pos);
            assembleFromCockpit(level, pos);
            return;
        }

        IACP.LOGGER.warn("[PhysicsAssemble] 目标位置无效（不在 SubLevel 中也未找到驾驶舱）: {}", pos);
    }

    /**
     * 收到客户端 Ctrl+右键信号后，在服务端从玩家眼睛发射 3 格射线，
     * 使用主世界 clip + SubLevel 感知射线检测，找到驾驶舱后装配或拆解。
     */
    public static void handleAssembleSignal(ServerPlayer player) {
        // 冷却检查
        if (PLAYER_COOLDOWNS.containsKey(player.getUUID())) {
            IACP.LOGGER.debug("[PhysicsAssemble] 玩家 {} 操作冷却中，忽略", player.getName().getString());
            return;
        }

        // 设置冷却（不管是装配还是拆解，操作后都会阻止短时间内再次触发）
        PLAYER_COOLDOWNS.put(player.getUUID(), PLAYER_COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();

        // 从玩家眼睛发射 3 格射线
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(3.0));

        // 第一步：主世界射线（命中主世界中的驾驶舱方块 → 装配）
        BlockHitResult mainHit = level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (mainHit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = mainHit.getBlockPos();
            BlockState hitState = level.getBlockState(hitPos);
            if (hitState.getBlock() instanceof CockpitBlock || hitState.getBlock() instanceof BaseCabinBlock) {
                IACP.LOGGER.info("[PhysicsAssemble] 主世界射线命中驾驶舱 @ {} → 装配", hitPos);
                assembleFromCockpit(level, hitPos);
                return;
            }
        }

        // 第二步：SubLevel 感知射线（命中已装配结构中的方块 → 拆解）
        BlockHitResult subHit = SableBlockHelper.rayTraceSubLevels(level, eyePos, endPos);
        if (subHit != null && subHit.getType() == HitResult.Type.BLOCK) {
            // 在世界空间中检查命中点所在的 SubLevel
            SubLevel subLevel = SubLevelUtil.getSubLevelAt(level, subHit.getBlockPos());
            if (subLevel == null) {
                // 用浮点坐标再试一次
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                if (container != null) {
                    for (SubLevel sl : container.getAllSubLevels()) {
                        if (sl.isRemoved()) continue;
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
                // 用命中点的世界 BlockPos 作为触发坐标
                BlockPos triggerPos = BlockPos.containing(subHit.getLocation());
                IACP.LOGGER.info("[PhysicsAssemble] SubLevel 射线命中 @ {} → 拆解 SubLevel={}", triggerPos, subLevel.getUniqueId());
                tryDisassemble(level, subLevel, triggerPos);
                return;
            }
        }

        IACP.LOGGER.warn("[PhysicsAssemble] 服务端射线未命中任何驾驶舱或 SubLevel");
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
        if (bounds == null) return;

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
        if (linearVelSq > Mth.square(MAX_DISASSEMBLY_VELOCITY) ||
                angularVelSq > Mth.square(MAX_DISASSEMBLY_ANGULAR_VELOCITY)) {
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
                        if (index < 0 || index >= chunk.getSectionsCount()) continue;
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
        if (container == null) { IACP.LOGGER.error("[PhysicsAssemble] 无法获取 SubLevelContainer"); return; }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (pipeline == null) { IACP.LOGGER.error("[PhysicsAssemble] 无法获取 PhysicsPipeline"); return; }

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
        if (constraint == null) { IACP.LOGGER.error("[PhysicsAssemble] 创建 FreeConstraint 失败"); return; }

        constraint.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);

        constraint.setMotor(ConstraintJointAxis.LINEAR_X, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);

        ACTIVE_DISASSEMBLIES.put(subLevel.getUniqueId(), new DisassemblyState(
                level, subLevel, triggerPos, constraint, targetOrientation, turns, 0, 0));

        IACP.LOGGER.info("[PhysicsAssemble] 拆解对齐启动: turns={}", turns);
    }

    @Nullable
    private static DisassemblyState tickDisassembling(DisassemblyState ds) {
        int newTicks = ds.ticksElapsed() + 1;
        if (newTicks >= MAX_DISASSEMBLY_TICKS) {
            IACP.LOGGER.warn("[PhysicsAssemble] 拆解超时");
            return null;
        }

        final SubLevel subLevel = ds.subLevel();
        if (!(subLevel instanceof ServerSubLevel)) return null;

        final Pose3d pose = subLevel.logicalPose();

        // 角度误差
        final double angle = pose.orientation().div(ds.targetOrientation(), new Quaterniond()).angle();

        // 位置目标：对齐到整格
        final Vector3d current = pose.transformPosition(
                new Vector3d(pose.rotationPoint()).floor().add(0.5, 0.5, 0.5));
        final Vector3d goal = current.floor(new Vector3d()).add(0.5, 0.5, 0.5);
        final Vector3d localGoal = ds.targetOrientation().transformInverse(goal, new Vector3d());

        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_X, localGoal.x, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_Y, localGoal.y, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        ds.constraint().setMotor(ConstraintJointAxis.LINEAR_Z, localGoal.z, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);

        double angleDeg = Math.toDegrees(Math.abs(angle));
        double distance = current.distance(goal);
        int newReadyTicks = (angleDeg <= DISASSEMBLY_DEGREE_TOLERANCE && distance < DISASSEMBLY_DISTANCE_TOLERANCE)
                ? ds.readyTicks() + 1 : 0;

        IACP.LOGGER.debug("[PhysicsAssemble] 对齐: angle={}°, dist={}, ready={}/5",
                String.format("%.2f", angleDeg), String.format("%.3f", distance), newReadyTicks);

        if (newReadyTicks > 5) {
            placeIntoWorld(ds);
            return null;
        }

        return new DisassemblyState(ds.level(), ds.subLevel(), ds.triggerPos(),
                ds.constraint(), ds.targetOrientation(), ds.disassemblyAngle(), newTicks, newReadyTicks);
    }

    private static void placeIntoWorld(DisassemblyState ds) {
        final SubLevel subLevel = ds.subLevel();
        try {
            if (subLevel instanceof ServerSubLevel serverSubLevel) {
                String safetyError = checkSafety(ds.level(), serverSubLevel);
                if (safetyError != null) {
                    IACP.LOGGER.warn("[PhysicsAssemble] 最终安全检查失败: {}", safetyError);
                    return;
                }
            }

            final BlockPos goal = BlockPos.containing(
                    subLevel.logicalPose().transformPosition(Vec3.atCenterOf(ds.triggerPos())));
            final Rotation rotation = rotationFrom90DegRots(ds.disassemblyAngle());
            performDisassembly(ds.level(), subLevel, ds.triggerPos(), goal, rotation);
            IACP.LOGGER.info("[PhysicsAssemble] 拆解完成 @ {} → {}", ds.triggerPos(), goal);
        } catch (Exception e) {
            IACP.LOGGER.error("[PhysicsAssemble] 拆解异常", e);
        }
    }

    private static void performDisassembly(@NotNull Level level, @NotNull SubLevel subLevel,
                                           @NotNull BlockPos triggerPos, @NotNull BlockPos disassemblyGoal,
                                           @NotNull Rotation rotation) {
        final BoundingBox3i plotBounds = new BoundingBox3i(subLevel.getPlot().getBoundingBox());
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                triggerPos, disassemblyGoal,
                rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal()),
                rotation, (ServerLevel) level);

        final ObjectArrayList<BlockPos> blocks = SubLevelUtil.collectBlocks(level, subLevel);
        if (!blocks.isEmpty()) {
            ((ServerLevelPlot) subLevel.getPlot()).kickAllEntities();
            SubLevelAssemblyHelper.moveBlocks((ServerLevel) level, transform, blocks);
        }
        SubLevelAssemblyHelper.moveTrackingPoints((ServerLevel) level, plotBounds, null, transform);

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer((ServerLevel) level);
        if (container != null) {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    private static void cleanupConstraint(@Nullable PhysicsConstraintHandle constraint) {
        if (constraint != null && constraint.isValid()) constraint.remove();
    }

    /** 从四元数提取 Y 轴偏航角（弧度） */
    private static double getClosestYaw(Quaterniondc q) {
        double siny_cosp = 2.0 * (q.w() * q.y() + q.x() * q.z());
        double cosy_cosp = 1.0 - 2.0 * (q.y() * q.y() + q.z() * q.z());
        return Math.atan2(siny_cosp, cosy_cosp);
    }

    /** 90° 倍数 → Rotation 枚举 */
    private static Rotation rotationFrom90DegRots(int rots) {
        return switch ((rots % 4 + 4) % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // ==================================================================
    //  BFS
    // ==================================================================

    private static Set<BlockPos> bfsConnectedBlocks(ServerLevel level, BlockPos startPos) {
        Set<BlockPos> collected = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        if (!level.getBlockState(startPos).isAir()) { queue.add(startPos); collected.add(startPos); }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                int dx = Math.abs(neighbor.getX() - startPos.getX());
                int dy = Math.abs(neighbor.getY() - startPos.getY());
                int dz = Math.abs(neighbor.getZ() - startPos.getZ());
                if (dx > MAX_RADIUS || dy > MAX_RADIUS || dz > MAX_RADIUS) continue;
                if (!collected.add(neighbor)) continue;
                if (!level.getBlockState(neighbor).isAir()) {
                    queue.add(neighbor);
                    if (collected.size() > MAX_BLOCKS) { IACP.LOGGER.warn("[PhysicsAssemble] BFS 超量"); break; }
                }
            }
        }

        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : collected) {
            if (!level.getBlockState(pos).isAir()) result.add(pos);
        }
        return result;
    }

    private static BoundingBox3i computeBounds(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
