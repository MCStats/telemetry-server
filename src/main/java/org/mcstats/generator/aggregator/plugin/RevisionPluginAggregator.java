package org.mcstats.generator.aggregator.plugin;

import org.mcstats.generator.DataContainer;
import org.mcstats.generator.aggregator.PluginAggregator;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPluginData;

public class RevisionPluginAggregator implements PluginAggregator {

    @Override
    public void aggregate(DataContainer container, Server instance, ServerPluginData by) {
        container.add("MCStats Revision", Integer.toString(by.getRevision()), 1);
    }

}
