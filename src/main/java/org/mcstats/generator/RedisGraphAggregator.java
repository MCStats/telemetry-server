package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates data from redis and then inserts it into MongoDB
 */
public class RedisGraphAggregator implements Runnable {

    private MCStats mcstats;

    public RedisGraphAggregator(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    public void run() {
        try (Jedis redis = mcstats.getRedisPool().getResource()) {
            List<Integer> plugins = redis.smembers("plugins").stream().map(Integer::parseInt).collect(Collectors.toList());
            plugins.add(-1); // All Plugins

            int epoch = ReportHandler.normalizeTime();

            // iterate over all plugins
            plugins.forEach(pluginId -> {
                Plugin plugin = mcstats.loadPlugin(pluginId);

                System.out.println("Generating data for " + plugin.getName());

                // all graphs for the plugin
                Set<String> graphNames = redis.smembers("graphs:" + pluginId);

                for (String graphName : graphNames) {
                    Graph graph = mcstats.getDatabase().loadGraph(plugin, graphName);
                    List<Tuple<Column, GeneratedData>> data = new ArrayList<>();

                    // all columns for the graph
                    Set<String> columnNames = redis.smembers("columns:" + pluginId + ":" + graphName);

                    for (String columnName : columnNames) {
                        Column column = mcstats.getDatabase().loadColumn(graph, columnName);

                        String redisDataKey = String.format("data:%d:%s:%s", plugin.getId(), graphName, columnName);

                        Set<redis.clients.jedis.Tuple> redisData = redis.zrangeWithScores(redisDataKey, 0, -1);

                        // data aggregation
                        int sum = 0;
                        int count = 0;
                        int min = Integer.MAX_VALUE;
                        int max = Integer.MIN_VALUE;

                        for (redis.clients.jedis.Tuple tuple : redisData) {
                            int score = (int) tuple.getScore();

                            sum += score;
                            count++;
                            min = Math.min(min, score);
                            max = Math.max(max, score);
                        }

                        data.add(new Tuple<>(column, new GeneratedData(sum, count, max, min)));
                    }

                    // TODO insert
                    // mcstats.getGraphStore().batchInsert(graph, data, epoch);
                    data.forEach(t -> System.out.println(t.first().getName() + " " + t.second()));
                }
            });
        }
    }

    public static void main(String[] args) {
        MCStats.getInstance().init();
        new RedisGraphAggregator(MCStats.getInstance()).run();
    }

}
