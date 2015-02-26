package org.mcstats.generator.aggregator;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.procedure.TIntIntProcedure;
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

public class RevisionAggregator extends SimpleAggregator {

    /**
     * The name of the graph to store to
     */
    private String graphName;

    public RevisionAggregator(String graphName) {
        this.graphName = graphName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        ServerPlugin serverPlugin = server.getPlugin(plugin);
        final List<Tuple<Column, Long>> res = new ArrayList<>();

        try {
            final Graph graph = mcstats.loadGraph(plugin, graphName);

            if (serverPlugin != null) {
                if (serverPlugin.getRevision() > 0) {
                    Column column = graph.loadColumn(Integer.toString(serverPlugin.getRevision()));
                    res.add(new Tuple<>(column, 1L));
                }
            } else {
                TIntIntHashMap sums = new TIntIntHashMap();

                for (ServerPlugin serverPlugin2 : server.getPlugins().values()) {
                    int revision = serverPlugin2.getRevision();

                    if (revision == 0 || !serverPlugin2.recentlyUpdated()) {
                        continue;
                    }

                    if (sums.containsKey(revision)) {
                        sums.put(revision, sums.get(revision) + 1);
                    } else {
                        sums.put(revision, 1);
                    }
                }

                sums.forEachEntry((key, value) -> {
                    Column column = graph.loadColumn(Integer.toString(key));
                    res.add(new Tuple<>(column, (long) value));
                    return true;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

}
