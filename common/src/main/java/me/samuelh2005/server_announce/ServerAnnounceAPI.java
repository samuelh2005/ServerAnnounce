package me.samuelh2005.server_announce;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import me.samuelh2005.server_announce.Types.Server;
import me.samuelh2005.server_announce.Types.ServersResponse;

public class ServerAnnounceAPI {
    private final RawAPIClient apiClient;
    private final ServerCache serverCache;
    private final Logger logger;
    private final long cleanupIntervalMillis;

    private ServerAnnounceAPI(String baseUrl, long cleanupIntervalMillis, Logger logger) {
        this.apiClient = new RawAPIClient(baseUrl);
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        this.logger = logger;
        this.serverCache = new ServerCache(logger);
        Thread refreshThread = new Thread(new RefreshTask(this));
        refreshThread.setDaemon(true);
        refreshThread.start();
    }

    public static class Builder {
        private String baseUrl;
        private long cleanupIntervalMillis = 60000; // Default to 60 seconds
        private Logger logger = null;

        public Builder setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder setCleanupInterval(long cleanupIntervalMillis) {
            this.cleanupIntervalMillis = cleanupIntervalMillis;
            return this;
        }

        public Builder setLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public ServerAnnounceAPI build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalArgumentException("Base URL cannot be null or empty");
            }
            return new ServerAnnounceAPI(baseUrl, cleanupIntervalMillis, logger);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public ServerCache getServerCache() {
        return serverCache;
    }

    public void addListener(ServerEventListener listener) {
         serverCache.getListeners().add(listener);
    }

    public void removeListener(ServerEventListener listener) {
        serverCache.getListeners().remove(listener);
    }

    private static class RefreshTask implements Runnable {
        private final ServerAnnounceAPI api;

        public RefreshTask(ServerAnnounceAPI api) {
            this.api = api;
        }

        @Override
        public void run() {
            api.logger.info("Starting server refresh task with cleanup interval: {} ms", api.cleanupIntervalMillis);
            while (true) {
                long nowMillis = System.currentTimeMillis();
                long nowSeconds = nowMillis / 1000L;

                ServersResponse response;
                try {
                    api.logger.info("Fetching servers from API...");
                    response = api.apiClient.fetchServers();
                } catch (Exception e) {
                    // Don't wipe existing state just because a single fetch failed.
                    api.logger.error("Failed to fetch servers from API: {}", e);
                    continue;
                }

                Map<String, Server> newServers = response.servers();
                Map<String, Map<String, Server>> groups = response.groups();

                api.logger.info("Fetched {} servers and {} groups from API", newServers.size(), groups.size());

                List<Server> oldServers = api.serverCache.getServers();

                // 1. Remove servers that are no longer present in the response.
                for (Server server : oldServers) {
                    if (!newServers.containsKey(server.name())) {
                        api.serverCache.removeServer(server.name());
                    }
                    // Also check server groups
                    for (Map.Entry<String, Map<String, Server>> entry : groups.entrySet()) {
                        Map<String, Server> groupServers = entry.getValue();
                        if (!groupServers.containsKey(server.name())) {
                            api.serverCache.removeServer(server.name());
                        }
                    }
                }

                // 2. Add or update servers from the response.
                for (Server newServer : newServers.values()) {
                    if (newServer.expiry() < nowSeconds) {
                        // Skip expired servers.
                        continue;
                    }

                    // Always update the server, even if it already exists, to ensure the latest data is used.
                    api.serverCache.updateServer(newServer, null);
                }

                // 3. Update group memberships.
                for (Map.Entry<String, Map<String, Server>> entry : groups.entrySet()) {
                    String groupName = entry.getKey();
                    Map<String, Server> groupServers = entry.getValue();

                    for (Server groupServer : groupServers.values()) {
                        if (groupServer.expiry() < nowSeconds) {
                            // Skip expired servers.
                            continue;
                        }

                        // Always update the server, even if it already exists, to ensure group membership is correct.
                        api.serverCache.updateServer(groupServer, groupName);
                    }
                }

                // 4. Remove all expired servers.
                for (Server server : api.serverCache.getServers()) {
                    if (server.expiry() < nowSeconds) {
                        api.serverCache.removeServer(server.name());
                    }
                }

                // 5. Sleep for the cleanup interval before the next refresh.
                try {
                    Thread.sleep(api.cleanupIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}