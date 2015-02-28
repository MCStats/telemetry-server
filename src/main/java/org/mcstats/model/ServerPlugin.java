package org.mcstats.model;

import org.mcstats.util.Tuple;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlugin {

    /**
     * The server this plugin belongs to
     */
    private final Server server;

    /**
     * The plugin this server has
     */
    private final Plugin plugin;

    /**
     * The version of the plugin this server is running
     */
    private String version = "";

    /**
     * The server's revision
     */
    private int revision = 0;

    /**
     * Unix timestamp of when it was last updated
     */
    private int updated = 0;

    /**
     * The last custom data sent to the server
     */
    private Map<Column, Long> customData = new ConcurrentHashMap<>();

    /**
     * The version changes for this plugin
     */
    public final Set<Tuple<String, String>> versionChanges = new HashSet<>();

    public ServerPlugin(Server server, Plugin plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ServerPlugin)) {
            return false;
        }

        ServerPlugin other = (ServerPlugin) o;
        return server.getUUID().equals(other.getServer().getUUID()) && plugin.getId() == other.plugin.getId();
    }

    @Override
    public int hashCode() {
        int hash = 23;
        hash *= 31 + server.getUUID().hashCode();
        hash *= 31 + plugin.getId();
        return hash;
    }

    /**
     * Check if the server plugin has been updated in the last 30 minutes
     *
     * @return
     */
    public boolean recentlyUpdated() {
        return updated > ((System.currentTimeMillis() / 1000) - 1800);
    }

    /**
     * Add a version change
     *
     * @param oldVersion the version being chnaged from
     * @param newVersion the version changing to
     */
    public void addVersionChange(String oldVersion, String newVersion) {
        versionChanges.add(new Tuple<>(oldVersion, newVersion));
    }

    /**
     * Get an unmodifiable list of the version changes
     * @return
     */
    public Set<Tuple<String, String>> getVersionChanges() {
        return Collections.unmodifiableSet(versionChanges);
    }

    /**
     * Clear the list of version changes
     */
    public void clearVersionChanges() {
        versionChanges.clear();
    }

    public Server getServer() {
        return server;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Map<Column, Long> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<Column, Long> customData) {
        this.customData = customData;
    }

}
