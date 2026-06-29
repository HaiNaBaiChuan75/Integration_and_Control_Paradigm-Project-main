package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface VehicleClientSystem {
    default void onTick(@NotNull ClientSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {}
}
