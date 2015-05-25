package org.mcstats.generator;

import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.aws.s3.AccumulatorStorage;
import org.mcstats.db.GraphStore;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginGraphGenerator {

    private static final Logger logger = Logger.getLogger(PluginGraphGenerator.class);

    private final MCStats mcstats;
    private final JedisPool redisPool;
    private final GraphStore graphStore;
    private final AccumulatorStorage accumulatorStorage;

    @Inject
    public PluginGraphGenerator(MCStats mcstats, JedisPool redisPool, GraphStore graphStore, AccumulatorStorage accumulatorStorage) {
        this.mcstats = mcstats;
        this.redisPool = redisPool;
        this.graphStore = graphStore;
        this.accumulatorStorage = accumulatorStorage;
    }

    /**
     * Generates data from the given bucket.
     *
     * @param bucket
     */
    public void generateBucket(int bucket) {
        logger.info("Generating data from bucket: " + bucket);

        long start = System.currentTimeMillis();

        Map<Integer, Map<String, Map<String, Long>>> data = accumulatorStorage.getPluginData(bucket);

        data.keySet().parallelStream().forEach(pluginId -> {
            Map<String, Map<String, Long>> pluginData = data.get(pluginId);

            final Plugin plugin = mcstats.loadPlugin(pluginId);
            Map<Graph, List<Tuple<Column, Long>>> generatedGraphs = new HashMap<>();

            logger.debug("Generating data for plugin: " + plugin.getId());

            pluginData.forEach((graphName, graphData) -> {
                List<Tuple<Column, Long>> generatedData = new ArrayList<>();
                Graph graph = plugin.getGraph(graphName);

                graphData.forEach((columnName, value) -> {
                    // TODO get/create column
                    Column column = null;

                    generatedData.add(new Tuple<>(column, value));
                });

                generatedGraphs.put(graph, generatedData);
            });

            // graphStore.insert(generatedGraphs, epoch);
        });

        long taken = System.currentTimeMillis() - start;
        logger.info("Generated bucket " + bucket + " in " + taken + " ms");
    }

}
