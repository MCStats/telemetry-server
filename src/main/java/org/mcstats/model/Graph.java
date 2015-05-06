package org.mcstats.model;

public class Graph {

    /**
     * The plugin this graph is for
     */
    private final Plugin plugin;

    /**
     * The id of the graph (if available)
     */
    private int id;

    /**
     * The name of the graph
     */
    private final String name;

    /**
     * Flag for if the graph has the full data from the database
     */
    private boolean isFromDatabase = false;

    public Graph(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public Graph(Plugin plugin, int id, String name) {
        this(plugin, name);
        this.id = id;
        isFromDatabase = true;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public String getName() {
        return name;
    }

    /**
     * Initializes the graph as a loaded object from the database
     * @param id
     */
    public void initFromDatabase(int id) {
        this.id = id;
        isFromDatabase = true;
    }

    public int getId() {
        return id;
    }

    public boolean isFromDatabase() {
        return isFromDatabase;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Graph graph = (Graph) o;

        if (name != null ? !name.equals(graph.name) : graph.name != null) return false;
        if (plugin != null ? !plugin.equals(graph.plugin) : graph.plugin != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = plugin != null ? plugin.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
