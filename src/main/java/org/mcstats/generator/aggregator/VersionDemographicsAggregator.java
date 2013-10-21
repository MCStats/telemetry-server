package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

public class VersionDemographicsAggregator extends SimpleAggregator {

    /**
     * The name of the graph to store to
     */
    private String graphName;

    public VersionDemographicsAggregator(String graphName) {
        this.graphName = graphName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tuple<Column, Long> getValue(MCStats mcstats, Plugin plugin, Server server) {
        ServerPlugin serverPlugin = server.getPlugin(plugin);

        if (serverPlugin == null) {
            return null;
        }

        try {
            Graph graph = mcstats.loadGraph(plugin, graphName);
            Column column = graph.loadColumn(serverPlugin.getVersion());

            return new Tuple<Column, Long>(column, 1L);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
