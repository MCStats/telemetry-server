package org.mcstats.model;

import org.mcstats.MCStats;
import org.mcstats.sql.Savable;

public class ServerPlugin implements Savable {

    /**
     * The MCStats object
     */
    private final MCStats mcstats;

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
    private String version;

    /**
     * Unix timestamp of when it was last updated
     */
    private int updated;

    /**
     * If this was modified
     */
    private boolean modified;

    public ServerPlugin(MCStats mcstats, Server server, Plugin plugin) {
        this.mcstats = mcstats;
        this.server = server;
        this.plugin = plugin;
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
        modified = true;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
        modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public void save() {
        if (modified) {
            mcstats.getDatabaseQueue().offer(this);
            modified = false;
        }
    }

    public void saveNow() {
        mcstats.getDatabase().saveServerPlugin(this);
        modified = false;
    }
}
