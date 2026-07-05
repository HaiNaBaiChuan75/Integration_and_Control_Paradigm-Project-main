package com.hainabaichuan75.iac_p.client.screen;

import com.hainabaichuan75.iac_p.client.VehicleOrientationData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 载具朝向信息界面 —— 显示载具各方向悬挂数量统计。
 * <p>
 * 智能映射相关功能已移除（变速/引擎逻辑已废弃）。
 */
public class VehicleOrientationScreen extends Screen {

    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/demo_background" +
                                                                                     ".png");

    private final VehicleOrientationData data;
    private final UUID subLevelUUID;

    private static final int BG_W = 248;
    private static final int BG_H = 166;

    public VehicleOrientationScreen(VehicleOrientationData data, UUID subLevelUUID) {
        super(Component.translatable("screen.iac_p.vehicle_orientation.title"));
        this.data = data;
        this.subLevelUUID = subLevelUUID;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int cx = (this.width - BG_W) / 2;
        int cy = (this.height - BG_H) / 2;

        g.blit(BG, cx, cy, 0, 0, BG_W, BG_H, BG_W, BG_H);

        int tx = cx + 15;
        int ty = cy + 20;

        // ── 标题 ──
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.title"), tx, ty, 0xFFFFFF);
        ty += 16;

        if (data == null) {
            g.drawString(this.font, Component.literal("§cNo data"), tx, ty, 0xFFFFFF);
            return;
        }

        // ── 朝向统计 ──
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.north", data.north()), tx,
                ty, 0xAAAAAA);
        ty += 12;
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.south", data.south()), tx,
                ty, 0xAAAAAA);
        ty += 12;
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.east", data.east()), tx, ty,
                0xAAAAAA);
        ty += 12;
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.west", data.west()), tx, ty,
                0xAAAAAA);
        ty += 12;
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.total", data.total()), tx,
                ty, 0xFFFFFF);
        ty += 20;

        // ── 关闭提示 ──
        g.drawString(this.font, Component.translatable("screen.iac_p.vehicle_orientation.close_hint"), tx, ty,
                0x555555);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
