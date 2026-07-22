package me.samuelh2005.server_announce;

/**
 * A single change detected during a refresh cycle.
 * Server add/remove/update are global (servers are a single canonical store).
 * Group events are pure membership changes over that store, keyed by server id.
 */
public sealed interface ServerEvent {
    record ServerAdded(Server server) implements ServerEvent {}
    record ServerRemoved(Server server) implements ServerEvent {}
    record ServerUpdated(Server oldServer, Server newServer) implements ServerEvent {}

    record GroupMemberAdded(String group, String serverId) implements ServerEvent {}
    record GroupMemberRemoved(String group, String serverId) implements ServerEvent {}
    record GroupRemoved(String group) implements ServerEvent {}
}