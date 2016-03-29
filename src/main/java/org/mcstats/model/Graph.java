package org.mcstats.model;

public class Graph {

    /**
     * The graph id
     */
    private int id;

    /**
     * The plugin this graph is for
     */
    private Plugin plugin;

    /**
     * The position of the graph
     */
    private int position;

    /**
     * The graph type, TODO enum
     */
    private int type;

    /**
     * If the graph is active, TODO bool
     */
    private int active;

    /**
     * The graph's name
     */
    private String name;

    /**
     * The graph's display name
     */
    private String displayName;

    /**
     * The graph's scale, TODO enum
     */
    private String scale;

    public Graph(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Graph)) {
            return false;
        }

        Graph other = (Graph) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getScale() {
        return scale;
    }

    public void setScale(String scale) {
        this.scale = scale;
    }
}
