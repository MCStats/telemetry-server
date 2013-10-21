package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Aggregates a field from every Server
 */
public abstract class SimpleAggregator implements GraphGenerator {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    /**
     * The plugin to aggregate. If null, all servers are used
     */
    private final Plugin plugin;

    /**
     * Aggregates from all servers
     */
    public SimpleAggregator() {
        this(null);
    }

    /**
     * Aggregates from all servers running a specific plugin
     *
     * @param plugin
     */
    public SimpleAggregator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Get the value to aggregate, given a server. It is assumed the given the server matches the aggregate definition
     *
     * @param plugin
     * @param server
     * @return
     */
    public abstract Tuple<Column, Long> getValue(MCStats mcstats, Plugin plugin, Server server);

    private Map<Column, Long> aggregate(MCStats mcstats, Plugin plugin) {
        Map<Column, Long> data = new HashMap<Column, Long>();

        for (Server server : mcstats.getCachedServers()) {
            boolean match = true;

            if (plugin != null) {
                ServerPlugin serverPlugin = server.getPlugin(plugin);

                if (serverPlugin == null || serverPlugin.getUpdated() < (((int) System.currentTimeMillis() / 1000) - 1800 /* TODO not a magic number, funtionize into ServerPlugin */)) {
                    match = false;
                }
            }

            if (!match) {
                continue;
            }

            Plugin pluginValue;

            if (plugin == null) {
                // All Servers
                pluginValue = mcstats.loadPlugin(-1);
            } else {
                pluginValue = plugin;
            }

            Tuple<Column, Long> value = getValue(mcstats, pluginValue, server);

            if (value != null) {
                data.put(value.first(), value.second());
            }
        }

        return data;
    }

    /**
     * {@inheritDoc}
     */
    public Map<Column, Long> generate(MCStats mcstats) {
        Map<Column, Long> data = new HashMap<Column, Long>();

        // aggregate all servers first
        data.putAll(aggregate(mcstats, null));

        // aggregate all plugins
        for (Plugin plugin : mcstats.getCachedPlugins()) {
            data.putAll(aggregate(mcstats, plugin));
        }

        return data;
    }
}
