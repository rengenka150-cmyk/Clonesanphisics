package com.example;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Clones_on_phisics implements ModInitializer {
	public static final String MOD_ID = "clones_on_phisics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Clones on Physics loaded!");
	}
}
