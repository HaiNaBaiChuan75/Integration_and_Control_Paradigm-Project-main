package com.hainabaichuan75.iac_p.data;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {

    private final String locale;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, IACP.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        switch (locale) {
            case "en_us" -> addEnUs();
            case "zh_cn" -> addZhCn();
        }
    }

    private void addEnUs() {
        add("block.iac_p.base_cabin", "Base Cabin");
        add("block.iac_p.shot_gun", "Shotgun Turret");
    }

    private void addZhCn() {
        add("block.iac_p.base_cabin", "基础舱室");
        add("block.iac_p.shot_gun", "霰弹枪炮塔");
    }
}
