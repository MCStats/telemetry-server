package org.mcstats.db;

import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.util.List;

public interface Database {

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
    Graph createGraph(Plugin plugin, String name);

    /**
     * Load the graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    Graph loadGraph(Plugin plugin, String name);

    /**
     * Load all of the graphs for a given plugin
     *
     * @param plugin
     * @return
     */
    List<Graph> loadGraphs(Plugin plugin);

    /**
     * Create a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    Column createColumn(Graph graph, String name);

    /**
     * Load a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    Column loadColumn(Graph graph, String name);

    /**
     * Load all columns for the given graph
     *
     * @param graph
     * @return
     */
    List<Column> loadColumns(Graph graph);

}
