package org.mcstats.generator.aggregator.plugin;

import org.mcstats.generator.DataContainer;
import org.mcstats.generator.aggregator.PluginAggregator;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPluginData;

public class VersionTrendsPluginAggregator implements PluginAggregator {

    @Override
    public void aggregate(DataContainer container, Server instance, ServerPluginData by) {
        by.getVersionChanges().forEach(tuple -> {
            String oldVersion = tuple.first();
            String newVersion = tuple.second();

            container.add("Version Trends", oldVersion, -1);
            container.add("Version Trends", newVersion, 1);
        });
    }

}
