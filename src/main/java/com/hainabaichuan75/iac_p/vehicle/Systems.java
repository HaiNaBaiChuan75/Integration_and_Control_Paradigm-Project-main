package com.hainabaichuan75.iac_p.vehicle;

import java.util.ArrayList;
import java.util.List;

public class Systems {
    public static final List<System> SYSTEMS = new ArrayList<>();

    private Systems() {}

    public static void registerAll() {
        registerSystem(new RandomAimSystem());
    }

    public static void registerSystem(System system) {
        SYSTEMS.add(system);
    }
}
