package com.hainabaichuan75.iac_p.block.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * TurretClearanceSolver —— 网格级炮塔旋转间隙求解器。
 * <p>
 * <b>设计意图</b>（对应 Crossout 动态间隙求解器第1章，但适配 Minecraft 方块网格）：
 * <p>
 * 在"炮塔为多方块结构、旋转由 GeckoLib 动画驱动"的新架构下，
 * 炮塔模型旋转到某角度时可能穿入车体其他方块。
 * 本类通过检查各候选角度下炮管占用的 BlockPos 是否与已有非空气方块重叠，
 * 计算出可用的偏航/俯仰角度范围。
 * <p>
 * <b>原理</b>：
 * <pre>
 * 1. 定义炮管在 0° 偏航、0° 俯仰时的局部 BlockPos 偏移表（barrel footprint）
 * 2. 对每个候选角度 (yaw, pitch)：
 *    a. 将 barrel footprint 通过旋转矩阵变换到世界 BlockPos
 *    b. 检查这些位置中是否有非空气方块（且不属于炮塔自身）
 * 3. 合并连续的自由角度 → 输出可用范围
 * </pre>
 * <p>
 * <b>精度</b>：方块网格级。旋转后的位置四舍五入到最近的 BlockPos，
 * 精度为 ±0.5 格。对于 Minecraft 的 1m³ 方块网格来说，这个精度足够。
 * <p>
 * <b>优势</b>：不依赖 AABB、不依赖凸包、不依赖几何相交库，只查询 BlockState。
 */
public class TurretClearanceSolver {

    // ==================================================================
    //  常量
    // ==================================================================
    /** 最小有效间隙（度），小于此值的缝隙被视为不可用 */
    private static final double MIN_GAP_DEGREES = 5.0;

    /** 最大扫描半径（格），超出此距离的方块不考虑 */
    private static final int SCAN_RADIUS = 8;

    // ==================================================================
    //  数据结构
    // ==================================================================
    /**
     * 一个连续的可用的角度范围。
     */
    public record AngleRange(double minDeg, double maxDeg) {
        /** 返回此范围的中心角度 */
        public double center() { return (minDeg + maxDeg) / 2.0; }

        /** 返回范围大小（度） */
        public double span() { return maxDeg - minDeg; }

        /** 角度 θ 是否在此范围内（含边界） */
        public boolean contains(double theta) {
            return theta >= minDeg - 1e-6 && theta <= maxDeg + 1e-6;
        }
    }

    /**
     * 完整的间隙求解结果。
     */
    public record ClearanceResult(
            List<AngleRange> yawRanges,
            List<AngleRange> pitchRanges
    ) {
        /** 偏航是否完全没有可用角度 */
        public boolean yawCompletelyBlocked() { return yawRanges.isEmpty(); }

        /** 俯仰是否完全没有可用角度 */
        public boolean pitchCompletelyBlocked() { return pitchRanges.isEmpty(); }

        /** 获取距离 target 最近的自由偏航角（在 blocked 区域时找边界） */
        public double clampYaw(double target) {
            if (yawRanges.isEmpty()) return target; // 全 blocked，啥也干不了
            for (AngleRange range : yawRanges) {
                if (range.contains(target)) return target;
            }
            // 找最近的边界
            double best = yawRanges.get(0).minDeg;
            double bestDist = Math.abs(wrapAngleDiff(target, best));
            for (AngleRange range : yawRanges) {
                for (double bound : new double[]{range.minDeg, range.maxDeg}) {
                    double d = Math.abs(wrapAngleDiff(target, bound));
                    if (d < bestDist) { bestDist = d; best = bound; }
                }
            }
            return best;
        }

        /** 获取距离 target 最近的自由俯仰角 */
        public double clampPitch(double target) {
            if (pitchRanges.isEmpty()) return target;
            for (AngleRange range : pitchRanges) {
                if (range.contains(target)) return target;
            }
            double best = pitchRanges.get(0).minDeg;
            double bestDist = Math.abs(target - best);
            for (AngleRange range : pitchRanges) {
                for (double bound : new double[]{range.minDeg, range.maxDeg}) {
                    double d = Math.abs(target - bound);
                    if (d < bestDist) { bestDist = d; best = bound; }
                }
            }
            return best;
        }

        /** 将角度差值归一化到 [-180, 180] */
        private static double wrapAngleDiff(double from, double to) {
            double diff = to - from;
            while (diff > 180.0) diff -= 360.0;
            while (diff < -180.0) diff += 360.0;
            return diff;
        }
    }

    // ==================================================================
    //  实例状态
    // ==================================================================
    /** 炮管局部 BlockPos 偏移表（相对于偏航旋转中心的偏移） */
    private final List<BlockPos> barrelFootprint;

    /**
     * 俯仰枢轴点的局部偏移（相对于偏航旋转中心）。
     * <p>
     * 对于标准炮塔布局：地毯在 (0,0,0)，砂轮在 (0,1,0)，
     * 俯仰旋转绕砂轮中心发生，因此 pitchPivotOffset = (0, 1, 0)。
     */
    private final BlockPos pitchPivotOffset;

    /**
     * 创建一个 TurretClearanceSolver。
     *
     * @param barrelFootprint   炮管在 0° 偏航、0° 俯仰时，相对于偏航旋转中心
     *                          （通常是地毯/底座中心 BlockPos）的 BlockPos 偏移列表。
     *                          <p>
     *                          例如，对于一块地毯 + 上方一格砂轮 + 前方延伸二格避雷针的结构：
     *                          <pre>
     *                          List.of(
     *                              new BlockPos(0, 1, 0),   // 砂轮（俯仰枢轴）
     *                              new BlockPos(0, 1, 1),   // 避雷针根
     *                              new BlockPos(0, 1, 2)    // 避雷针尖
     *                          )
     *                          </pre>
     * @param pitchPivotOffset  俯仰枢轴点相对于偏航旋转中心的局部偏移。
     *                          <p>
     *                          标准布局中砂轮在底座正上方一格：{@code new BlockPos(0, 1, 0)}。
     *                          如果炮管没有独立的俯仰枢轴（仅偏航），传 {@code BlockPos.ZERO}。
     */
    public TurretClearanceSolver(List<BlockPos> barrelFootprint, BlockPos pitchPivotOffset) {
        if (barrelFootprint == null || barrelFootprint.isEmpty()) {
            throw new IllegalArgumentException("barrelFootprint must not be null or empty");
        }
        if (pitchPivotOffset == null) {
            throw new IllegalArgumentException("pitchPivotOffset must not be null");
        }
        // 防御性拷贝 + 去重
        this.barrelFootprint = new ArrayList<>(new LinkedHashSet<>(barrelFootprint));
        this.pitchPivotOffset = pitchPivotOffset;
    }

    // ==================================================================
    //  核心方法
    // ==================================================================

    /**
     * 计算给定炮塔位置在当前 SubLevel 内的可用偏航范围。
     *
     * @param level      世界
     * @param turretPos  炮塔底座（地毯）的 BlockPos，即偏航旋转中心
     * @param ignoredPos 炮塔自身占用的 BlockPos 集合（不计为障碍物）
     * @param pitchDeg   当前俯仰角（度），固定此俯仰计算偏航间隙
     * @param maxYawDeg  最大偏航范围（度），通常 360
     * @param stepDeg    采样步进（度），建议 2°~5°
     * @return 可用的偏航角度范围列表
     */
    public List<AngleRange> computeFreeYawRanges(
            Level level,
            BlockPos turretPos,
            Set<BlockPos> ignoredPos,
            double pitchDeg,
            double maxYawDeg,
            double stepDeg
    ) {
        // 1. 收集附近已占用的方块
        Set<BlockPos> occupied = collectOccupiedBlocks(level, turretPos, SCAN_RADIUS);
        occupied.removeAll(ignoredPos);
        if (occupied.isEmpty()) {
            // 没有障碍物 → 全范围可用
            return List.of(new AngleRange(0, maxYawDeg));
        }

        // 2. 逐角度采样
        List<Double> freeAngles = new ArrayList<>();
        for (double yaw = 0; yaw <= maxYawDeg; yaw += stepDeg) {
            if (!isFootprintBlocked(occupied, turretPos, yaw, pitchDeg)) {
                freeAngles.add(yaw);
            }
        }

        // 3. 合并连续角度 → 范围列表，过滤小间隙
        return mergeRanges(freeAngles, stepDeg, MIN_GAP_DEGREES);
    }

    /**
     * 计算给定炮塔位置的可用俯仰范围。
     *
     * @param level        世界
     * @param turretPos    炮塔底座 BlockPos
     * @param ignoredPos   炮塔自身占用的 BlockPos 集合
     * @param yawDeg       当前偏航角（度），固定此偏航计算俯仰间隙
     * @param maxPitchDeg  最大俯仰范围（度），如 60（±30°）
     * @param pitchCenter  俯仰中心角（度），0 = 水平
     * @param stepDeg      采样步进（度）
     * @return 可用的俯仰角度范围列表
     */
    public List<AngleRange> computeFreePitchRanges(
            Level level,
            BlockPos turretPos,
            Set<BlockPos> ignoredPos,
            double yawDeg,
            double maxPitchDeg,
            double pitchCenter,
            double stepDeg
    ) {
        Set<BlockPos> occupied = collectOccupiedBlocks(level, turretPos, SCAN_RADIUS);
        occupied.removeAll(ignoredPos);
        if (occupied.isEmpty()) {
            double half = maxPitchDeg / 2.0;
            return List.of(new AngleRange(pitchCenter - half, pitchCenter + half));
        }

        List<Double> freeAngles = new ArrayList<>();
        double half = maxPitchDeg / 2.0;
        for (double pitch = pitchCenter - half; pitch <= pitchCenter + half; pitch += stepDeg) {
            if (!isFootprintBlocked(occupied, turretPos, yawDeg, pitch)) {
                freeAngles.add(pitch);
            }
        }

        return mergeRanges(freeAngles, stepDeg, MIN_GAP_DEGREES);
    }

    /**
     * 简便方法：同时计算偏航和俯仰的可用范围。
     */
    public ClearanceResult computeAll(
            Level level,
            BlockPos turretPos,
            Set<BlockPos> ignoredPos,
            double maxYawDeg,
            double maxPitchDeg,
            double pitchCenter,
            double stepDeg
    ) {
        List<AngleRange> yawRanges = computeFreeYawRanges(
                level, turretPos, ignoredPos, pitchCenter, maxYawDeg, stepDeg);
        List<AngleRange> pitchRanges = computeFreePitchRanges(
                level, turretPos, ignoredPos, 0, maxPitchDeg, pitchCenter, stepDeg);
        return new ClearanceResult(yawRanges, pitchRanges);
    }

    // ==================================================================
    //  核心算法
    // ==================================================================

    /**
     * 检查 barrel footprint 在给定 (yaw, pitch) 角度下是否与 occupied 中的位置重叠。
     */
    private boolean isFootprintBlocked(
            Set<BlockPos> occupied,
            BlockPos origin,
            double yawDeg,
            double pitchDeg
    ) {
        for (BlockPos localOffset : barrelFootprint) {
            BlockPos worldPos = rotateBlockPos(origin, localOffset, yawDeg, pitchDeg, pitchPivotOffset);
            if (occupied.contains(worldPos)) {
                return true; // 哪怕只有一个方块重叠也算 blocked
            }
        }
        return false;
    }

    /**
     * 将局部偏移量从炮塔局部空间变换到世界 BlockPos。
     * <p>
     * <b>变换顺序</b>（从局部到世界）：
     * <pre>
     * 1. 平移至俯仰枢轴局部空间：减去 pitchPivotOffset
     * 2. Pitch：绕局部 X 轴旋转（在俯仰枢轴点）
     * 3. 平移回原始空间：加上 pitchPivotOffset
     * 4. Yaw：绕 Y 轴旋转（在偏航枢轴点 = 地毯原点）
     * 5. 平移至世界坐标：加上 origin
     * </pre>
     * <p>
     * 每一步的结果四舍五入到最近的整数 BlockPos。
     */
    private static BlockPos rotateBlockPos(
            BlockPos origin,
            BlockPos offset,
            double yawDeg,
            double pitchDeg,
            BlockPos pitchPivotOffset
    ) {
        double radYaw = Math.toRadians(yawDeg);
        double radPitch = Math.toRadians(pitchDeg);

        double x = offset.getX();
        double y = offset.getY();
        double z = offset.getZ();

        // ---------- Step 1: 平移至俯仰枢轴空间 ----------
        double px = pitchPivotOffset.getX();
        double py = pitchPivotOffset.getY();
        double pz = pitchPivotOffset.getZ();
        x -= px;
        y -= py;
        z -= pz;

        // ---------- Step 2: Pitch（绕 X 轴旋转，取反使正 pitch = 上抬） ----------
        // 标准右手系绕 X 轴旋转: y' = y*cosθ - z*sinθ, z' = y*sinθ + z*cosθ
        // 正 θ 会使 +Z 转向 -Y（下俯）。我们需要正 pitch = 上抬（+Z 转向 +Y），
        // 所以取反 pitch 角度：使用 -θ
        double cosPitch = Math.cos(-radPitch);
        double sinPitch = Math.sin(-radPitch);
        double y1 = y * cosPitch - z * sinPitch;
        double z1 = y * sinPitch + z * cosPitch;
        // (x 不变：绕 X 轴旋转不影响 X 分量)
        double xAfterPitch = x;

        // ---------- Step 3: 平移回原始空间 ----------
        double x2 = xAfterPitch + px;
        double y2 = y1 + py;
        double z2 = z1 + pz;

        // ---------- Step 4: Yaw（绕 Y 轴旋转） ----------
        double cosYaw = Math.cos(radYaw);
        double sinYaw = Math.sin(radYaw);
        double x3 = x2 * cosYaw - z2 * sinYaw;
        double z3 = x2 * sinYaw + z2 * cosYaw;
        // (y 不变：绕 Y 轴旋转不影响 Y 分量)
        double y3 = y2;

        // ---------- Step 5: 平移至世界坐标（四舍五入到最近 BlockPos） ----------
        return BlockPos.containing(
                Math.floor(origin.getX() + x3 + 0.5),
                Math.floor(origin.getY() + y3 + 0.5),
                Math.floor(origin.getZ() + z3 + 0.5)
        );
    }

    // ==================================================================
    //  障碍物收集
    // ==================================================================

    /**
     * 收集以 center 为中心、半径 radius 球体内的所有非空气方块的 BlockPos。
     * <p>
     * 使用 SubLevel 对应的 Level 进行查询。如果 vehicleSL 为 null，
     * 则用主世界 level。范围限制在 radius 内以避免全 SubLevel 扫描。
     */
    private static Set<BlockPos> collectOccupiedBlocks(
            Level level,
            BlockPos center,
            int radius
    ) {
        Set<BlockPos> occupied = new HashSet<>();
        int r = radius;

        // 限制扫描范围到加载的 chunk
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    // 球体裁剪：跳过距离超出 radius 的角落
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;

                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

                    // 确保 chunk 已加载
                    if (!level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;

                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir()) {
                        occupied.add(cursor.immutable());
                    }
                }
            }
        }

        return occupied;
    }

    // ==================================================================
    //  角度范围工具
    // ==================================================================

    /**
     * 将离散的自由角度列表合并为连续的 AngleRange 列表。
     * 跳过小于 minGapDeg 的间隙。
     */
    private static List<AngleRange> mergeRanges(
            List<Double> freeAngles,
            double stepDeg,
            double minGapDeg
    ) {
        if (freeAngles.isEmpty()) return List.of();

        List<AngleRange> result = new ArrayList<>();
        double rangeStart = freeAngles.get(0);
        double rangeEnd = freeAngles.get(0);

        for (int i = 1; i < freeAngles.size(); i++) {
            double curr = freeAngles.get(i);
            double prev = freeAngles.get(i - 1);

            if (curr - prev <= stepDeg * 1.5) {
                // 连续：延长当前范围
                rangeEnd = curr;
            } else {
                // 断开：保存当前范围，开始新范围
                addRangeIfValid(result, rangeStart, rangeEnd, minGapDeg);
                rangeStart = curr;
                rangeEnd = curr;
            }
        }
        addRangeIfValid(result, rangeStart, rangeEnd, minGapDeg);

        return result;
    }

    /** 如果范围跨度 >= minGapDeg，则加入结果 */
    private static void addRangeIfValid(
            List<AngleRange> ranges,
            double start, double end,
            double minGapDeg
    ) {
        if (end - start >= minGapDeg - 1e-6) {
            ranges.add(new AngleRange(start, end));
        }
    }

    /**
     * 适用于偏航 0-360° 的环绕合并。
     * 当 0° 和 360° 都是自由角度时，合并它们。
     */
    public static List<AngleRange> mergeYawRanges(List<AngleRange> normalRanges, double stepDeg) {
        if (normalRanges.isEmpty()) return normalRanges;

        // 检查首尾是否在 0/360 连续
        AngleRange first = normalRanges.get(0);
        AngleRange last = normalRanges.get(normalRanges.size() - 1);

        if (first.minDeg <= stepDeg && last.maxDeg >= 360.0 - stepDeg) {
            // 首尾连续 → 合并
            List<AngleRange> merged = new ArrayList<>();
            merged.add(new AngleRange(last.minDeg - 360.0, first.maxDeg));
            for (int i = 1; i < normalRanges.size() - 1; i++) {
                merged.add(normalRanges.get(i));
            }
            return merged;
        }

        return normalRanges;
    }

    // ==================================================================
    //  工厂方法：预设的炮管外形
    // ==================================================================

    /**
     * 创建一个适用于"地毯 + 砂轮 + 避雷针"标准炮塔布局的求解器。
     * <p>
     * 布局（以地毯为原点，朝 SOUTH 为例）：
     * <pre>
     * (0, 0, 0) = 地毯（偏航枢轴）
     * (0, 1, 0) = 砂轮（俯仰枢轴）
     * (0, 1, 1) ~ (0, 1, N) = 避雷针延伸
     * </pre>
     *
     * @param barrelLength 炮管的有效延伸长度（格），含砂轮位置；
     *                     2 = 砂轮 + 1格避雷针；
     *                     3 = 砂轮 + 2格避雷针
     */
    public static TurretClearanceSolver createStandardBarrel(int barrelLength) {
        BlockPos pitchPivot = new BlockPos(0, 1, 0); // 砂轮位置 = 俯仰枢轴
        List<BlockPos> footprint = new ArrayList<>();
        // 砂轮位置
        footprint.add(pitchPivot);
        // 炮管向前延伸
        for (int i = 1; i <= barrelLength; i++) {
            footprint.add(new BlockPos(0, 1, i));
        }
        return new TurretClearanceSolver(footprint, pitchPivot);
    }

    /**
     * 从一组实际的 BlockPos（世界坐标）反算局部偏移，创建求解器。
     * <p>
     * 用于在运行时根据已放置的炮塔方块自动推断 barrel footprint。
     *
     * @param turretPos      炮塔底座（地毯）位置
     * @param pitchPivotPos  俯仰枢轴位置（通常是砂轮的世界坐标）
     * @param turretBlocks   炮塔所有方块的 world BlockPos（含地毯自身）
     * @return 推断出的 TurretClearanceSolver，排除地毯自身
     */
    public static TurretClearanceSolver inferFromBlocks(
            BlockPos turretPos,
            BlockPos pitchPivotPos,
            Set<BlockPos> turretBlocks
    ) {
        BlockPos pivotOffset = pitchPivotPos.subtract(turretPos);
        List<BlockPos> offsets = new ArrayList<>();
        for (BlockPos bp : turretBlocks) {
            if (!bp.equals(turretPos)) {
                offsets.add(bp.subtract(turretPos));
            }
        }
        if (offsets.isEmpty()) {
            throw new IllegalArgumentException("炮塔没有非底座的方块可供推断 barrel footprint");
        }
        return new TurretClearanceSolver(offsets, pivotOffset);
    }
}
