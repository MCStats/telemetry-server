package org.mcstats.db;

import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

public interface ModelCache {

    /**
     * Loads a graph from the cache
     *
     * @param plugin
     * @param name
     * @return
     */
    Graph getPluginGraph(Plugin plugin, String name);

    /**
     * Loads a graph from the cache
     *
     * @param plugin
     * @param id
     * @return
     */
    Graph getPluginGraph(Plugin plugin, int id);

    /**
     * Caches the given graph for the plugin. It must come from the database.
     *
     * @param plugin
     * @param graph
     */
    void cachePluginGraph(Plugin plugin, Graph graph);

}
