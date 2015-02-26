package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.util.Tuple;

import java.util.ArrayList;
import java.util.List;

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
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        List<Tuple<Column, Long>> res = new ArrayList<>();

        try {
            String countryName = mcstats.getCountryName(server.getCountry());

            if (countryName == null) {
                countryName = "Unknown";
            }

            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(countryName);

            res.add(new Tuple<>(column, 1L));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

}
