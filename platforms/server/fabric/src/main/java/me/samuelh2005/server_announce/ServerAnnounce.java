package me.samuelh2005.server_announce;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerAnnounce implements ModInitializer {
	public static final String MOD_ID = "server-announce";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ServerAnnounceAPI INSTANCE;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Server Announce Mod");

		ServerAnnounceModConfig.HANDLER.load();

		ServerAnnounceModConfig config = ServerAnnounceModConfig.HANDLER.instance();

		INSTANCE = ServerAnnounceAPI.builder()
				.setBaseUrl(config.baseUrl)
				.setLogger(LOGGER)
				.setCleanupInterval(config.cleanupIntervalMillis)
				.build();

		LOGGER.info("Server Announce Mod Initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
