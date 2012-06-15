package org.mcstats.model;

import org.mcstats.MCStats;

public class PluginVersion {

    /**
     * The mcstats object
     */
    private MCStats mcstats;

    /**
     * The internal id
     */
    private int id;

    /**
     * The plugin this version is for
     */
    private Plugin plugin;

    /**
     * The versions tring
     */
    private String version;

    /**
     * When the version was created
     */
    private int created;

    public PluginVersion(MCStats mcstats, Plugin plugin) {
        this.mcstats = mcstats;
        this.plugin = plugin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PluginVersion)) {
            return false;
        }

        PluginVersion other = (PluginVersion) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }
}
