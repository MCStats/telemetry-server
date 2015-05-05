package org.mcstats.cron;

import org.apache.log4j.BasicConfigurator;
import org.mcstats.MCStats;
import org.mcstats.generator.RedisPluginGraphAggregator;
import org.mcstats.handler.ReportHandler;

import java.util.logging.Logger;

public class PluginGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final MCStats mcstats;
    private final RedisPluginGraphAggregator aggregator;

    public PluginGraphGenerator(MCStats mcstats) {
        this.mcstats = mcstats;
        aggregator = new RedisPluginGraphAggregator(mcstats);
    }

    public void run() {
        try {
            logger.info("Generating graphs for plugins");
            long start = System.currentTimeMillis();

            // TODO Rank graph, ServerCount30 generation
            aggregator.run();
            // TODO redis cleanup

            long taken = System.currentTimeMillis() - start;
            // TODO sampling

            logger.info("Generated plugin graphs in " + taken + " ms");

            ReportHandler.SOFT_IGNORE_REQUESTS = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ReportHandler.SOFT_IGNORE_REQUESTS = false;
        }
    }

    public static void main(String[] args) {
        BasicConfigurator.configure();
        MCStats.getInstance().init();
        new PluginGraphGenerator(MCStats.getInstance()).run();
    }

}
