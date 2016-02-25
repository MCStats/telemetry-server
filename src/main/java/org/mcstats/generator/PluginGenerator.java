package org.mcstats.generator;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import org.mcstats.generator.aggregator.BasicAggregator;
import org.mcstats.generator.aggregator.PluginAggregator;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PluginGenerator implements Generator<Plugin> {

    /**
     * The aggregators that can be applied to any server without a plugin parameter
     */
    private final List<BasicAggregator<Server>> basicAggregators = new ArrayList<>();

    /**
     * The aggregators that can be applied to any server with a plugin parameter
     */
    private final List<PluginAggregator> pluginAggregators = new ArrayList<>();

    private final Supplier<List<Server>> allServersSupplier;

    public PluginGenerator(Supplier<List<Server>> allServersSupplier) {
        this.allServersSupplier = allServersSupplier;
    }

    /**
     * Adds an aggregator that can be applied to any server without a plugin parameter.
     *
     * @param aggregator
     */
    public void addAggregator(BasicAggregator<Server> aggregator) {
        basicAggregators.add(aggregator);
    }

    /**
     * Adds an aggregator that can be applied to any server with a plugin parameter
     *
     * @param aggregator
     */
    public void addAggregator(PluginAggregator aggregator) {
        pluginAggregators.add(aggregator);
    }

    @Override
    public ImmutableMap<String, Map<String, Datum>> generateAll() {
        DataContainer container = new DataContainer();

        for (Server server : allServersSupplier.get()) {
            for (BasicAggregator<Server> aggregator : basicAggregators) {
                aggregator.aggregate(container, server);
            }
        }

        return container.getData();
    }

    @Override
    public ImmutableMap<String, Map<String, Datum>> generatorFor(Plugin instance) {
        DataContainer container = new DataContainer();

        for (Server server : allServersSupplier.get()) {
            ServerPlugin serverPlugin = server.getPlugin(instance);

            if (serverPlugin == null || !serverPlugin.recentlyUpdated()) {
                continue;
            }

            for (BasicAggregator<Server> aggregator : basicAggregators) {
                aggregator.aggregate(container, server);
            }

            // Mix in plugin-specific vars
            for (PluginAggregator aggregator : pluginAggregators) {
                aggregator.aggregate(container, server, serverPlugin);
            }
        }

        return container.getData();
    }

}
