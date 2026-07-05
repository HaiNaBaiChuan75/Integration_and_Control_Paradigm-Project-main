package com.hainabaichuan75.iac_p.client;

import com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlock;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具调试信息覆盖层。
 * <p>
 * 上车时在右下角显示动力系统与物理参数，每 5 ticks 刷新数据。
 * 文字左对齐，半透明黑底，F3 同款风格。
 */
@EventBusSubscriber(modid = "iac_p", value = Dist.CLIENT)
public class VehicleDebugOverlay {

    private static final int UPDATE_INTERVAL = 5;
    private static int updateCooldown = 0;

    // ===== 缓存数据 =====
    private static double engineTorque = 0;
    private static int totalWheels = 0;
    private static int wheelsWithTire = 0;
    private static double avgWheelRpm = 0;
    private static double avgWheelTorque = 0;
    private static double mass = 0;
    private static double currentSpeedMs = 0;
    private static double currentAccelMs2 = 0;
    private static double frictionPct = 0;
    private static double frictionDemandRatio = 0;

    private static final List<Component> displayLines = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!ClientMountHandler.isMounted()) {
            if (!displayLines.isEmpty()) {
                displayLines.clear();
            }
            return;
        }

        if (--updateCooldown <= 0) {
            updateCooldown = UPDATE_INTERVAL;
            collectData(mc);
        }

        if (displayLines.isEmpty()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int lineH = font.lineHeight + 2;
        int padX = 5, padY = 4;

        int maxW = 0;
        for (Component line : displayLines) {
            int w = font.width(line);
            if (w > maxW) {
                maxW = w;
            }
        }

        var window = mc.getWindow();
        int sw = window.getGuiScaledWidth();
        int sh = window.getGuiScaledHeight();
        int bl = sw - maxW - padX * 2 - 4;
        int br = sw - 2;
        int bt = sh - displayLines.size() * lineH - padY * 2 - 4;
        int bb = sh - 2;

        g.fill(bl, bt, br, bb, 0x88000000);

        int tx = bl + padX;
        int ty = bt + padY;
        for (Component line : displayLines) {
            g.drawString(font, line, tx, ty, 0xFFFFFFFF, true);
            ty += lineH;
        }
    }

    private static void collectData(Minecraft mc) {
        ClientSubLevel sl = ClientMountHandler.getMountedClientSubLevel();
        if (sl == null) {
            return;
        }
        LevelPlot plot = sl.getPlot();
        if (plot == null) {
            return;
        }

        CockpitBlockEntity cockpit = null;
        List<SuspensionTestBlockEntity> susp = new ArrayList<>();
        double totalRadius = 0;
        int onGroundCount = 0;
        BlockPos samplePos = null;

        for (PlotChunkHolder chunk : plot.getLoadedChunks()) {
            BoundingBox3ic bb = chunk.getBoundingBox();
            if (bb == null || bb == BoundingBox3i.EMPTY) {
                continue;
            }
            int cmx = chunk.getPos().getMinBlockX();
            int cmz = chunk.getPos().getMinBlockZ();
            for (int x = bb.minX(); x <= bb.maxX(); x++) {
                for (int y = bb.minY(); y <= bb.maxY(); y++) {
                    for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                        BlockPos wp = new BlockPos(x + cmx, y, z + cmz);
                        if (samplePos == null) {
                            samplePos = wp;
                        }
                        BlockState st = mc.level.getBlockState(wp);
                        BlockEntity be = mc.level.getBlockEntity(wp);
                        if (be instanceof CockpitBlockEntity cbe) {
                            cockpit = cbe;
                        } else if (be instanceof SuspensionTestBlockEntity sbe
                                && st.getBlock() instanceof SuspensionTestBlock) {
                            susp.add(sbe);
                            if (!sbe.isLifted()) {
                                onGroundCount++;
                            }
                            var tire = sbe.getHeldItem().get(OffroadDataComponents.TIRE);
                            if (tire != null) {
                                totalRadius += tire.radius();
                            }
                        }
                    }
                }
            }
        }

        totalWheels = susp.size();
        wheelsWithTire = 0;
        for (var s : susp) {
            if (s.getHeldItem().has(OffroadDataComponents.TIRE)) {
                wheelsWithTire++;
            }
        }

        // ── 扭矩：从 CockpitBlockEntity 的 EnginePart NBT 同步读取 ──
        if (cockpit != null) {
            engineTorque = cockpit.getTorque();
        } else {
            engineTorque = 0;
        }

        // ── 轮端数据 ──
        double avgR = wheelsWithTire > 0 ? totalRadius / wheelsWithTire : 0.25;
        avgWheelRpm = 0;
        int rpmCount = 0;
        for (var s : susp) {
            avgWheelRpm += s.getCurrentWheelRpm();
            rpmCount++;
        }
        avgWheelRpm = rpmCount > 0 ? avgWheelRpm / rpmCount : 0;
        int w = Math.max(totalWheels, 1);
        avgWheelTorque = w > 0 ? engineTorque / w : 0;

        // ── 速度 ──
        double cachedSpeed = ClientMountHandler.getCachedVehicleSpeedMs();
        if (cachedSpeed > 0) {
            currentSpeedMs = cachedSpeed;
        } else if (samplePos != null) {
            Vector3d vel = Sable.HELPER.getVelocity(mc.level, new Vector3d(samplePos.getX() + 0.5,
                    samplePos.getY() + 0.5, samplePos.getZ() + 0.5));
            currentSpeedMs = vel != null ? vel.length() : 0;
        } else {
            currentSpeedMs = 0;
        }
        currentAccelMs2 = ClientMountHandler.getCachedVehicleAccelMs2();

        double serverMass = ClientMountHandler.getVehicleMass();
        mass = serverMass > 0 ? serverMass : 2000.0;

        frictionDemandRatio = 0;
        int gripCount = 0;
        for (var s : susp) {
            frictionDemandRatio += s.getFrictionDemandRatio();
            gripCount++;
        }
        frictionDemandRatio = gripCount > 0 ? frictionDemandRatio / gripCount : 0;
        frictionPct = frictionDemandRatio * 100.0;

        buildLines();
    }

    private static void buildLines() {
        displayLines.clear();

        displayLines.add(line("debug.iac_p.overlay.mass", String.format("%,.0f kg", mass)));
        displayLines.add(line("debug.iac_p.overlay.torque", String.format("%.1f Nm", engineTorque)));
        displayLines.add(line("debug.iac_p.overlay.wheels", wheelsWithTire + "/" + totalWheels + " 轮着地"));
        displayLines.add(line("debug.iac_p.overlay.wheel_rpm", String.format("%,.0f RPM", avgWheelRpm)));
        displayLines.add(line("debug.iac_p.overlay.wheel_torque", String.format("%.1f Nm/轮", avgWheelTorque)));
        displayLines.add(line("debug.iac_p.overlay.current_speed", String.format("%.2f m/s  (%.1f km/h)",
                currentSpeedMs, currentSpeedMs * 3.6)));
        displayLines.add(Component.literal("§7加速度: §f%+.2f m/s²".formatted(currentAccelMs2)));
        displayLines.add(line("debug.iac_p.overlay.friction_demand", String.format("%.0f%%", frictionPct)));

        String upIcon = com.hainabaichuan75.iac_p.client.ClientEvents.debugThrottleUp ? "§a↑" : "§8↑";
        String downIcon = com.hainabaichuan75.iac_p.client.ClientEvents.debugThrottleDown ? "§a↓" : "§8↓";
        int dir = com.hainabaichuan75.iac_p.client.ClientEvents.debugLastThrottleDir;
        String dirStr = dir > 0 ? "§e+1" : dir < 0 ? "§e-1" : "§70";
        displayLines.add(Component.literal("§7输入: ").append(Component.literal(upIcon + " ")).append(Component.literal(downIcon + " ")).append(Component.literal("→ 方向 ")).append(Component.literal(dirStr)));
    }

    private static Component line(String labelKey, String value) {
        return Component.translatable(labelKey).append(": ").append(Component.literal(value));
    }
}
