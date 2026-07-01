package com.hainabaichuan75.iac_p.block.turret;

import java.util.*;

/**
 * ClearanceAlgorithmTest —— 脱离 Minecraft 环境的纯算法验证。
 * <p>
 * 使用简单的 int[3] 代替 BlockPos，验证旋转 + 网格查询的核心逻辑。
 * 在 IDE 中直接运行 main() 即可查看结果。
 */
public class ClearanceAlgorithmTest {

    // ==================================================================
    //  纯数据结构（模拟 BlockPos）
    // ==================================================================
    record Vec3i(int x, int y, int z) {}

    // ==================================================================
    //  被测试的核心算法（与 TurretClearanceSolver 逻辑一致）
    //  变换顺序：translateToPivot → pitch → translateBack → yaw → translateToWorld
    // ==================================================================

    static Vec3i rotateOffset(Vec3i origin, Vec3i offset, Vec3i pitchPivot,
                               double yawDeg, double pitchDeg) {
        double radYaw = Math.toRadians(yawDeg);
        double radPitch = Math.toRadians(pitchDeg);

        double x = offset.x;
        double y = offset.y;
        double z = offset.z;

        // Step 1: 平移至俯仰枢轴空间
        x -= pitchPivot.x;
        y -= pitchPivot.y;
        z -= pitchPivot.z;

        // Step 2: Pitch（绕 X 轴，取反使正 pitch = 上抬）
        double cosP = Math.cos(-radPitch);
        double sinP = Math.sin(-radPitch);
        double y1 = y * cosP - z * sinP;
        double z1 = y * sinP + z * cosP;

        // Step 3: 平移回原始空间
        double x2 = x + pitchPivot.x;
        double y2 = y1 + pitchPivot.y;
        double z2 = z1 + pitchPivot.z;

        // Step 4: Yaw（绕 Y 轴）
        double cosY = Math.cos(radYaw);
        double sinY = Math.sin(radYaw);
        double x3 = x2 * cosY - z2 * sinY;
        double z3 = x2 * sinY + z2 * cosY;

        // Step 5: 平移至世界坐标（四舍五入）
        return new Vec3i(
            origin.x + (int) Math.round(x3),
            origin.y + (int) Math.round(y2),
            origin.z + (int) Math.round(z3)
        );
    }

    static boolean isBlocked(Set<Vec3i> occupied, Vec3i origin,
                             List<Vec3i> footprint, Vec3i pitchPivot,
                             double yawDeg, double pitchDeg) {
        for (Vec3i offset : footprint) {
            Vec3i worldPos = rotateOffset(origin, offset, pitchPivot, yawDeg, pitchDeg);
            if (occupied.contains(worldPos)) return true;
        }
        return false;
    }

    static List<double[]> mergeRanges(List<Double> freeAngles, double stepDeg, double minGapDeg) {
        if (freeAngles.isEmpty()) return List.of();
        List<double[]> result = new ArrayList<>();
        double rangeStart = freeAngles.get(0);
        double rangeEnd = freeAngles.get(0);
        for (int i = 1; i < freeAngles.size(); i++) {
            double curr = freeAngles.get(i);
            double prev = freeAngles.get(i - 1);
            if (curr - prev <= stepDeg * 1.5) {
                rangeEnd = curr;
            } else {
                if (rangeEnd - rangeStart >= minGapDeg - 1e-6)
                    result.add(new double[]{rangeStart, rangeEnd});
                rangeStart = curr;
                rangeEnd = curr;
            }
        }
        if (rangeEnd - rangeStart >= minGapDeg - 1e-6)
            result.add(new double[]{rangeStart, rangeEnd});
        return result;
    }

    static List<double[]> computeFreeYawRanges(Set<Vec3i> occupied, Vec3i origin,
                                                List<Vec3i> footprint, Vec3i pitchPivot,
                                                double maxYaw, double step, double minGap) {
        List<Double> free = new ArrayList<>();
        for (double yaw = 0; yaw <= maxYaw; yaw += step) {
            if (!isBlocked(occupied, origin, footprint, pitchPivot, yaw, 0))
                free.add(yaw);
        }
        return mergeRanges(free, step, minGap);
    }

    // ==================================================================
    //  测试数据
    // ==================================================================
    static final Vec3i ORIGIN = new Vec3i(0, 0, 0);
    static final Vec3i PIVOT = new Vec3i(0, 1, 0); // 砂轮 = 俯仰枢轴
    static final List<Vec3i> B2 = List.of(new Vec3i(0,1,0), new Vec3i(0,1,1));
    static final List<Vec3i> B3 = List.of(new Vec3i(0,1,0), new Vec3i(0,1,1), new Vec3i(0,1,2));

    // ==================================================================
    //  Tests
    // ==================================================================

    static void testNoObstacles() {
        System.out.println("===== Test 1: 无阻挡 =====");
        var r = computeFreeYawRanges(Set.of(), ORIGIN, B2, PIVOT, 360, 2, 5);
        boolean ok = r.size() == 1 && r.get(0)[0] == 0.0 && r.get(0)[1] == 360.0;
        System.out.println(ok ? "  ✅ [0°, 360°]" : "  ❌ " + r.size());
    }

    static void testFrontWall() {
        System.out.println("\n===== Test 2: +Z 墙 =====");
        Set<Vec3i> occ = Set.of(new Vec3i(0,1,2), new Vec3i(1,1,2), new Vec3i(-1,1,2));
        boolean b0 = isBlocked(occ, ORIGIN, B3, PIVOT, 0, 0);
        boolean b90 = isBlocked(occ, ORIGIN, B3, PIVOT, 90, 0);
        boolean b180 = isBlocked(occ, ORIGIN, B3, PIVOT, 180, 0);
        boolean b270 = isBlocked(occ, ORIGIN, B3, PIVOT, 270, 0);
        System.out.println("   0°(+Z)=" + b0 + " 90°(-X)=" + b90 + " 180°(-Z)=" + b180 + " 270°(+X)=" + b270);
        System.out.println((b0 && !b90 && !b180 && !b270) ? "  ✅" : "  ❌");
    }

    static void testSideWall() {
        System.out.println("\n===== Test 3: +X 墙 (z=0 平面) =====");
        // 墙在 +X 方向、z=0 平面，炮管 270° 指向 +X 时会撞到
        Set<Vec3i> occ = Set.of(new Vec3i(2,1,0), new Vec3i(3,1,0));
        // 3格炮管 (0,1,2) 转 270° → (2,1,0) 撞墙
        boolean b = isBlocked(occ, ORIGIN, B3, PIVOT, 270, 0);
        System.out.println("  270°(+X) blocked=" + b + " (期望 true)");
        System.out.println(b ? "  ✅" : "  ❌");
    }

    static void testSelfExclusion() {
        System.out.println("\n===== Test 4: 自身排除 =====");
        Set<Vec3i> occ = new HashSet<>(Set.of(new Vec3i(0,1,0)));
        boolean b1 = isBlocked(occ, ORIGIN, List.of(new Vec3i(0,1,0)), PIVOT, 0, 0);
        occ.clear();
        boolean b2 = isBlocked(occ, ORIGIN, List.of(new Vec3i(0,1,0)), PIVOT, 0, 0);
        System.out.println("  有自身=" + b1 + " 排除后=" + b2 + " (期望 true, false)");
        System.out.println((b1 && !b2) ? "  ✅" : "  ❌");
    }

    static void testPitchBlocking() {
        System.out.println("\n===== Test 5: 俯仰阻挡 =====");
        // 头顶 (0,3,1) 有方块，需要 pitch=60° 才撞到
        Set<Vec3i> occ60 = Set.of(new Vec3i(0,3,1));
        boolean h0   = isBlocked(occ60, ORIGIN, B3, PIVOT, 0, 0);
        boolean h60  = isBlocked(occ60, ORIGIN, B3, PIVOT, 0, 60);
        System.out.println("  墙在 (0,3,1): 0°=" + h0 + " 60°=" + h60 + " (期望 false, true)");
        System.out.println((!h0 && h60) ? "  ✅" : "  ❌");

        // 较低的天花板 (0,2,1)，pitch=45° 即撞
        Set<Vec3i> occ45 = Set.of(new Vec3i(0,2,1));
        boolean h45 = isBlocked(occ45, ORIGIN, B3, PIVOT, 0, 45);
        System.out.println("  墙在 (0,2,1): 45°=" + h45 + " (期望 true)");
        System.out.println(h45 ? "  ✅" : "  ❌");
    }

    static void testPitchCoords() {
        System.out.println("\n===== Test 6: 俯仰坐标 =====");
        Vec3i t = rotateOffset(ORIGIN, new Vec3i(0,1,2), PIVOT, 0, 45);
        System.out.println("  (0,1,2)@45° → (" + t.x + "," + t.y + "," + t.z + ")");
        System.out.println("  y=" + t.y + " (>1 则上抬正确)");
        System.out.println(t.y > 1 ? "  ✅" : "  ⚠ y 未上抬");
    }

    static void testLongBarrel() {
        System.out.println("\n===== Test 7: 5格长炮管 =====");
        List<Vec3i> b5 = new ArrayList<>();
        b5.add(new Vec3i(0,1,0));
        for (int i = 1; i <= 5; i++) b5.add(new Vec3i(0,1,i));
        // 墙在 +X 方向 z=0 平面, 炮管转 270°(+X) 时尖端 (0,1,5) → (5,1,0) 不会被 3 格处的墙挡住
        // 但炮管中部 (0,1,3~4) 会在旋转后经过墙的位置
        // 墙在 (3,1,0): 5格炮管 (0,1,3) 在 270° 时 → (3,1,0) 撞墙
        Set<Vec3i> occ = Set.of(new Vec3i(3,1,0));
        for (double y : new double[]{0,30,60,90,270}) {
            boolean b = isBlocked(occ, ORIGIN, b5, PIVOT, y, 0);
            System.out.println("  " + y + "°=" + b);
        }
        boolean blocked270 = isBlocked(occ, ORIGIN, b5, PIVOT, 270, 0);
        System.out.println("  270° 应被阻挡: " + blocked270);
    }

    static void testCorridor() {
        System.out.println("\n===== Test 8: 狭窄通道 =====");
        Set<Vec3i> occ = Set.of(new Vec3i(1,1,0), new Vec3i(-1,1,0));
        var r = computeFreeYawRanges(occ, ORIGIN, B2, PIVOT, 360, 2, 5);
        System.out.println("  范围数=" + r.size() + " (期望 2: ±Z)");
        for (var x : r) System.out.println("    [" + fmt(x[0]) + "°, " + fmt(x[1]) + "°]");
        System.out.println(r.size() == 2 ? "  ✅" : "  ❌");
    }

    static void testFullyBlocked() {
        System.out.println("\n===== Test 9: 全阻挡 =====");
        Set<Vec3i> occ = Set.of(new Vec3i(0,1,0), new Vec3i(1,1,0), new Vec3i(-1,1,0),
            new Vec3i(0,2,0), new Vec3i(0,1,1), new Vec3i(0,1,-1));
        var r = computeFreeYawRanges(occ, ORIGIN, List.of(new Vec3i(0,1,0)), PIVOT, 360, 2, 5);
        System.out.println("  范围数=" + r.size() + " (期望 0)");
        System.out.println(r.isEmpty() ? "  ✅" : "  ❌");
    }

    static String fmt(double d) { return String.format("%.1f", d); }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  TurretClearanceSolver v2 算法验证");
        System.out.println("  俯仰枢轴=(0,1,0)  MC偏航: 0°=+Z");
        System.out.println("========================================\n");
        testNoObstacles();
        testFrontWall();
        testSideWall();
        testSelfExclusion();
        testPitchBlocking();
        testPitchCoords();
        testLongBarrel();
        testCorridor();
        testFullyBlocked();
        System.out.println("\n========================================\n");
    }
}
