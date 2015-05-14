package org.mcstats.cron;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.mcstats.MCStats;
import org.mcstats.generator.RedisPluginGraphAggregator;
import org.mcstats.guice.GuiceModule;
import org.mcstats.handler.ReportHandler;
import redis.clients.jedis.Jedis;

import java.util.logging.Logger;

public class PluginGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final MCStats mcstats;
    private final RedisPluginGraphAggregator aggregator;
    private final String redisDelWildcardSha;

    public PluginGraphGenerator(MCStats mcstats) {
        this.mcstats = mcstats;
        aggregator = new RedisPluginGraphAggregator(mcstats);

        redisDelWildcardSha = mcstats.loadRedisScript("/scripts/redis/del-wildcard.lua");
    }

    public void run() {
        try {
            logger.info("Generating graphs for plugins");
            long start = System.currentTimeMillis();

            // TODO Rank graph, ServerCount30 generation
            aggregator.run();

            try (Jedis redis = mcstats.getRedisPool().getResource()) {
                redis.evalsha(redisDelWildcardSha, 0, "plugin-data:*", "plugin-data-sum:*");
            }

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

        Injector injector = Guice.createInjector(new GuiceModule());
        MCStats mcstats = injector.getInstance(MCStats.class);

        new PluginGraphGenerator(mcstats).run();
    }

}
