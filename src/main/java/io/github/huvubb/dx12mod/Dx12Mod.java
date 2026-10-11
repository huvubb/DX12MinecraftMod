package io.github.huvubb.dx12mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dx12Mod implements ModInitializer {
    public static final String MOD_ID = "dx12mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[DX12] {}. Press F3 after the window opens to see the verified backend.", Dx12Bootstrap.getStatusLine());
    }
}
