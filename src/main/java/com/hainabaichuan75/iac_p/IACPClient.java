package com.hainabaichuan75.iac_p;

import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlockRenderer;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockRenderer;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestRenderer;
import com.hainabaichuan75.iac_p.client.*;
import com.hainabaichuan75.iac_p.client.renderer.AxisLineRenderer;
import com.hainabaichuan75.iac_p.client.renderer.BulletTrailRenderer;
import com.hainabaichuan75.iac_p.content.blocks.test_controller.TestControllerRenderer;
import com.hainabaichuan75.iac_p.ecs.system.VehicleSystemRegistry;
import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import com.hainabaichuan75.iac_p.index.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = IACP.MODID, dist = Dist.CLIENT)
public class IACPClient {

    public IACPClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册客户端 VehicleSystem
        VehicleSystemRegistry.registerClientSystems();

        // 注册按键映射
        modEventBus.addListener(this::registerKeyMappings);

        // 注册 BlockEntity 渲染器
        modEventBus.addListener(this::registerRenderers);

        // 将客户端事件处理器注册到游戏总线
        NeoForge.EVENT_BUS.register(ClientMountGameHandler.class);
        NeoForge.EVENT_BUS.register(ClientMountHandler.class);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.register(VehicleDebugOverlay.class);
        NeoForge.EVENT_BUS.register(WeaponOverlay.class);
        NeoForge.EVENT_BUS.register(AxisLineRenderer.class);
        NeoForge.EVENT_BUS.register(BulletTrailRenderer.class);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientEvents.getVehicleConfigKey());
        event.register(ClientEvents.getRaycastFireKey());
        event.register(ClientEvents.getDebugGearKey());
        event.register(ClientEvents.getStationaryCamKey());
        event.register(ClientEvents.getDismountKey());
        event.register(ClientEvents.getVehicleCameraKey());
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // IACP 座位实体（不可见）
        event.registerEntityRenderer(ModEntities.IACP_SEAT.get(), IACPSeatEntity.Render::new);

        event.registerBlockEntityRenderer(ModBlockEntityTypes.SUSPENSION_TEST.get(),
                SuspensionTestRenderer::new);

        // GeckoLib 测试控制器
        event.registerBlockEntityRenderer(ModBlockEntityTypes.TEST_CONTROLLER.get(),
                TestControllerRenderer::new);

        // GeckoLib 基础座舱（静态骨骼模型）
        event.registerBlockEntityRenderer(ModCockpitBlockEntityTypes.BASE_CABIN.get(),
                BaseCabinBlockRenderer::new);

        // GeckoLib 霰弹枪炮塔
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SHOTGUN_TURRET.get(),
                ShotGunBlockRenderer::new);
    }
}
