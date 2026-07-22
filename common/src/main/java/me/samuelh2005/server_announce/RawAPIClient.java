package me.samuelh2005.server_announce;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import me.samuelh2005.server_announce.Types.Server;
import me.samuelh2005.server_announce.Types.ServersResponse;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RawAPIClient {
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RawAPIClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    // Wire-format DTOs matching the Go JSON exactly.
    @JsonIgnoreProperties(ignoreUnknown = true)
    static record ServerResponseDto(
            String name,
            String address,
            @JsonProperty("forced_host") String forcedHost,
            long expiry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record ServersResponseDto(
            Map<String, List<ServerResponseDto>> groups,
            List<ServerResponseDto> servers) {
    }

    public ServersResponse fetchServers() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/servers")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "{}";
            ServersResponseDto dto = objectMapper.readValue(body, ServersResponseDto.class);

            Map<String, Server> servers = new HashMap<>();
            if (dto.servers() != null) {
                for (ServerResponseDto s : dto.servers()) {
                    servers.put(s.name(), toServer(s));
                }
            }

            Map<String, Map<String, Server>> groups = new HashMap<>();
            if (dto.groups() != null) {
                for (Map.Entry<String, List<ServerResponseDto>> entry : dto.groups().entrySet()) {
                    Map<String, Server> groupServers = new HashMap<>();
                    for (ServerResponseDto s : entry.getValue()) {
                        groupServers.put(s.name(), toServer(s));
                    }
                    groups.put(entry.getKey(), groupServers);
                }
            }

            return new ServersResponse(servers, groups);
        }
    }

    private static Server toServer(ServerResponseDto dto) {
        return new Server(dto.name(), dto.address(), dto.forcedHost(), dto.expiry());
    }
}