package org.mcstats.generator;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Aggregates data from redis and then inserts it into MongoDB
 */
public class RedisGraphAggregator implements Runnable {

    private static final Logger logger = Logger.getLogger(RedisGraphAggregator.class);

    private MCStats mcstats;

    public RedisGraphAggregator(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    public void run() {
        try (Jedis redis = mcstats.getRedisPool().getResource()) {
            List<Integer> plugins = redis.smembers("plugins").stream().map(Integer::parseInt).collect(Collectors.toList());
            plugins.add(-1); // All Plugins

            int epoch = ReportHandler.normalizeTime();
            long start = System.currentTimeMillis();

            plugins.parallelStream().forEach(pluginId -> {
                try (Jedis pluginRedis = mcstats.getRedisPool().getResource()) {
                    Plugin plugin = mcstats.loadPlugin(pluginId);

                    for (Graph graph : mcstats.getDatabase().loadGraphs(plugin)) {
                        plugin.addGraph(graph);
                    }

                    logger.info("Generating data for " + plugin.getName());

                    // all graphs for the plugin
                    Set<String> graphNames = pluginRedis.smembers("graphs:" + pluginId);

                    for (String graphName : graphNames) {
                        // TODO this does not create the graph if it does not exist
                        Graph graph = plugin.getGraph(graphName);

                        if (graph == null || graph.getName() == null) {
                            continue;
                        }

                        List<Tuple<Column, GeneratedData>> data = new ArrayList<>();

                        // all columns for the graph
                        Set<String> columnNames = pluginRedis.smembers("columns:" + pluginId + ":" + graphName);

                        Map<String, Column> columns = new HashMap<>();

                        for (Column column : mcstats.getDatabase().loadColumns(graph)) {
                            columns.put(column.getName(), column);
                        }

                        // future suppliers that are called after the pipeline is closed
                        List<Tuple<Column, Supplier<GeneratedData>>> suppliers = new ArrayList<>();

                        Pipeline pipeline = pluginRedis.pipelined();

                        for (String columnName : columnNames) {
                            Column column = columns.get(columnName);

                            if (column == null || column.getName() == null) {
                                continue;
                            }

                            suppliers.add(new Tuple<>(column, aggregateNew(pipeline, plugin, graphName, columnName)));
                            // GeneratedData generatedData = aggregateOld(pluginRedis, plugin, graphName, columnName);
                        }

                        pipeline.sync();

                        suppliers.forEach(t -> {
                            GeneratedData generatedData = t.second().get();

                            if (generatedData != null) {
                                data.add(new Tuple<>(t.first(), generatedData));
                            }
                        });

                        // TODO insert
                        // mcstats.getGraphStore().batchInsert(graph, data, epoch);
                        if (plugin.getId() == -1) {
                            data.forEach(t -> logger.info(t.first().getName() + " " + t.second()));
                        }
                    }
                }
            });

            long taken = System.currentTimeMillis() - start;

            logger.info("Generation completed in " + taken + "ms");
        }
    }

    /**
     * New aggregate test function: lua aggregation.
     *
     * @param pipeline
     * @param plugin
     * @param graphName
     * @param columnName
     * @return
     */
    private Supplier<GeneratedData> aggregateNew(Pipeline pipeline, Plugin plugin, String graphName, String columnName) {
        String redisDataKey = "data:" + plugin.getId() + ":" + graphName + ":" + columnName;
        String redisDataSumKey = "data-sum:" + plugin.getId() + ":" + graphName + ":" + columnName;

        Response<String> sumValueResponse = pipeline.get(redisDataSumKey);
        Response<Long> countResponse = pipeline.zcard(redisDataKey);
        Response<Set<redis.clients.jedis.Tuple>> minResponse = pipeline.zrangeWithScores(redisDataKey, 0, 0);
        Response<Set<redis.clients.jedis.Tuple>> maxResponse = pipeline.zrangeWithScores(redisDataKey, -1, -1);

        return () -> {
            String sumValue = sumValueResponse.get();

            if (sumValue == null) {
                return null;
            }

            Set<redis.clients.jedis.Tuple> minSet = minResponse.get();
            Set<redis.clients.jedis.Tuple> maxSet = maxResponse.get();

            if (minSet.isEmpty() || maxSet.isEmpty()) {
                return null;
            }

            long count = countResponse.get();
            long sum = castToLong(sumValue);
            long min = castToLong(minSet.iterator().next().getScore());
            long max = castToLong(maxSet.iterator().next().getScore());

            return new GeneratedData((int) sum, (int) count, (int) max, (int) min);
        };
    }

    /**
     * Old aggregate function: works on official redis, but is slower.
     *
     * @param pipeline
     * @param plugin
     * @param graphName
     * @param columnName
     * @return
     */
    private Supplier<GeneratedData> aggregateOld(Pipeline pipeline, Plugin plugin, String graphName, String columnName) {
        String redisDataKey = "data:" + plugin.getId() + ":" + graphName + ":" + columnName;

        Response<Set<redis.clients.jedis.Tuple>> rangeResponse = pipeline.zrangeWithScores(redisDataKey, 0, -1);

        return () -> {
            int sum = 0;
            int count = 0;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;

            for (redis.clients.jedis.Tuple tuple : rangeResponse.get()) {
                int score = (int) tuple.getScore();

                sum += score;
                count++;
                min = Math.min(min, score);
                max = Math.max(max, score);
            }

            return new GeneratedData(sum, count, max, min);
        };
    }

    /**
     * Casts a (hopefully numeric) value to a long.
     *
     * @param value
     * @return
     */
    public long castToLong(Object value) {
        if (value instanceof Long) {
            return (long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof String) {
            return Long.parseLong((String) value);
        } else {
            throw new UnsupportedOperationException("Unsupported type " + value.getClass().getName() + ": " + value.toString());
        }
    }

    public static void main(String[] args) {
        BasicConfigurator.configure();
        MCStats.getInstance().init();
        new RedisGraphAggregator(MCStats.getInstance()).run();
    }

}
