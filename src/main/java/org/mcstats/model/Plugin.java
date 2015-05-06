package org.mcstats.model;

import org.mcstats.MCStats;
import org.mcstats.db.ModelCache;
import org.mcstats.db.Savable;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Plugin implements Savable {

    /**
     * The MCStats object
     */
    private final MCStats mcstats;

    /**
     * The plugin's id
     */
    private int id;

    /**
     * This plugin's parent, that data should be forwarded to instead
     */
    private int parent;

    /**
     * The plugin's name
     */
    private String name;

    /**
     * The plugin's authors
     */
    private String authors;

    /**
     * If the plugin is hidden
     */
    private int hidden;

    /**
     * The total amount of server startups the plugin has received
     */
    private int globalHits;

    /**
     * The plugin's rank
     */
    private int rank;

    /**
     * The plugins last rank
     */
    private int lastRank;

    /**
     * The epoch the rank last changed at
     */
    private int lastRankChange;

    /**
     * The unix epoch the plugin was created at
     */
    private int created;

    /**
     * When a server last used this plugin
     */
    private int lastUpdated;

    /**
     * The number of servers in the last 30 minutes (ish)
     */
    private int serverCount30;

    /**
     * If this plugin was modified
     */
    private boolean modified = false;

    /**
     * If the plugin has been queued to save
     */
    private boolean queuedForSave = false;

    /**
     * Map of the graphs for the plugin
     */
    private Map<String, Graph> graphs = new ConcurrentHashMap<>();

    public Plugin(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Plugin)) {
            return false;
        }

        Plugin other = (Plugin) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * Get a graph using its name. It will be loaded or created if it does not exist.
     *
     * @param name
     * @return
     */
    public Graph getGraph(String name) {
        if (graphs.containsKey(name)) {
            return graphs.get(name);
        }

        ModelCache cache = mcstats.getModelCache();

        Graph graph = cache.getPluginGraph(this, name);

        if (graph == null) {
            graph = mcstats.getDatabase().loadGraph(this, name);

            if (graph == null) {
                graph = mcstats.getDatabase().createGraph(this, name);
            }

            if (graph != null) {
                cache.cachePluginGraph(this, graph);
            }
        }

        graphs.put(name, graph);
        return graph;
    }

    /**
     * Check if the plugin has been updated in the last 30 minutes
     *
     * @return
     */
    public boolean recentlyUpdated() {
        return lastUpdated > (((int) System.currentTimeMillis() / 1000) - 1800);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        modified = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        modified = true;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
        modified = true;
    }

    public int getHidden() {
        return hidden;
    }

    public void setHidden(int hidden) {
        this.hidden = hidden;
        modified = true;
    }

    public int getGlobalHits() {
        return globalHits;
    }

    public void setGlobalHits(int globalHits) {
        this.globalHits = globalHits;
        modified = true;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
        modified = true;
    }

    public int getLastRank() {
        return lastRank;
    }

    public void setLastRank(int lastRank) {
        this.lastRank = lastRank;
        modified = true;
    }

    public int getLastRankChange() {
        return lastRankChange;
    }

    public void setLastRankChange(int lastRankChange) {
        this.lastRankChange = lastRankChange;
        modified = true;
    }

    public int getServerCount30() {
        return serverCount30;
    }

    public void setServerCount30(int serverCount30) {
        this.serverCount30 = serverCount30;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
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
        mcstats.getDatabase().savePlugin(this);
        modified = false;
        queuedForSave = false;
    }

    public int getParent() {
        return parent;
    }

    public void setParent(int parent) {
        this.parent = parent;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
        modified = true;
    }
}
