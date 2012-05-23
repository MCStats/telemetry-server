package org.mcstats.model;

import org.mcstats.MCStats;
import org.mcstats.sql.Savable;

import java.util.HashMap;
import java.util.Map;

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
     * If this plugin was modified
     */
    private boolean modified = false;

    /**
     * Map of the graphs for the plugin
     */
    private Map<String, Graph> graphs = new HashMap<String, Graph>();

    /**
     * Map of the plugin versions by their database id
     */
    private Map<Integer, PluginVersion> versionsById = new HashMap<Integer, PluginVersion>();

    /**
     * Map of the plugin versions by their string name
     */
    private Map<String, PluginVersion> versionsByName = new HashMap<String, PluginVersion>();

    public Plugin(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    /**
     * Get a graph using its name if it is already loaded
     *
     * @param name
     * @return
     */
    public Graph getGraph(String name) {
        return graphs.get(name);
    }

    /**
     * Add a graph to the plugin
     *
     * @param graph
     */
    public void addGraph(Graph graph) {
        graphs.put(graph.getName(), graph);
    }

    /**
     * Get a plugin version by its id
     *
     * @param id
     * @return
     */
    public PluginVersion getVersionById(int id) {
        return versionsById.get(id);
    }

    /**
     * Get a plugin version by its name
     *
     * @param name
     * @return
     */
    public PluginVersion getVersionByName(String name) {
        return versionsByName.get(name);
    }

    /**
     * Add a version to the plugin
     *
     * @param version
     */
    public void addVersion(PluginVersion version) {
        versionsById.put(version.getId(), version);
        versionsByName.put(version.getVersion(), version);
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
        mcstats.getDatabase().savePlugin(this);
        modified = false;
    }
}
