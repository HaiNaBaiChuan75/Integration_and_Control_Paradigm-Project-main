package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface VehicleTickSystem {
    default void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {}
}
