package me.samuelh2005.server_announce;

import java.util.EventListener;

/** Single-method, functional, mainstream (java.util.EventListener style). */
public interface ServerAnnounceListener extends EventListener {
    void onServerEvent(ServerEvent event);
}
