package org.mcstats.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.mcstats.db.Database;
import org.mcstats.db.ModelCache;
import org.mcstats.guice.GuiceModule;
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

        plugins.forEach(modelCache::cachePlugin);

        logger.info("Cached " + plugins.size() + " plugins");

        // TODO cache plugin graphs?
    }

}
