package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.util.Tuple;

import java.lang.reflect.Field;

public class ReflectionDonutAggregator extends SimpleAggregator {

    /**
     * The name of the graph to use
     */
    private String graphName;

    /**
     * The name of the column to use on the inner donut. If null, then the field is the column's name
     */
    private String innerColumnName;

    /**
     * The name of the column to use on the outer donut. If null, then the field is the column's name
     */
    private String outerColumnName;

    /**
     * The inner donut's field
     */
    private Field innerField;

    /**
     * The outer donut's field
     */
    private Field outerField;

    /**
     * Create a new reflection aggregator that will use the value returned by
     * the given field as the name of the column and the value to be summed
     * will be a constant 1.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param innerFieldName
     * @param outerFieldName
     * @param graphName
     */
    public ReflectionDonutAggregator(String innerFieldName, String outerFieldName, String graphName) {
        this(innerFieldName, outerFieldName, graphName, null, null);
    }

    /**
     * Create a new reflection aggregator that will use the value returned
     * by the given field as the value of the data and the given columnName
     * as the name of the column.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param innerFieldName
     * @param outerFieldName
     * @param graphName
     * @param innerColumnName
     * @param outerColumnName
     */
    public ReflectionDonutAggregator(String innerFieldName, String outerFieldName, String graphName, String innerColumnName, String outerColumnName) {
        try {
            this.innerField = Server.class.getDeclaredField(innerFieldName);
            this.outerField = Server.class.getDeclaredField(outerFieldName);
            this.innerField.setAccessible(true);
            this.outerField.setAccessible(true);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        this.graphName = graphName;
        this.innerColumnName = innerColumnName;
        this.outerColumnName = outerColumnName;

        if (graphName == null) {
            throw new UnsupportedOperationException("graphName cannot be null");
        }
    }

    /**
     * {@inheritDoc
     */
    @Override
    public Tuple<Column, Long> getValue(MCStats mcstats, Plugin plugin, Server server) {
        if (innerField == null || outerField == null) {
            return null;
        }

        try {
            Object innerValue = innerField.get(server);
            Object outerValue = outerField.get(server);

            String usingInner = innerColumnName;
            String usingOuter = outerColumnName;

            long columnValue = 1;

            // attempt to parse it as a string
            if (usingInner == null) {
                usingInner = (String) innerValue;
            }

            if (usingOuter == null) {
                usingOuter = (String) outerValue;
            }

            // load the graph for the plugin
            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(usingInner + "~=~" + usingOuter);

            return new Tuple<Column, Long>(column, columnValue);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("ReflectionAggregator(innerFieldName = %s, outerFieldName = %s, graphName = %s, innerColumnName = %s, outerColumnName = %s)", innerField.getName(), outerField.getName(), graphName, innerColumnName, outerColumnName);
    }

}
