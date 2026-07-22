package me.samuelh2005.server_announce;

public record Server(String name, String address, String forcedHost, long expiry) {
}
