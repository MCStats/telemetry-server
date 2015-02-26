package org.mcstats.model;

import org.mcstats.MCStats;
import org.mcstats.db.Savable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Savable {

    /**
     * The MCStats object
     */
    private final MCStats mcstats;

    /**
     * The server's id
     */
    private int id;

    /**
     * The server's guid
     */
    private String guid;

    /**
     * The server's country
     */
    private String country = "";

    /**
     * The amount of players currently on the server
     */
    private int players = 0;

    /**
     * The server's software version
     */
    private String serverVersion = "";

    /**
     * The name of the OS the server is running
     */
    private String osname = "";

    /**
     * The OS arch the server is running on
     */
    private String osarch = "";

    /**
     * The version of OS the server is running
     */
    private String osversion = "";

    /**
     * The server's java name (e.g 1.5)
     */
    private String java_name = "";

    /**
     * The server's version of Java (e.g 0_10)
     */
    private String java_version = "";

    /**
     * How many cores the server has
     */
    private int cores = 0;

    /**
     * If the server is in online mode or not
     */
    private int online_mode = 0;

    /**
     * Unix timestamp of when the server was created
     */
    private int created = 0;

    /**
     * The software the server is running
     */
    private String serverSoftware = "";

    /**
     * The minecraft version the server is
     */
    private String minecraftVersion = "";

    /**
     * If the server was modified
     */
    private boolean modified = false;

    /**
     * If the plugin has been queued to save
     */
    private boolean queuedForSave = false;

    /**
     * Violation count
     */
    private int violations = 0;

    /**
     * If the server was blacklisted or not already
     */
    private boolean blacklisted = false;

    /**
     * A map of all of the plugins this server is known to have
     */
    private final Map<Plugin, ServerPlugin> plugins = new ConcurrentHashMap<>();

    /**
     * Unix timestamp of when it last sent data
     */
    private int lastSentData;

    public Server(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    /**
     * Check if the server has sent data in the last 30 minutes
     *
     * @return
     */
    public boolean recentlySentData() {
        return lastSentData > ((System.currentTimeMillis() / 1000) - 1800);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Server)) {
            return false;
        }

        Server other = (Server) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * @param version
     */
    public void addVersionHistory(PluginVersion version) {
        mcstats.getDatabase().addPluginVersionHistory(this, version);
    }

    /**
     * Get all of the plugins that are on the server
     *
     * @return
     */
    public Map<Plugin, ServerPlugin> getPlugins() {
        return Collections.unmodifiableMap(plugins);
    }

    /**
     * Get the ServerPlugin object for the given plugin
     *
     * @param plugin
     * @return
     */
    public ServerPlugin getPlugin(Plugin plugin) {
        return plugins.get(plugin);
    }

    /**
     * Add a server plugin to this server
     *
     * @param serverPlugin
     */
    public void addPlugin(ServerPlugin serverPlugin) {
        if (serverPlugin == null) {
            throw new IllegalArgumentException("Server plugin cannot be null");
        }
        if (serverPlugin.getServer() != this) {
            throw new IllegalArgumentException("Server plugin must be assigned to the server it belongs to");
        }

        plugins.put(serverPlugin.getPlugin(), serverPlugin);
        mcstats.notifyServerPlugin(serverPlugin);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        modified = true;
    }

    public String getGUID() {
        return guid;
    }

    public void setGUID(String guid) {
        this.guid = guid;
        modified = true;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
        modified = true;
    }

    public int getPlayers() {
        return players;
    }

    public void setPlayers(int players) {
        this.players = players;
        modified = true;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
        modified = true;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
        modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public int getViolationCount() {
        return violations;
    }

    public void setViolationCount(int violations) {
        this.violations = violations;
    }

    public void incrementViolations() {
        violations ++;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public String getOSName() {
        return osname;
    }

    public int getLastSentData() {
        return lastSentData;
    }

    public void setLastSentData(int lastSentData) {
        this.lastSentData = lastSentData;
    }

    public void setOSName(String osname) {
        this.osname = osname;
        modified = true;
    }

    public String getOSArch() {
        return osarch;
    }

    public void setOSArch(String osarch) {
        this.osarch = osarch;
        modified = true;
    }

    public String getOSVersion() {
        return osversion;
    }

    public void setOSVersion(String osversion) {
        this.osversion = osversion;
        modified = true;
    }

    public int getCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
        modified = true;
    }

    public int getOnlineMode() {
        return online_mode;
    }

    public void setOnlineMode(int online_mode) {
        this.online_mode = online_mode;
        modified = true;
    }

    public String getJavaName() {
        return java_name;
    }

    public void setJavaName(String java_name) {
        this.java_name = java_name;
        modified = true;
    }

    public String getJavaVersion() {
        return java_version;
    }

    public void setJavaVersion(String java_version) {
        this.java_version = java_version;
        modified = true;
    }

    public void resetQueuedStatus() {
        queuedForSave = false;
    }

    public void save() {
        if (queuedForSave) {
            modified = false;
            return;
        }

        if (modified) {
            mcstats.getDatabaseQueue().offer(this);
            modified = false;
            queuedForSave = true;
        }
    }

    public void saveNow() {
        mcstats.getDatabase().saveServer(this);
        modified = false;
        queuedForSave = false;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
        modified = true;
    }

    public String getServerSoftware() {
        return serverSoftware;
    }

    public void setServerSoftware(String serverSoftware) {
        this.serverSoftware = serverSoftware;
        modified = true;
    }

}
