package org.mcstats.generator;

import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Aggregates data from redis and then inserts it into MongoDB
 */
public class RedisPluginGraphAggregator implements Runnable {

    private static final Logger logger = Logger.getLogger(RedisPluginGraphAggregator.class);

    private MCStats mcstats;

    public RedisPluginGraphAggregator(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    public void run() {
        try (Jedis redis = mcstats.getRedisPool().getResource()) {
            List<Integer> plugins = redis.smembers("plugins").stream().map(Integer::parseInt).collect(Collectors.toList());
            plugins.add(-1); // All Plugins

            int epoch = ReportHandler.normalizeTime();

            plugins.parallelStream().forEach(pluginId -> {
                try (Jedis pluginRedis = mcstats.getRedisPool().getResource()) {
                    Plugin plugin = mcstats.loadPlugin(pluginId);

                    for (Graph graph : mcstats.getDatabase().loadGraphs(plugin)) {
                        plugin.addGraph(graph);
                    }

                    logger.info("Generating data for " + plugin.getName());

                    // all graphs for the plugin
                    Set<String> graphNames = pluginRedis.smembers("graphs:" + pluginId);

                    // graphName => Set<ColumnName>
                    // note that this pipelines!
                    Map<String, Set<String>> columnsForGraph = loadColumns(pluginRedis, pluginId, graphNames);

                    // future suppliers that are called after the pipeline is closed
                    Map<Graph, Map<Column, GeneratedData>> data = new HashMap<>();
                    Map<Column, Supplier<GeneratedData>> suppliers = new HashMap<>();

                    Pipeline pipeline = pluginRedis.pipelined();

                    for (String graphName : graphNames) {
                        // TODO this does not create the graph if it does not exist
                        Graph graph = plugin.getGraph(graphName);

                        if (graph == null || graph.getName() == null) {
                            continue;
                        }

                        // all columns for the graph
                        Set<String> columnNames = columnsForGraph.get(graphName);

                        if (columnNames == null) {
                            continue;
                        }

                        Map<String, Column> columns = new HashMap<>();

                        for (Column column : mcstats.getDatabase().loadColumns(graph)) {
                            columns.put(column.getName(), column);
                        }

                        for (String columnName : columnNames) {
                            Column column = columns.get(columnName);

                            if (column == null || column.getName() == null) {
                                continue;
                            }

                            suppliers.put(column, aggregateGraphData(pipeline, plugin, graphName, columnName));
                        }
                    }

                    pipeline.sync();

                    suppliers.forEach((column, supplier) -> {
                        GeneratedData generatedData = supplier.get();

                        if (generatedData != null) {
                            Map<Column, GeneratedData> graphData = data.get(column.getGraph());

                            if (graphData == null) {
                                graphData = new HashMap<>();
                                data.put(column.getGraph(), graphData);
                            }

                            graphData.put(column, generatedData);
                        }
                    });

                    // TODO insert
                    // mcstats.getGraphStore().batchInsert(graph, data, epoch);
                }
            });
        }
    }

    /**
     * Loads all columns for the given plugin & graph set
     *
     * @param redis
     * @param pluginId
     * @param graphNames
     * @return
     */
    private Map<String, Set<String>> loadColumns(Jedis redis, int pluginId, Set<String> graphNames) {
        Map<String, Set<String>> columnsForGraph = new HashMap<>();

        Map<String, Supplier<Set<String>>> suppliers = new HashMap<>();
        Pipeline pipeline = redis.pipelined();

        for (String graphName : graphNames) {
            final Response<Set<String>> response = pipeline.smembers("columns:" + pluginId + ":" + graphName);
            suppliers.put(graphName, response::get);
        }

        pipeline.sync();

        // resolve the suppliers
        for (String graphName : graphNames) {
            columnsForGraph.put(graphName, suppliers.get(graphName).get());
        }

        return columnsForGraph;
    }

    /**
     * Aggregates graph data. The returned supplier can only be retrieved AFTER the pipeline has been synced.
     *
     * @param pipeline
     * @param plugin
     * @param graphName
     * @param columnName
     * @return
     */
    private Supplier<GeneratedData> aggregateGraphData(Pipeline pipeline, Plugin plugin, String graphName, String columnName) {
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

}
