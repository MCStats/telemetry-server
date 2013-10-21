package org.mcstats.cron;

import org.mcstats.MCStats;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.generator.GraphGenerator;
import org.mcstats.generator.IncrementAggregator;
import org.mcstats.generator.ReflectionAggregator;
import org.mcstats.generator.ReflectionDonutAggregator;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MainlineGraphs implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private MCStats mcstats;

    /**
     * A list of all generators
     */
    private List<GraphGenerator> generators = new LinkedList<GraphGenerator>();

    public MainlineGraphs(MCStats mcstats) {
        this.mcstats = mcstats;

        // custom data

        // auth mode
        // -- game version
        // -- global stats
        // -- > Players
        // -- > Servers
        // java version, donut (Reflection2Aggregator)
        // operating system, donut
        // rank
        // -- revision
        // server locations, ?
        // -- server software
        // -- system arch
        // -- system cores
        // version demographics
        // version trends

        generators.add(new IncrementAggregator("Global Statistics", "Servers"));
        generators.add(new ReflectionAggregator("players", "Global Statistics", "Players"));

        // generators.add(new ReflectionDonutAggregator("osname", "osversion", "Operating System"));
        // generators.add(new ReflectionDonutAggregator("java_name", "java_version", "Java Version"));
        /*

        generators.add(new ReflectionAggregator("minecraftVersion", "Game Version"));
        // generators.add(new ReflectionAggregator("revision", "MCStats Revision"));
        generators.add(new ReflectionAggregator("serverSoftware", "Server Software"));
        generators.add(new ReflectionAggregator("osarch", "System Arch"));
        generators.add(new ReflectionAggregator("cores", "System Cores"));
        generators.add(new ReflectionAggregator("", ""));
        generators.add(new ReflectionAggregator("", ""));

        */
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
        logger.info("Beginning graph generation");
        GraphStore store = mcstats.getGraphStore();

        long start = System.currentTimeMillis();

        for (GraphGenerator generator : generators) {
            logger.info("Generating graph for: " + generator);

            Map<Column, Long> data = generator.generate(mcstats);

            int sum = 0;
            int count = data.size();
            int avg = 0;
            int max = Integer.MIN_VALUE;
            int min = Integer.MAX_VALUE;

            for (long value : data.values()) {
                sum += value;

                if (value > max) {
                    max = (int) value;
                }

                if (value < min) {
                    min = (int) value;
                }
            }

            avg = (int) (sum / count);

            int epoch = ReportHandler.normalizeTime();

            for (Column column : data.keySet()) {
                store.insert(column, epoch, sum, count, avg, max, min);
            }

            logger.info("Aggregated: " + data);
        }

        ((MongoDBGraphStore) store).finishGeneration();
        logger.info("Finished graph generation in " + (System.currentTimeMillis() - start) + "ms");

    }
}
