package org.mcstats.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.mcstats.db.Database;
import org.mcstats.db.ModelCache;
import org.mcstats.guice.GuiceModule;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Populates the cache with plugin data
 */
public class PluginCachePopulator {

    private static final int NUM_THREADS = 16;

    private static final Logger logger = Logger.getLogger(PluginCachePopulator.class);

    public static void main(String[] args) {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new GuiceModule());
        Database database = injector.getInstance(Database.class);
        ModelCache modelCache = injector.getInstance(ModelCache.class);

        List<Plugin> plugins = database.loadPlugins();
        AtomicInteger numPluginsCached = new AtomicInteger(0);

        logger.info("Caching " + plugins.size() + " plugins");

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        plugins.forEach(plugin -> executor.submit(() -> {
            modelCache.cachePlugin(plugin);

            for (Graph graph : database.loadGraphs(plugin)) {
                modelCache.cachePluginGraph(plugin, graph);
                modelCache.cachePluginGraphColumns(graph, database.loadColumns(graph));
            }

            int pluginsCached = numPluginsCached.incrementAndGet();

            if (pluginsCached % 10 == 0) {
                System.out.print('.');

                if (pluginsCached % 100 == 0) {
                    System.out.print(numPluginsCached);
                }

                if (pluginsCached % 1000 == 0) {
                    System.out.println();
                }
            }
        }));

        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                logger.error("Failed to cache all plugins");
                return;
            }
        } catch (InterruptedException e) {
            logger.error("Failed to cache all plugins", e);
            return;
        }

        System.out.println();

        logger.info("Cached all plugins");
    }

}
