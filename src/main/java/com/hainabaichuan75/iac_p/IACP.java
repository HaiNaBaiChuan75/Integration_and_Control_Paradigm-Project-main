package com.hainabaichuan75.iac_p;

import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import com.hainabaichuan75.iac_p.vehicle.VehicleSystems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static com.hainabaichuan75.iac_p.registry.IACPBlocks.BLOCKS;
import static com.hainabaichuan75.iac_p.registry.IACPItems.ITEMS;

@Mod(IACP.MODID)
public class IACP {

    public static final String MODID = "iac_p";
    public static final Logger LOGGER = LogUtils.getLogger();


    public IACP(IEventBus modEventBus, ModContainer modContainer) {
        this.register(modEventBus);
    }

    public void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        IACPBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        VehicleSystems.registerAll();
    }
}
