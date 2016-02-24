package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.util.HashMap;
import java.util.List;
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
    public abstract List<Tuple<Column, Long>> getValues(MCStats mcstats, Plugin plugin, Server server);

    private Map<String, Map<String, GeneratedData>> aggregate(MCStats mcstats, Plugin plugin) {
        Map<String, Map<String, GeneratedData>> result = new HashMap<>();

        Plugin pluginValue;

        if (plugin == null) {
            // All Servers
            pluginValue = mcstats.loadPlugin(-1);
        } else {
            pluginValue = plugin;
        }

        if (pluginValue.getId() == -1) {
            for (Server server : mcstats.getCachedServers()) {
                if (!server.recentlySentData()) {
                    continue;
                }

                List<Tuple<Column, Long>> values = getValues(mcstats, pluginValue, server);

                if (values == null || values.size() == 0) {
                    continue;
                }

                for (Tuple<Column, Long> value : values) {
                    if (value != null) {
                        Column column = value.first();
                        long columnValue = value.second();

                        GeneratedData current = result.get(column);

                        if (current == null) {
                            current = new GeneratedData();
                            current.setCount(1);
                            current.setMax((int) columnValue);
                            current.setMin((int) columnValue);
                            current.setSum((int) columnValue);
                            result.put(column, current);
                            continue;
                        }

                        current.incrementCount();
                        current.incrementSum((int) columnValue);
                    }
                }
            }
        } else {
            for (ServerPlugin serverPlugin : mcstats.getServerPlugins(pluginValue)) {
                if (!serverPlugin.recentlyUpdated()) {
                    continue;
                }

                List<Tuple<Column, Long>> values = getValues(mcstats, pluginValue, serverPlugin.getServer());

                if (values == null || values.size() == 0) {
                    continue;
                }

                for (Tuple<Column, Long> value : values) {
                    if (value != null) {
                        Column column = value.first();
                        long columnValue = value.second();

                        GeneratedData current = result.get(column);

                        if (current == null) {
                            current = new GeneratedData();
                            current.setCount(1);
                            current.setMax((int) columnValue);
                            current.setMin((int) columnValue);
                            current.setSum((int) columnValue);
                            result.put(column, current);
                            continue;
                        }

                        current.incrementCount();
                        current.incrementSum((int) columnValue);
                    }
                }
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Map<String, GeneratedData>> generate(MCStats mcstats) {
        Map<String, Map<String, GeneratedData>> result = new HashMap<>();

        // aggregate all servers first
        result.putAll(aggregate(mcstats, null));

        // aggregate all plugins
        for (Plugin plugin : mcstats.getCachedPlugins()) {
            if (plugin.recentlyUpdated()) {
                result.putAll(aggregate(mcstats, plugin));
            }
        }

        return result;
    }

}
