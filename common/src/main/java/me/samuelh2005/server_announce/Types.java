package me.samuelh2005.server_announce;

import java.util.Map;

public class Types {
    public static record ServersResponse(Map<String, Server> servers, Map<String, Map<String, Server>> groups) {
    }

    public static record Server(String name, String address, String forcedHost, long expiry) {
    }
}
