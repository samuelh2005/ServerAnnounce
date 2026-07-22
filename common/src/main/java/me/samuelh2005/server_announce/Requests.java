package me.samuelh2005.server_announce;

import java.util.Map;

public class Requests {
    public static record ServersResponse(Map<String, Server> servers, Map<String, Map<String, Server>> groups) {
    }

    public static ServersResponse fetchServers(String baseUrl) {
        // Implement the logic to fetch servers from the API using the baseUrl
        // This is a placeholder implementation and should be replaced with actual HTTP request logic
        return new ServersResponse(Map.of(), Map.of());
    }
}
