package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.util.Tuple;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class IncrementAggregator extends SimpleAggregator {

    /**
     * The name of the graph to use
     */
    private String graphName;

    /**
     * The name of the column to use. If null, then the method returns the column's name
     */
    private String columnName;

    /**
     * Create a new increment aggregator that simply uses a value of 1 for every call
     * to getValue using the given graphName and columnName
     *
     * @param graphName
     * @param columnName
     */
    public IncrementAggregator(String graphName, String columnName) {
        this.graphName = graphName;
        this.columnName = columnName;

        if (columnName == null) {
            throw new UnsupportedOperationException("columnName cannot be null");
        }

        if (graphName == null) {
            throw new UnsupportedOperationException("graphName cannot be null");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        List<Tuple<Column, Long>> res = new ArrayList<Tuple<Column, Long>>();

        try {
            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(columnName);

            res.add(new Tuple<Column, Long>(column, 1L));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

    @Override
    public String toString() {
        return String.format("IncrementAggregator(graphName = %s, columnName = %s)", graphName, columnName);
    }

}
