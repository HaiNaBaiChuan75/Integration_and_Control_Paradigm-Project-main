package com.hainabaichuan75.iac_p.data;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.registry.IACPBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, IACP.MODID, exFileHelper);
    }

    @Override
    protected void registerModels() {
        // BaseCabin 物品模型 → 沿用中心方块模型
        withExistingParent(IACPBlocks.BASE_CABIN.getId().getPath(), modLoc("block/base_cabin"));
    }
}
