package org.mcstats.cron;

import com.google.common.collect.ImmutableMap;
import org.mcstats.MCStats;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.generator.Datum;
import org.mcstats.generator.PluginGenerator;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.model.Plugin;
import org.mcstats.model.ServerPlugin;

import java.util.Map;
import java.util.logging.Logger;

public class CronGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final MCStats mcstats;
    private final PluginGenerator pluginGenerator;

    public CronGraphGenerator(MCStats mcstats, PluginGenerator pluginGenerator) {
        this.mcstats = mcstats;
        this.pluginGenerator = pluginGenerator;
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
        try {
            logger.info("Beginning graph generation");
            GraphStore store = mcstats.getGraphStore();
            PluginTelemetryHandler.SOFT_IGNORE_REQUESTS = true;

            if (mcstats.countRecentServers() < 50000) {
                logger.info("Not enough data. Auto correcting internal caches.");
                mcstats.resetInternalCaches();
                return;
            }

            long start = System.currentTimeMillis();
            int epoch = PluginTelemetryHandler.normalizeTime();

            // Generate All Servers
            // TODO insert all_servers data to its own collection
            Plugin allServersPlugin = mcstats.loadPlugin(-1);
            ImmutableMap<String, Map<String, Datum>> allServerGeneratedData = pluginGenerator.generateAll();

            allServerGeneratedData.forEach((graphName, data) -> {
                store.insertPluginData(epoch, allServersPlugin, graphName, data);
            });

            for (Plugin plugin : mcstats.getCachedPlugins()) {
                ImmutableMap<String, Map<String, Datum>> generatedData = pluginGenerator.generatorFor(plugin);

                generatedData.forEach((graphName, data) -> {
                    store.insertPluginData(epoch, plugin, graphName, data);
                });
            }

            logger.info("Beginning final stage of graph generation");

            for (Plugin plugin : mcstats.getCachedPlugins()) {
                int activeServerCount = 0;
                int activePlayerCount = 0;

                for (ServerPlugin serverPlugin : mcstats.getServerPlugins(plugin)) {
                    if (serverPlugin.recentlyUpdated()) {
                        serverPlugin.getServer().setViolationCount(0);

                        activeServerCount ++;
                        activePlayerCount += serverPlugin.getServer().getPlayers();
                    }
                }

                plugin.setActiveServerCount(activeServerCount);
                plugin.setActivePlayerCount(activePlayerCount);
                plugin.saveNow();
            }

            ((MongoDBGraphStore) store).finishGeneration();
            mcstats.resetIntervalData();

            logger.info("Finished graph generation in " + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PluginTelemetryHandler.SOFT_IGNORE_REQUESTS = false;
        }
    }
}
