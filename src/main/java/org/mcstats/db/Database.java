package org.mcstats.db;

import org.mcstats.model.Plugin;
import org.mcstats.model.PluginGraph;

import java.util.List;

public interface Database {

    /**
     * Saves a savable later.
     *
     * @param savable
     */
    void saveLater(Savable savable);

    /**
     * Create a bare plugin with the given name
     *
     * @param name
     * @return
     */
    Plugin createPlugin(String name);

    /**
     * Load all of the plugins from the database
     *
     * @return
     */
    List<Plugin> loadPlugins();

    /**
     * Load the plugin from the database with the given ID
     *
     * @param id
     * @return
     */
    Plugin loadPlugin(int id);

    /**
     * Load the plugin from the database with the given name
     *
     * @param name
     * @return
     */
    Plugin loadPlugin(String name);

    /**
     * Save the given plugin to the database
     *
     * @param plugin
     */
    void savePlugin(Plugin plugin);

    /**
     * Create a graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    PluginGraph createGraph(Plugin plugin, String name);

    /**
     * Load the graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    PluginGraph loadGraph(Plugin plugin, String name);

    /**
     * Load all of the graphs for a given plugin
     *
     * @param plugin
     * @return
     */
    List<PluginGraph> loadGraphs(Plugin plugin);

}
