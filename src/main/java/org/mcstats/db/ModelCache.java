package org.mcstats.db;

import org.mcstats.model.Plugin;
import org.mcstats.model.PluginGraph;
import org.mcstats.model.PluginGraphColumn;

import java.util.List;

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
     * Caches the given graph for the plugin. It must come from the database.
     *
     * @param plugin
     * @param graph
     */
    void cachePluginGraph(Plugin plugin, PluginGraph graph);

    /**
     * Gets all columns for the given graph
     *
     * @param graph
     * @return
     */
    List<PluginGraphColumn> getPluginGraphColumns(PluginGraph graph);

    /**
     * Caches all the given graph columns
     *
     * @param graph
     * @param columns
     */
    void cachePluginGraphColumns(PluginGraph graph, List<PluginGraphColumn> columns);

}
