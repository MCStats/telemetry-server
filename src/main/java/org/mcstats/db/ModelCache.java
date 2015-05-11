package org.mcstats.db;

import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

public interface ModelCache {

    /**
     * Loads a plugin from the cache with the given name
     *
     * @param name
     * @return
     */
    Plugin getPlugin(String name);

    /**
     * Loads a plugin from the cache with the given id
     *
     * @param id
     * @return
     */
    Plugin getPlugin(int id);

    /**
     * Stores a plugin in the cache
     *
     * @param plugin
     */
    void cachePlugin(Plugin plugin);

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