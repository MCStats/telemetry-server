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

/**
 * Populates the cache with plugin data
 */
public class PluginCachePopulator {

    private static final Logger logger = Logger.getLogger(PluginCachePopulator.class);

    public static void main(String[] args) {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new GuiceModule());
        Database database = injector.getInstance(Database.class);
        ModelCache modelCache = injector.getInstance(ModelCache.class);

        List<Plugin> plugins = database.loadPlugins();
        int cached = 0;

        logger.info("Caching " + plugins.size() + " plugins");

        for (Plugin plugin : plugins) {
            modelCache.cachePlugin(plugin);

            for (Graph graph : database.loadGraphs(plugin)) {
                modelCache.cachePluginGraph(plugin, graph);
            }

            cached ++;

            if (cached % 10 == 0) {
                System.out.print('.');

                if (cached % 100 == 0) {
                    System.out.print(cached);
                }

                if (cached % 1000 == 0) {
                    System.out.println();
                }
            }
        }

        System.out.println();

        logger.info("Cached all plugins");

        // TODO cache plugin graphs?
    }

}
