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
