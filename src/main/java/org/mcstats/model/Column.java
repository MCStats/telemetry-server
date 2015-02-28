package org.mcstats.model;

public class Column {

    /**
     * The graph this column belongs to
     */
    private final Graph graph;

    /**
     * The id of the column (if available)
     */
    private int id;

    /**
     * The name of this column
     */
    private final String name;

    /**
     * Flag for if the graph has the full data from the database
     */
    private boolean isFromDatabase = false;

    public Column(Graph graph, String name) {
        this.graph = graph;
        this.name = name;
    }

    public Graph getGraph() {
        return graph;
    }

    public String getName() {
        return name;
    }

    public void initFromDatabase(int id) {
        this.id = id;
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

        Column column = (Column) o;

        if (graph != null ? !graph.equals(column.graph) : column.graph != null) return false;
        if (name != null ? !name.equals(column.name) : column.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = graph != null ? graph.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

}
