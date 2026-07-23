package me.samuelh2005.server_announce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import me.samuelh2005.server_announce.Types.Server;

public class ServerCache {
    private final Logger logger;

    public ServerCache(Logger logger) {
        this.logger = logger;
    }

    // id -> Server: the single canonical store. Server.name() is the id.
    private final Map<String, Server> servers = new ConcurrentHashMap<>();
    // group -> set of server ids currently in that group
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();

    public Server getServer(String id) {
        return servers.get(id);
    }

    public List<String> getGroups() {
        return List.copyOf(groups.keySet());
    }

    public List<Server> getServers() {
        return List.copyOf(servers.values());
    }

    List<ServerEventListener> getListeners() {
        return listeners;
    }

    /** Resolves current group membership to actual Server objects via the canonical store. */
    public List<Server> getGroupMembers(String group) {
        Set<String> ids = groups.get(group);
        if (ids == null) return List.of();
        List<Server> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Server s = servers.get(id);
            if (s != null) result.add(s);
        }
        return result;
    }

    public @Nullable String getGroupForServer(Server server) {
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            String group = entry.getKey();
            Set<String> members = entry.getValue();
            if (members.contains(server.name())) {
                return group;
            }
        }
        return null;
    }

    private void dispatch(ServerEvent event) {
        for (ServerEventListener listener : this.listeners) {
            listener.onServerEvent(event);
        }
    }

    void removeServer(String name) {
        Server removed = servers.remove(name);
        if (removed != null) {
            logger.info("Server removed: {}", removed);
            dispatch(new ServerEvent.ServerRemoved(removed));
        }

        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            String group = entry.getKey();
            Set<String> members = entry.getValue();
            if (members.remove(name)) {
                logger.info("Server {} removed from group {}", name, group);
                dispatch(new ServerEvent.GroupMemberRemoved(group, name));
                if (members.isEmpty()) {
                    groups.remove(group);
                    logger.info("Group removed: {}", group);
                    dispatch(new ServerEvent.GroupRemoved(group));
                }
            }
        }
    }

    void updateServer(
        Server newServer,
        @Nullable String group
    ) {
        logger.info("Updating server: {} (group: {})", newServer, group);
        // If the new server is the same as an existing, simply return and do nothing.
        Server oldServer = servers.get(newServer.name());
        if (oldServer != null && oldServer.equals(newServer)) {
            return;
        }
        logger.info("Server {} is new or updated. Old: {}, New: {}", newServer.name(), oldServer, newServer);

        // Dispatch the appropriate event for the server addition or update.
        if (oldServer == null) {
            servers.put(newServer.name(), newServer);
            logger.info("Server added: {}", newServer);
            dispatch(new ServerEvent.ServerAdded(newServer));
        } else {
            servers.put(newServer.name(), newServer);
            logger.info("Server updated: {}", newServer);
            dispatch(new ServerEvent.ServerUpdated(oldServer, newServer));
        }

        if (group != null) {
            // Remove from any previous group.
            for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
                String existingGroup = entry.getKey();
                Set<String> members = entry.getValue();
                if (!existingGroup.equals(group) && members.remove(newServer.name())) {
                    logger.info("Server {} removed from group {}", newServer.name(), existingGroup);
                    dispatch(new ServerEvent.GroupMemberRemoved(existingGroup, newServer.name()));
                    if (members.isEmpty()) {
                        groups.remove(existingGroup);
                        logger.info("Group removed: {}", existingGroup);
                        dispatch(new ServerEvent.GroupRemoved(existingGroup));
                    }
                }
            }

            // Add to the new group.
            groups.computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet()).add(newServer.name());
            dispatch(new ServerEvent.GroupMemberAdded(group, newServer.name()));
        }
    }
}
