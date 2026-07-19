package com.hainabaichuan75.iac_p.vehicle.provider;

/**
 * 引擎能力 —— 使 BE 可报告当前扭矩与转速输出（只读）。
 */
public interface EngineProvider {

    double getTorque();

    double getMaxTorque();

    double getRpm();

    double getMaxRpm();
}
