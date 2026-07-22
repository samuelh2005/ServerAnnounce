package me.samuelh2005.server_announce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.samuelh2005.server_announce.Types.Server;
import me.samuelh2005.server_announce.Types.ServersResponse;

public class ServerAnnounceAPI {
    private final String baseUrl;
    private final long cleanupIntervalMillis;

    // id -> Server: the single canonical store. Server.name() is the id.
    private final Map<String, Server> servers = new ConcurrentHashMap<>();
    // group -> set of server ids currently in that group
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();

    private ServerAnnounceAPI(String baseUrl, long cleanupIntervalMillis) {
        this.baseUrl = baseUrl;
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        Thread refreshThread = new Thread(new RefreshTask(this));
        refreshThread.setDaemon(true);
        refreshThread.start();
    }

    public static class Builder {
        private String baseUrl;
        private long cleanupIntervalMillis = 60000; // Default to 60 seconds

        public Builder setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder setCleanupInterval(long cleanupIntervalMillis) {
            this.cleanupIntervalMillis = cleanupIntervalMillis;
            return this;
        }

        public ServerAnnounceAPI build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalArgumentException("Base URL cannot be null or empty");
            }
            return new ServerAnnounceAPI(baseUrl, cleanupIntervalMillis);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Server getServer(String id) {
        return servers.get(id);
    }

    public List<String> getGroups() {
        return List.copyOf(groups.keySet());
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

    public void addListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServerEventListener listener) {
        listeners.remove(listener);
    }

    private void dispatch(List<ServerEvent> events) {
        for (ServerEvent event : events) {
            for (ServerEventListener listener : listeners) {
                listener.onServerEvent(event);
            }
        }
    }

    /** True if address/forcedHost match, ignoring expiry - i.e. "same server, maybe renewed". */
    private static boolean sameContent(Server a, Server b) {
        return a.address().equals(b.address())
                && Objects.equals(a.forcedHost(), b.forcedHost());
    }

    private static class RefreshTask implements Runnable {
        private final ServerAnnounceAPI api;

        public RefreshTask(ServerAnnounceAPI api) {
            this.api = api;
        }

        // 1. periodically remove expired servers, and drop them from any groups they were in
        // 2. fetch the latest servers/groups from the API and reconcile against the canonical store
        //
        // We only remove servers that have expired or are no longer returned by the API.
        // Servers that are still valid and unchanged are left alone. If only the expiry
        // changed, the record is swapped in place (records are immutable) with no event.
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(api.cleanupIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                ServersResponse response;
                try {
                    response = RawAPIClient.fetchServers(api.baseUrl);
                } catch (Exception e) {
                    // Don't wipe existing state just because a single fetch failed.
                    continue;
                }

                long now = System.currentTimeMillis();
                List<ServerEvent> events = new ArrayList<>();

                // --- 1. Reconcile the canonical id -> Server store ---
                // response.groups() nests full Server objects per the API shape; since ids are
                // globally unique, fold every server we see (flat list + every group listing)
                // into one incoming view before diffing against the canonical store once.
                Map<String, Server> incomingServers = new java.util.HashMap<>(response.servers());
                for (Map<String, Server> members : response.groups().values()) {
                    incomingServers.putAll(members);
                }

                Set<String> removedIds = mergeServers(api.servers, incomingServers, now, events);

                // --- 2. Reconcile group membership as plain id-set diffs ---
                Map<String, Map<String, Server>> incomingGroups = response.groups();

                for (Map.Entry<String, Map<String, Server>> groupEntry : incomingGroups.entrySet()) {
                    String groupName = groupEntry.getKey();
                    Set<String> incomingIds = groupEntry.getValue().keySet();
                    Set<String> existingIds =
                            api.groups.computeIfAbsent(groupName, g -> ConcurrentHashMap.newKeySet());

                    for (String id : incomingIds) {
                        if (existingIds.add(id)) {
                            events.add(new ServerEvent.GroupMemberAdded(groupName, id));
                        }
                    }
                    existingIds.removeIf(id -> {
                        boolean gone = !incomingIds.contains(id);
                        if (gone) events.add(new ServerEvent.GroupMemberRemoved(groupName, id));
                        return gone;
                    });
                }

                // Drop globally-removed/expired servers from every remaining group's set too.
                if (!removedIds.isEmpty()) {
                    for (Map.Entry<String, Set<String>> groupEntry : api.groups.entrySet()) {
                        String groupName = groupEntry.getKey();
                        Set<String> ids = groupEntry.getValue();
                        for (String id : removedIds) {
                            if (ids.remove(id)) {
                                events.add(new ServerEvent.GroupMemberRemoved(groupName, id));
                            }
                        }
                    }
                }

                // Whole groups no longer present in the response at all.
                Iterator<Map.Entry<String, Set<String>>> groupIt = api.groups.entrySet().iterator();
                while (groupIt.hasNext()) {
                    Map.Entry<String, Set<String>> entry = groupIt.next();
                    String groupName = entry.getKey();
                    if (!incomingGroups.containsKey(groupName)) {
                        for (String id : entry.getValue()) {
                            events.add(new ServerEvent.GroupMemberRemoved(groupName, id));
                        }
                        groupIt.remove();
                        events.add(new ServerEvent.GroupRemoved(groupName));
                    }
                }

                api.dispatch(events);
            }
        }

        /**
         * Diffs `incoming` against the canonical `store` and mutates it in place.
         * Add-then-remove ordering:
         *  - new id                          -> add, emit ServerAdded
         *  - existing id, content differs    -> replace, emit ServerUpdated
         *  - existing id, same content,
         *    expiry differs                  -> replace silently (expiry refresh, no event)
         *  - existing id, fully identical    -> untouched
         *  - anything left over missing from
         *    `incoming`, or expired          -> remove, emit ServerRemoved
         *
         * Returns the set of ids removed this cycle, so group membership can be pruned to match.
         */
        private static Set<String> mergeServers(Map<String, Server> store, Map<String, Server> incoming,
                                                 long now, List<ServerEvent> events) {
            for (Map.Entry<String, Server> entry : incoming.entrySet()) {
                String id = entry.getKey();
                Server newServer = entry.getValue();
                Server oldServer = store.get(id);

                if (oldServer == null) {
                    store.put(id, newServer);
                    events.add(new ServerEvent.ServerAdded(newServer));
                } else if (!sameContent(oldServer, newServer)) {
                    store.put(id, newServer);
                    events.add(new ServerEvent.ServerUpdated(oldServer, newServer));
                } else if (oldServer.expiry() != newServer.expiry()) {
                    store.put(id, newServer); // expiry refresh, no event
                }
            }

            Set<String> removedIds = new HashSet<>();
            Iterator<Map.Entry<String, Server>> it = store.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Server> entry = it.next();
                String id = entry.getKey();
                Server s = entry.getValue();
                if (!incoming.containsKey(id) || s.expiry() <= now) {
                    it.remove();
                    removedIds.add(id);
                    events.add(new ServerEvent.ServerRemoved(s));
                }
            }
            return removedIds;
        }
    }
}