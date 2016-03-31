package org.mcstats.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.mcstats.util.Tuple;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServerPluginData {

    private final Server server;
    private String version;
    private int revision;

    /**
     * Unix timestamp of when this plugin last sent data for the server
     */
    private int lastSentData = -1;

    /**
     * Last set of custom data sent for the plugin/server combo
     */
    private ImmutableMap<String, Map<String, Long>> customData = ImmutableMap.of();

    /**
     * The version changes for this plugin/server combo
     *
     * TODO
     */
    private final Set<Tuple<String, String>> versionChanges = new HashSet<>();

    public ServerPluginData(Server server, String version, int revision) {
        this.server = server;
        this.version = version;
        this.revision = revision;
    }

    /**
     * Check if the server plugin has been lastSentData in the last 30 minutes
     *
     * @return true if this recently sent data; false otherwise
     */
    public boolean recentlyLastSentData() {
        return lastSentData > ((System.currentTimeMillis() / 1000) - 1800);
    }

    /**
     * Sets the time this has recently sent data to now.
     */
    public void setRecentlyLastDataTimeToNow() {
        lastSentData = (int) (System.currentTimeMillis() / 1000);
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
     * Gets the version changes for this data. Returned set is a copy and immutable.
     *
     * @return an immutable copy of the version changes
     */
    public Set<Tuple<String, String>> getVersionChanges() {
        return ImmutableSet.copyOf(versionChanges);
    }

    /**
     * Gets the custom data
     *
     * @return
     */
    public ImmutableMap<String, Map<String, Long>> getCustomData() {
        return customData;
    }

    public Server getServer() {
        return server;
    }

    public void setCustomData(ImmutableMap<String, Map<String, Long>> customData) {
        this.customData = customData;
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

}
