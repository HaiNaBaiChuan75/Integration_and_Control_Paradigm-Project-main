package com.hainabaichuan75.iac_p.registry;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public abstract class IACPItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IACP.MODID);

    public static final DeferredItem<BlockItem> BASE_CABIN = ITEMS.registerSimpleBlockItem(IACPBlocks.BASE_CABIN);
}
