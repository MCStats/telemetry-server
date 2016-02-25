package org.mcstats.generator.aggregator.plugin;

import org.mcstats.generator.DataContainer;
import org.mcstats.generator.aggregator.PluginAggregator;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

public class CustomDataPluginAggregator implements PluginAggregator {

    @Override
    public void aggregate(DataContainer container, Server instance, ServerPlugin by) {
        by.getCustomData().forEach((graphName, columnData) -> {
            columnData.forEach((columnName, value) -> {
                container.add(graphName, columnName, value);
            });
        });
    }

}
