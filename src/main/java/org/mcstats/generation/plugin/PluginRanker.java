package org.mcstats.generation.plugin;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mcstats.db.Database;
import org.mcstats.guice.GuiceModule;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.MapUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class PluginRanker {

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    private static final Logger logger = Logger.getLogger(PluginRanker.class);

    private final Gson gson;
    private final Database database;
    private final JedisPool redisPool;

    @Inject
    public PluginRanker(Gson gson, Database database, JedisPool redisPool) {
        this.gson = gson;
        this.database = database;
        this.redisPool = redisPool;
    }

    /**
     * Ranks all plugins.
     *
     * @param bucket
     */
    public void run(int bucket) {
        // TODO hidden support
        long start = System.currentTimeMillis();

        logger.info("Ranking plugins");

        Map<Integer, Plugin> plugins = database.loadPlugins().stream().collect(Collectors.toMap(Plugin::getId, Function.identity()));
        Map<Integer, Integer> serverCounts = new HashMap<>();

        plugins.forEach((pluginId, plugin) -> serverCounts.put(pluginId, 0));

        try (Jedis redis = redisPool.getResource()) {
            serverCounts.putAll(getPluginServerCounts(redis, bucket));
        }

        Map<Integer, Integer> sortedServerCounts = MapUtil.sortByValue(serverCounts);

        int epoch = ReportHandler.normalizeTime();
        AtomicInteger rank = new AtomicInteger(1);

        sortedServerCounts.forEach((pluginId, serverCount) -> {
            Plugin plugin = plugins.get(pluginId);
            int newRank = rank.getAndIncrement();

            if (newRank != plugin.getRank()) {
                plugin.setLastRankChange(epoch);
                plugin.setLastRank(plugin.getRank());
                plugin.setRank(newRank);
                System.out.println(pluginId + " modified");
            }
        });

        ensurePluginsSaved(new ArrayList<>(plugins.values()));

        long taken = System.currentTimeMillis() - start;
        logger.info("Ranked plugins in " + taken + " ms");
    }

    /**
     * Ensures that all of the given plugins are saved to the database
     *
     * @param plugins
     */
    private void ensurePluginsSaved(List<Plugin> plugins) {
        ExecutorService service = Executors.newFixedThreadPool(NUM_THREADS);
        plugins.forEach(plugin -> service.submit(plugin::saveNow));
        service.shutdown();

        try {
            service.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets all plugin server counts from the given bucket.
     *
     * @param redis
     * @param bucket
     * @return
     */
    private Map<Integer, Integer> getPluginServerCounts(Jedis redis, int bucket) {
        Map<Integer, Integer> result = new HashMap<>();

        Set<Integer> pluginIds = redis.smembers("plugins:" + bucket).stream().map(Integer::parseInt).collect(Collectors.toSet());
        Map<Integer, Response<Long>> responses = new HashMap<>();

        {
            Pipeline pipeline = redis.pipelined();
            pluginIds.forEach(pluginId -> responses.put(pluginId, pipeline.hlen("plugin-data:" + bucket + ":" + pluginId)));
            pipeline.sync();
        }

        responses.forEach((pluginId, response) -> result.put(pluginId, response.get().intValue()));
        return result;
    }

    public static void main(String[] args) {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        Injector injector = Guice.createInjector(new GuiceModule());
        PluginRanker ranker = injector.getInstance(PluginRanker.class);

        ranker.run(1432308600);
    }

}
