package com.hainabaichuan75.iac_p.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public interface CapabilityProviderBlock {
    @NotNull CapabilityProviderBlock.VehicleCapability getCapability(@NotNull BlockState state,
                                                                     @NotNull ServerLevel level, @NotNull BlockPos pos);

    interface VehicleCapability {
        VehicleCapability NULL_CAPABILITY = new VehicleCapability() {};

    }
}
