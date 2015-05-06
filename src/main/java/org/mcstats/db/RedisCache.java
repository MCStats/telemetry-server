package org.mcstats.db;

import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisCache implements ModelCache {

    public static final String PLUGIN_GRAPHS_KEY = "plugin-graphs:%d";
    public static final String PLUGIN_GRAPH_INDEX_KEY = "plugin-graph-index:%d:%s";


    /**
     * The redis pool
     */
    private JedisPool pool;

    public RedisCache(JedisPool pool) {
        this.pool = pool;
    }

    @Override
    public Graph getPluginGraph(Plugin plugin, String name) {
        String key = String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId(), name);

        try (Jedis redis = pool.getResource()) {
            if (redis.exists(key)) {
                int id = Integer.parseInt(redis.get(key));
                return new Graph(plugin, id, name);
            } else {
                return null;
            }
        }
    }

    @Override
    public Graph getPluginGraph(Plugin plugin, int id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void cachePluginGraph(Plugin plugin, Graph graph) {
        if (!graph.isFromDatabase()) {
            throw new UnsupportedOperationException("Graph to be cached cannot be virtual.");
        }

        try (Jedis redis = pool.getResource()) {
            redis.sadd(String.format(PLUGIN_GRAPHS_KEY, plugin.getId()), Integer.toString(graph.getId()));
            redis.set(String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId(), graph.getName()), Integer.toString(graph.getId()));
            // TODO other data
        }
    }

}
