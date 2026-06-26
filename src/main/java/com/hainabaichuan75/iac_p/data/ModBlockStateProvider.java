package com.hainabaichuan75.iac_p.data;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.registry.IACPBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, IACP.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        baseCabin();
    }

    // ============================
    //  BaseCabin 舱室
    // ============================

    private void baseCabin() {
        // 单方块模型
        ModelFile model = models().cubeAll("base_cabin", modLoc("block/composter_top"));

        getVariantBuilder(IACPBlocks.BASE_CABIN.get()).forAllStates(state -> {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

            int yRot = switch (facing) {
                case NORTH -> 0;
                case EAST  -> 90;
                case SOUTH -> 180;
                case WEST  -> 270;
                default -> 0;
            };

            return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationY(yRot)
                    .build();
        });
    }
}
