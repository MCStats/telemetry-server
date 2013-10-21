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
import java.util.Map;

public class CustomDataAggregator extends SimpleAggregator {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server) {
        List<Tuple<Column, Long>> res = new ArrayList<Tuple<Column, Long>>();
        ServerPlugin serverPlugin = server.getPlugin(plugin);

        if (serverPlugin == null) {
            return res;
        }

        for (Map.Entry<Column, Long> entry : serverPlugin.getCustomData().entrySet()) {
            res.add(new Tuple<Column, Long>(entry.getKey(), entry.getValue()));
        }

        return res;
    }

}
