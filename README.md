# Server Announce

Server Announce is a distributed Minecraft server discovery service for private networks.

It provides a central registry that allows Minecraft servers and proxies to discover available backend servers through a REST API. The backend is written in Go and configured through YAML.

Server Announce is designed to work alongside proxy software such as Velocity, allowing servers to be added, removed, or replaced without requiring proxy restarts.

This project is intended for internal server infrastructure. It is not a public server list and does not provide player-facing discovery features.

## Who's it for?

1. Server operators who want to manage backend servers without restarting their proxies.
2. Minigame networks with ephemeral servers that need automatic discovery as instances are created and removed.
3. Platforms that dynamically create server instances, such as player island or game session systems.

## Who's it not for?

1. Public server lists, such as [Minecraft Server List](https://minecraft-server-list.com/) or [Minecraft-MP](https://minecraft-mp.com/). Server Announce does not provide player-facing listing, ranking, or discovery features.
2. Players looking for Minecraft servers. This project is an internal infrastructure component, not a server browser.
3. Developers looking for a public server discovery API. The client library is intended for private network integrations and backend services.

## Features TODO

- [ ] **Backend**[./backend](./backend/):
    - [x] Static server registration (config file)
    - [x] Config hot reload
    - [x] Server expiration
    - [x] Server grouping
    - [x] Registry query API
    - [x] Docker image
    - [ ] API Authentication
    - [ ] Server Provider model (the provider will produce servers, Config Server Provider)
    - [ ] Dynamic server registration (API Server Provider)
    - [ ] Server specific instance tokens
    - [ ] Server heartbeat (expiration++)
    - [ ] Server status+metadata (online/offline, player count, etc.)
    - [ ] Status Query API
    - [ ] HTTP Server Sent Events (SSE) to simplify client
    - [ ] Metrics API (Prometheus)
- [x] **Embeddable Client library** [./common](./common/):
    - [x] Event API for developers to listen for server changes
    - [x] Automatic server expiration and reconciliation
    - [x] Cache lookup APIs for manual discovery
    - [x] Configurable API properties
- [ ] **Game server platforms**:
    - [x] Fabric [./platforms/server/fabric](./platforms/server/fabric/)
    - [ ] Paper
    - *Forge will not be supported, Use NeoForge instead*
    - [x] NeoForge *Use the [Sinytra Connector](https://modrinth.com/mod/connector) NeoForge mod to run Server Announce on NeoForge*
- [ ] **Proxy platforms**:
    - [ ] Velocity
    - *BungeeCord will not be supported, Velocity is the preferred proxy for this project.*

Player facing game server integrations are not part of this mod and are instead provided by external third-party mods. This mod is intended to be used as a backend service for other mods, and does not provide any player facing features itself.

## License

This project is licensed under the [GNU Lesser General Public License v3.0](./LICENSE) (LGPL-3.0). See the LICENSE file for more details.
