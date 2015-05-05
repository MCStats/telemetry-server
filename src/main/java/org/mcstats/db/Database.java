package org.mcstats.db;

import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.util.List;
import java.util.Map;

public interface Database {

    /**
     * Create a bare plugin with the given name
     *
     * @param name
     * @return
     */
    public Plugin createPlugin(String name);

    /**
     * Load all of the plugins from the database
     *
     * @return
     */
    public List<Plugin> loadPlugins();

    /**
     * Load the plugin from the database with the given ID
     *
     * @param id
     * @return
     */
    public Plugin loadPlugin(int id);

    /**
     * Load the plugin from the database with the given name
     *
     * @param name
     * @return
     */
    public Plugin loadPlugin(String name);

    /**
     * Save the given plugin to the database
     *
     * @param plugin
     */
    public void savePlugin(Plugin plugin);

    /**
     * Create a graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    public Graph createGraph(Plugin plugin, String name);

    /**
     * Load the graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    public Graph loadGraph(Plugin plugin, String name);

    /**
     * Load all of the graphs for a given plugin
     *
     * @param plugin
     * @return
     */
    public List<Graph> loadGraphs(Plugin plugin);

    /**
     * Create a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    public Column createColumn(Graph graph, String name);

    /**
     * Load a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    public Column loadColumn(Graph graph, String name);

    /**
     * Load all columns for the given graph
     *
     * @param graph
     * @return
     */
    public List<Column> loadColumns(Graph graph);

}
