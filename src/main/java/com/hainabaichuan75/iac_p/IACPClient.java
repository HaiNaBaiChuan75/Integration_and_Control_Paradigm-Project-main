package com.hainabaichuan75.iac_p;

import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlockModel;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockEntity;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockModel;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockRenderLayer;
import com.hainabaichuan75.iac_p.core.part.PartRenderer;
import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = IACP.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = IACP.MODID)
public class IACPClient {

    public IACPClient(ModContainer container, IEventBus modEventBus) {

    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                IACPBlockEntities.BASE_CABIN.get(), ctx -> new PartRenderer<>(new BaseCabinBlockModel())
        );

        event.registerBlockEntityRenderer(IACPBlockEntities.SHOT_GUN.get(), ctx -> {
            PartRenderer<ShotGunBlockEntity> renderer = new PartRenderer<>(new ShotGunBlockModel());
            renderer.addRenderLayer(new ShotGunBlockRenderLayer(renderer));
            return renderer;
        });
    }
}
