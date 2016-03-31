package org.mcstats.cron;

import com.google.common.collect.Lists;
import org.mcstats.MCStats;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.MapUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CronRanking implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private MCStats mcstats;

    public CronRanking(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    /**
     * {@inheritDoc}
     */
    public void run() {

        if (mcstats.countRecentServers() < 50000) {
            logger.info("Not enough data. Auto correcting internal caches.");
            mcstats.resetInternalCaches();
            return;
        }

        Map<Plugin, Integer> pluginServerCounts = new HashMap<>();

        for (Plugin plugin : mcstats.getCachedPlugins()) {
            if (plugin.isHidden()) {
                continue;
            }

            pluginServerCounts.put(plugin, plugin.getActiveServerCount());
        }

        int epoch = PluginTelemetryHandler.normalizeTime();
        int rank = 1;

        Map<Plugin, Integer> sorted = MapUtil.sortByValue(pluginServerCounts);
        List<Map.Entry<Plugin, Integer>> list = new ArrayList<>(sorted.entrySet());

        // traverse the list in reverse order (high - low)
        for (Map.Entry<Plugin, Integer> entry : Lists.reverse(list)) {
            Plugin plugin = entry.getKey();
            int servers = entry.getValue();

            int newRank = rank++;

            if (newRank != plugin.getRank()) {
                plugin.setLastRankChange(epoch);
            }

            plugin.setLastRank(plugin.getRank());
            plugin.setRank(newRank);
            plugin.save();

            // TODO add to Rank graph
        }

    }
}
