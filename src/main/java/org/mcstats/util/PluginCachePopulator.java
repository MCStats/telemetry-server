package org.mcstats.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.mcstats.GuiceModule;
import org.mcstats.MCStats;
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
        MCStats mcstats = injector.getInstance(MCStats.class);

        List<Plugin> plugins = mcstats.getDatabase().loadPlugins();

        for (Plugin plugin : plugins) {
            mcstats.getModelCache().cachePlugin(plugin);
        }

        logger.info("Cached " + plugins.size() + " plugins");

        // TODO cache plugin graphs?
    }

}
