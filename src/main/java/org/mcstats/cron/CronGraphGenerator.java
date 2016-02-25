package org.mcstats.cron;

import org.mcstats.MCStats;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.model.Plugin;
import org.mcstats.model.ServerPlugin;

import java.util.logging.Logger;

public class CronGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private MCStats mcstats;

    public CronGraphGenerator(MCStats mcstats) {
        this.mcstats = mcstats;

        // -- custom data

        // -- auth mode
        // -- game version
        // -- global stats
        // -- > Players
        // -- > Servers
        // -- java version, donut (Reflection2Aggregator)
        // -- operating system, donut
        // -- rank
        // -- revision
        // -- server locations
        // -- server software
        // -- system arch
        // -- system cores
        // -- version demographics
        // -- version trends

        /*
        generators.add(new MergeAggregator(new IncrementAggregator("Global Statistics", "Servers"), new ReflectionAggregator("players", "Global Statistics", "Players")));

        generators.add(new ReflectionAggregator("serverSoftware", "Server Software"));
        generators.add(new ReflectionAggregator("minecraftVersion", "Game Version"));
        generators.add(new ReflectionAggregator("osarch", "System Arch"));
        generators.add(new ReflectionAggregator("cores", "System Cores"));

        generators.add(new RevisionAggregator("MCStats Revision"));

        generators.add(new ReflectionDonutAggregator("osname", "osversion", "Operating System"));
        generators.add(new ReflectionDonutAggregator("java_name", "java_version", "Java Version"));

        generators.add(new VersionDemographicsAggregator("Version Demographics"));

        generators.add(new VersionChangesAggregator("Version Trends"));

        generators.add(new CountryAggregator("Server Locations"));

        generators.add(new CustomDataAggregator());

        generators.add(new RankAggregator());

        generators.add(new DecoderAggregator<Integer>("online_mode", "Auth Mode", value -> {
            switch (value) {
                case 1:
                    return "Online";
                case 0:
                    return "Offline";
                default:
                    return "Unknown";
            }
        }));
        */
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

            /*
            for (GraphGenerator generator : generators) {
                logger.info("Generating graph for: " + generator);

                Map<Column, GeneratedData> data = generator.generate(mcstats);

                int epoch = PluginTelemetryHandler.normalizeTime();

                logger.info("Storing " + data.size() + " columns of data");

                Map<Graph, List<Tuple<Column, GeneratedData>>> grouped = new HashMap<>();

                // group together the data for each graph
                for (Map.Entry<Column, GeneratedData> entry : data.entrySet()) {
                    Column column = entry.getKey();
                    GeneratedData columnData = entry.getValue();

                    if (column == null || columnData == null) {
                        continue;
                    }

                    List<Tuple<Column, GeneratedData>> listdata = grouped.get(column.getGraph());

                    if (listdata == null) {
                        listdata = new ArrayList<>();
                        grouped.put(column.getGraph(), listdata);
                    }

                    listdata.add(new Tuple<>(column, columnData));
                }

                for (Map.Entry<Graph, List<Tuple<Column, GeneratedData>>> entry : grouped.entrySet()) {
                    List<Tuple<Column, GeneratedData>> listdata = entry.getValue();
                    store.batchInsert(entry.getKey(), listdata, epoch);
                }

                grouped.clear();
                data.clear();

                // logger.info("Aggregated: " + data);
            }
            */

            logger.info("Beginning final stage of graph generation");

            for (Plugin plugin : mcstats.getCachedPlugins()) {
                int numServers30 = 0;

                for (ServerPlugin serverPlugin : mcstats.getServerPlugins(plugin)) {
                    if (serverPlugin.recentlyUpdated()) {
                        serverPlugin.getServer().setViolationCount(0);
                        // serverPlugin.getServer().save();
                        // serverPlugin.save();
                        numServers30 ++;
                    }
                }

                plugin.setServerCount30(numServers30);
                plugin.saveNow();
            }

            ((MongoDBGraphStore) store).finishGeneration();
            mcstats.resetIntervalData();

            System.gc();
            System.runFinalization();
            System.gc();

            logger.info("Finished graph generation in " + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PluginTelemetryHandler.SOFT_IGNORE_REQUESTS = false;
        }
    }
}