package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.util.Tuple;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReflectionAggregator extends SimpleAggregator {

    /**
     * The name of the graph to use
     */
    protected String graphName;

    /**
     * The name of the column to use. If null, then the field is the column's name
     */
    protected String columnName;

    /**
     * The method we are reflecting into
     */
    protected Field field;

    /**
     * Create a new reflection aggregator that will use the value returned by
     * the given field as the name of the column and the value to be summed
     * will be a constant 1.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param graphName
     * @param fieldName
     */
    public ReflectionAggregator(String fieldName, String graphName) {
        this(fieldName, graphName, null);
    }

    /**
     * Create a new reflection aggregator that will use the value returned
     * by the given field as the value of the data and the given columnName
     * as the name of the column.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param fieldName
     * @param graphName
     * @param columnName
     */
    public ReflectionAggregator(String fieldName, String graphName, String columnName) {
        try {
            this.field = Server.class.getDeclaredField(fieldName);
            this.field.setAccessible(true);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        this.graphName = graphName;
        this.columnName = columnName;

        if (graphName == null) {
            throw new UnsupportedOperationException("graphName cannot be null");
        }
    }

    /**
     * Get the column name
     *
     * @return
     */
    public String getColumnName(Server server) {
        return columnName;
    }

    /**
     * {@inheritDoc
     */
    @Override
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        List<Tuple<Column, Long>> res = new ArrayList<Tuple<Column, Long>>();

        if (field == null) {
            return res;
        }

        try {
            Object value = field.get(server);

            String usingColumn = getColumnName(server);
            long columnValue = 1;

            // attempt to parse it as a string
            if (usingColumn == null) {
                usingColumn = value.toString();
            } else {
                try {
                    columnValue = Long.parseLong(value.toString());
                } catch (Exception e) {
                    columnValue = 1;
                }
            }

            // load the graph for the plugin
            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(usingColumn);

            res.add(new Tuple<Column, Long>(column, columnValue));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

    @Override
    public String toString() {
        return String.format("ReflectionAggregator(fieldName = %s, graphName = %s, columnName = %s)", field.getName(), graphName, columnName);
    }

}
