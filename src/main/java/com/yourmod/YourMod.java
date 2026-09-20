package com.yourmod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YourMod implements ModInitializer {
    public static final String MOD_ID = "dx12mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[DX12] Mod loaded!");
        LOGGER.info("[DX12] DX12 renderer mod initialized");
        LOGGER.info("[DX12] Waiting for Minecraft window...");
    }
}