package org.mcstats.model;

import org.apache.commons.lang3.Validate;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    /**
     * The server's unique id
     */
    private final String id;

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
    private int lastSentData = (int) (System.currentTimeMillis() / 1000L);

    public Server(String guid) {
        this.id = guid;
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
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
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
        Validate.notNull(serverPlugin);
        Validate.isTrue(serverPlugin.getServer() ==  this);

        plugins.put(serverPlugin.getPlugin(), serverPlugin);
    }

    public String getUniqueId() {
        return id;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getPlayers() {
        return players;
    }

    public void setPlayers(int players) {
        this.players = players;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
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
    }

    public String getOSArch() {
        return osarch;
    }

    public void setOSArch(String osarch) {
        this.osarch = osarch;
    }

    public String getOSVersion() {
        return osversion;
    }

    public void setOSVersion(String osversion) {
        this.osversion = osversion;
    }

    public int getCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
    }

    public int getOnlineMode() {
        return online_mode;
    }

    public void setOnlineMode(int online_mode) {
        this.online_mode = online_mode;
    }

    public String getJavaName() {
        return java_name;
    }

    public void setJavaName(String java_name) {
        this.java_name = java_name;
    }

    public String getJavaVersion() {
        return java_version;
    }

    public void setJavaVersion(String java_version) {
        this.java_version = java_version;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getServerSoftware() {
        return serverSoftware;
    }

    public void setServerSoftware(String serverSoftware) {
        this.serverSoftware = serverSoftware;
    }

}
