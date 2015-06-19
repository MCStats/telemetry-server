package org.mcstats.model;

import org.mcstats.db.Database;
import org.mcstats.db.ModelCache;
import org.mcstats.db.Savable;

import javax.inject.Inject;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Plugin implements Savable {

    /**
     * The plugin's id
     */
    private int id;

    /**
     * The plugin's name
     */
    private String name;

    /**
     * The plugin type
     */
    private String type;

    /**
     * If the plugin is hidden
     */
    private boolean hidden;

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
    private Date createdAt;

    /**
     * When a server last used this plugin
     */
    private Date updatedAt;

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
    private Map<String, PluginGraph> graphs = new ConcurrentHashMap<>();

    private final Database database;
    private final ModelCache modelCache;

    @Inject
    public Plugin(Database database, ModelCache modelCache) {
        this.database = database;
        this.modelCache = modelCache;
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
    public PluginGraph getGraph(String name) {
        if (graphs.containsKey(name)) {
            return graphs.get(name);
        }

        PluginGraph graph = modelCache.getPluginGraph(this, name);

        if (graph == null) {
            graph = database.loadGraph(this, name);

            if (graph == null) {
                graph = database.createGraph(this, name);
            }

            if (graph != null) {
                modelCache.cachePluginGraph(this, graph);
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
        return (updatedAt.getTime() / 1000) > (((int) System.currentTimeMillis() / 1000) - 1800);
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

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void save() {
        if (queuedForSave) {
            modified = false;
            return;
        }

        if (modified) {
            database.saveLater(this);
            modified = false;
            queuedForSave = true;
        }
    }

    public void saveNow() {
        if (modified) {
            database.savePlugin(this);
            modified = false;
            queuedForSave = false;
        }
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
