package org.mcstats.generator.aggregator.plugin;

import org.mcstats.generator.DataContainer;
import org.mcstats.generator.Datum;
import org.mcstats.generator.aggregator.PluginAggregator;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

public class RankPluginAggregator implements PluginAggregator {

    @Override
    public void aggregate(DataContainer container, Server instance, ServerPlugin by) {
        Datum datum = new Datum();
        datum.setSum(by.getPlugin().getRank());
        datum.setCount(1);

        container.set("Rank", "Rank", datum);
    }

}
