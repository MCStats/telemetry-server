package org.mcstats.model;

import org.apache.log4j.Logger;
import org.mcstats.MCStats;

import java.util.HashMap;
import java.util.Map;

public class Graph {
    private Logger logger = Logger.getLogger("Graph");

    /**
     * The mcstats object
     */
    private MCStats mcstats;

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

    /**
     * The columns for this graph. This is not all of them, only a cache
     */
    private Map<String, Column> columns = new HashMap<String, Column>();

    /**
     * If the columns were loaded from the database
     */
    private boolean didTryLoadColumns = false;

    public Graph(MCStats mcstats, Plugin plugin) {
        this.mcstats = mcstats;
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

    /**
     * Load a column for the given graph or created it if necessary
     *
     * @param name
     * @return
     */
    public Column loadColumn(String name) {
        if (!didTryLoadColumns && columns.size() == 0) {
            for (Column column : mcstats.getDatabase().loadColumns(this)) {
                columns.put(column.getName().toLowerCase(), column);
            }

            didTryLoadColumns = true;
        }

        Column column = columns.get(name.toLowerCase());

        if (column != null) {
            return column;
        }

        column = mcstats.getDatabase().createColumn(this, name);

        if (column == null) {
            if (mcstats.isDebug()) {
                logger.error("Failed to create Column for " + name + " , \"" + name + "\"");
            }
            return null;
        }

        columns.put(name.toLowerCase(), column);
        return column;
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
