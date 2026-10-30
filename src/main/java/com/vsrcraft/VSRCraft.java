package com.vsrcraft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VSRCraft implements ModInitializer {
    public static final String MOD_ID = "vsrcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("VSRCraft initializing!");
    }
}
