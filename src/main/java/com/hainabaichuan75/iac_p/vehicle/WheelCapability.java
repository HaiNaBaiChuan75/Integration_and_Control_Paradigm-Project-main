package com.hainabaichuan75.iac_p.vehicle;

public interface WheelCapability extends CapabilityProviderBlock.VehicleCapability {
    double getVerticalLoad();

    void driving(double power);

    void steering(double targetDegree);
}
