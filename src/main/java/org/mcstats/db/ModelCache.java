package org.mcstats.db;

import org.mcstats.model.Plugin;
import org.mcstats.model.PluginGraph;

import java.util.Map;

/**
 * A cache for models. All operations must be thread-safe.
 */
public interface ModelCache {

    /**
     * Gets the plugin id for the given plugin
     *
     * @param name
     * @return
     */
    int getPluginId(String name);

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
    PluginGraph getPluginGraph(Plugin plugin, String name);

    /**
     * Loads a graph from the cache
     *
     * @param plugin
     * @param id
     * @return
     */
    PluginGraph getPluginGraph(Plugin plugin, int id);

    /**
     * Gets the plugin graph index for the given plugin. Name -> Id
     *
     * @param plugin
     * @return
     */
    Map<String, Integer> getPluginGraphs(Plugin plugin);

    /**
     * Caches the given graph for the plugin. It must come from the database.
     *
     * @param plugin
     * @param graph
     */
    void cachePluginGraph(Plugin plugin, PluginGraph graph);

}
