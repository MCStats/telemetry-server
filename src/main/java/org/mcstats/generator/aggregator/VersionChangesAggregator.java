package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.SimpleAggregator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.util.ArrayList;
import java.util.List;

public class VersionChangesAggregator extends SimpleAggregator {

    /**
     * The name of the graph to store to
     */
    private String graphName;

    public VersionChangesAggregator(String graphName) {
        this.graphName = graphName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        List<Tuple<Column, Long>> res = new ArrayList<>();
        ServerPlugin serverPlugin = server.getPlugin(plugin);

        if (serverPlugin == null) {
            return res;
        }

        try {
            Graph graph = mcstats.loadGraph(plugin, graphName);

            for (Tuple<String, String> tuple : serverPlugin.getVersionChanges()) {
                if (tuple == null) {
                    continue;
                }

                String oldVersion = tuple.first();
                String newVersion = tuple.second();

                // plot old version at some point, too?
                Column newColumn = graph.loadColumn(newVersion);

                res.add(new Tuple<>(newColumn, 1L));
            }

            serverPlugin.clearVersionChanges();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

}
