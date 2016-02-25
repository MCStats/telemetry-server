package org.mcstats.generator;

import org.mcstats.model.Plugin;

public abstract class AbstractPluginAggregator implements PluginAggregator {

    /**
     * The plugin being aggregated on
     */
    private final Plugin plugin;

    protected AbstractPluginAggregator(Plugin plugin) {
        this.plugin = plugin;
    }

}
