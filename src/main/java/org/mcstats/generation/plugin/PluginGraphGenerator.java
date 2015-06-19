package org.mcstats.generation.plugin;

import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.aws.s3.AccumulatorStorage;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.ModelCache;
import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Plugin;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PluginGraphGenerator {

    private static final Logger logger = Logger.getLogger(PluginGraphGenerator.class);

    private final MCStats mcstats;
    private final Database database;
    private final ModelCache modelCache;
    private final GraphStore graphStore;
    private final AccumulatorStorage accumulatorStorage;
    private final int numThreads;

    @Inject
    public PluginGraphGenerator(@Named("generator.threads") int numThreads,
                                MCStats mcstats, Database database, ModelCache modelCache, GraphStore graphStore, AccumulatorStorage accumulatorStorage) {
        this.mcstats = mcstats;
        this.database = database;
        this.modelCache = modelCache;
        this.graphStore = graphStore;
        this.accumulatorStorage = accumulatorStorage;
        this.numThreads = numThreads;
    }

    /**
     * Generates data from the given bucket.
     *
     * @param bucket
     */
    public void run(int bucket) {
        logger.info("Generating data from bucket: " + bucket);

        long start = System.currentTimeMillis();

        Map<Integer, Map<String, Map<String, Long>>> data = accumulatorStorage.getPluginData(bucket);

        if (data == null) {
            logger.error("No bucket found: " + bucket);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // graphId, data
        Map<Plugin, Map<Integer, List<GeneratedData>>> generatedGraphs = new ConcurrentHashMap<>();

        data.keySet().forEach(pluginId -> executor.submit(() -> {
            Map<String, Map<String, Long>> pluginData = data.get(pluginId);

            final Plugin plugin = mcstats.loadPlugin(pluginId);
            Map<String, Integer> cachedGraphs = modelCache.getPluginGraphs(plugin);

            logger.debug("Generating data for plugin: " + plugin.getId());
            Map<Integer, List<GeneratedData>> generatedPluginData = new HashMap<>();

            pluginData.forEach((graphName, graphData) -> {
                int graphId;

                if (cachedGraphs.containsKey(graphName)) {
                    graphId = cachedGraphs.get(graphName);
                } else {
                    graphId = plugin.getGraph(graphName).getId();
                }

                List<GeneratedData> generatedData = new ArrayList<>();

                graphData.forEach((columnName, value) -> {
                    generatedData.add(new GeneratedData(columnName, value.intValue(), 0, 0, 0));
                });

                generatedPluginData.put(graphId, generatedData);
            });

            generatedGraphs.put(plugin, generatedPluginData);
        }));

        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                logger.error("Graph generation failed");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        graphStore.insert(generatedGraphs, bucket);

        long taken = System.currentTimeMillis() - start;
        logger.info("Generated bucket " + bucket + " in " + taken + " ms");
    }

}
