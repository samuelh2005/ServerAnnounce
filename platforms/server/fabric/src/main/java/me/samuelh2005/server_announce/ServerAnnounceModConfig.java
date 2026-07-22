package me.samuelh2005.server_announce;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import static me.samuelh2005.server_announce.ServerAnnounce.MOD_ID;

public class ServerAnnounceModConfig {
    public static ConfigClassHandler<ServerAnnounceModConfig> HANDLER = ConfigClassHandler.createBuilder(ServerAnnounceModConfig.class)
        .id(Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID))
        .serializer(config -> GsonConfigSerializerBuilder.create(config)
            .setPath(FabricLoader.getInstance().getConfigDir().resolve("server_announce.json5"))
            .setJson5(true)
            .build())
        .build();

    @SerialEntry(comment = "The base URL of the server list API. This should point to an instance of the Server Announce backend.")
    public String baseUrl = "https://example.com/v1/";

    @SerialEntry(comment = "The interval in milliseconds at which the server list will be cleaned up. This is the time between cleanup tasks.")
    public long cleanupIntervalMillis = 60000;
}
