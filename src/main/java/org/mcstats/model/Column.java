package org.mcstats.model;

import org.mcstats.MCStats;

public class Column {

    /**
     * The mcstats object
     */
    private MCStats mcstats;

    /**
     * The id for the column
     */
    private int id;

    /**
     * The plugin object
     */
    private Plugin plugin;

    /**
     * The graph object
     */
    private Graph graph;

    /**
     * The column's name
     */
    private String name;

    public Column(MCStats mcstats, Graph graph, Plugin plugin) {
        this.mcstats = mcstats;
        this.graph = graph;
        this.plugin = plugin;
    }

    @Override
    public String toString() {
        return String.format("Column(Plugin = %s, Graph = %s, Name = %s)", plugin.getName(), graph.getName(), name);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Graph getGraph() {
        return graph;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Column)) {
            return false;
        }

        Column oc = (Column) o;
        return id == oc.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

}
