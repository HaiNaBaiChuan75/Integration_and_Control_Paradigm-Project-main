package com.hainabaichuan75.iac_p.client;

import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 客户端骑乘交互限制 —— 游戏总线（Forge Event Bus）事件处理。
 * <p>
 * 骑乘时阻止玩家与世界交互（右键、左键、实体交互），
 * 并阻断键盘/鼠标输入对玩家实体的影响（WASD、跳跃、潜行）。
 * 玩家模型正常渲染，不隐藏手持物品。
 * <p>
 * 注意：此类通过 {@code NeoForge.EVENT_BUS.register(ClientMountGameHandler.class)}
 * 注册到游戏总线。
 */
public class ClientMountGameHandler {

    // ====== 骑乘时阻断移动输入 ======

    /**
     * 骑乘时清零移动输入，使 WASD/跳跃/潜行不影响玩家实体。
     * <p>
     * 按键仍被 {@link ClientEvents#sendVehicleControlInput(Minecraft)} 读取
     * 并发送到载具控制系统中，但不作用于玩家自身。
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (ClientMountHandler.isMounted()
                && event.getEntity() == Minecraft.getInstance().player) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    // ====== Seat 骑乘者身体跟随 ======

    /**
     * 骑乘 {@link IACPSeatEntity} 时，将座位的帧间偏航变化量应用到本地玩家身体，
     * 使玩家模型跟随 SubLevel 水平旋转。
     * <p>
     * 服务端已通过 seat tick 将 delta 应用到所有骑乘者并权威同步；
     * 但本地玩家（{@link net.minecraft.client.player.LocalPlayer}）的 yRot
     * 由鼠标输入覆盖，服务端同步值被忽略。此处额外修正。
     */
    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.getVehicle() instanceof IACPSeatEntity seat)) return;

        float yawDelta = seat.getLastYawDelta();
        if (yawDelta != 0) {
            mc.player.setYRot(mc.player.getYRot() + yawDelta);
            mc.player.setYHeadRot(mc.player.getYHeadRot() + yawDelta);
            mc.player.yBodyRot = mc.player.yBodyRot + yawDelta;
        }
    }

    // ====== 骑乘时禁止交互（客户端即时反馈） ======

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ClientMountHandler.isMounted()
                && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ClientMountHandler.isMounted()
                && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (ClientMountHandler.isMounted()
                && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (ClientMountHandler.isMounted()
                && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    // ====== 隐藏手部渲染 ======

}
