package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.util.Tuple;

public class CountryAggregator extends SimpleAggregator {

    /**
     * The name of the graph to store to
     */
    private String graphName;

    public CountryAggregator(String graphName) {
        this.graphName = graphName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tuple<Column, Long> getValue(MCStats mcstats, Plugin plugin, Server server) {
        try {
            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(mcstats.getCountryName(server.getCountry()));

            return new Tuple<Column, Long>(column, 1L);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
