package org.mcstats.db;

import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RedisCache implements ModelCache {

    public static final String PLUGINS_KEY = "plugins";
    public static final String PLUGIN_KEY = "plugin:%d";
    public static final String PLUGIN_NAME_INDEX_KEY = "plugin-index:%s";

    public static final String PLUGIN_GRAPHS_KEY = "plugin-graphs:%d";
    public static final String PLUGIN_GRAPH_INDEX_KEY = "plugin-graph-index:%d:%s";

    private final Database database;
    private final JedisPool pool;

    @Inject
    public RedisCache(Database database, JedisPool pool) {
        this.database = database;
        this.pool = pool;
    }

    @Override
    public Plugin getPlugin(String name) {
        try (Jedis redis = pool.getResource()) {
            String key = String.format(PLUGIN_NAME_INDEX_KEY, name);

            if (redis.exists(key)) {
                return internalGetPlugin(redis, Integer.parseInt(redis.get(key)));
            } else {
                return null;
            }
        }
    }

    @Override
    public Plugin getPlugin(int id) {
        try (Jedis redis = pool.getResource()) {
            return internalGetPlugin(redis, id);
        }
    }

    @Override
    public void cachePlugin(Plugin plugin) {
        String key = String.format(PLUGIN_KEY, plugin.getId());

        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            pipeline.sadd(PLUGINS_KEY, Integer.toString(plugin.getId()));

            pipeline.hset(key, "parent", Integer.toString(plugin.getParent()));
            pipeline.hset(key, "name", plugin.getName());
            pipeline.hset(key, "authors", plugin.getAuthors());
            pipeline.hset(key, "hidden", Integer.toString(plugin.getHidden()));
            pipeline.hset(key, "globalHits", Integer.toString(plugin.getGlobalHits()));
            pipeline.hset(key, "rank", Integer.toString(plugin.getRank()));
            pipeline.hset(key, "lastRank", Integer.toString(plugin.getLastRank()));
            pipeline.hset(key, "lastRankChange", Integer.toString(plugin.getLastRankChange()));
            pipeline.hset(key, "created", Integer.toString(plugin.getCreated()));
            pipeline.hset(key, "lastUpdated", Integer.toString(plugin.getLastUpdated()));
            pipeline.hset(key, "serverCount30", Integer.toString(plugin.getServerCount30()));

            pipeline.set(String.format(PLUGIN_NAME_INDEX_KEY, plugin.getName()), Integer.toString(plugin.getId()));

            pipeline.sync();
        }
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
            Pipeline pipeline = redis.pipelined();

            pipeline.sadd(String.format(PLUGIN_GRAPHS_KEY, plugin.getId()), Integer.toString(graph.getId()));
            pipeline.set(String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId(), graph.getName()), Integer.toString(graph.getId()));
            // TODO other data

            pipeline.sync();
        }
    }

    /**
     * Gets a plugin for the given id.
     *
     * @param redis Redis connection. It will NOT be closed by this method.
     * @param id
     */
    private Plugin internalGetPlugin(Jedis redis, int id) {
        String key = String.format(PLUGIN_KEY, id);

        if (redis.exists(key)) {
            Plugin plugin = new Plugin(database, this);
            // TODO pipeline

            plugin.setId(id);
            plugin.setParent(Integer.parseInt(redis.hget(key, "parent")));
            plugin.setName(redis.hget(key, "name"));
            plugin.setAuthors(redis.hget(key, "authors"));
            plugin.setHidden(Integer.parseInt(redis.hget(key, "hidden")));
            plugin.setGlobalHits(Integer.parseInt(redis.hget(key, "globalHits")));
            plugin.setRank(Integer.parseInt(redis.hget(key, "rank")));
            plugin.setLastRank(Integer.parseInt(redis.hget(key, "lastRank")));
            plugin.setLastRankChange(Integer.parseInt(redis.hget(key, "lastRankChange")));
            plugin.setCreated(Integer.parseInt(redis.hget(key, "created")));
            plugin.setLastUpdated(Integer.parseInt(redis.hget(key, "lastUpdated")));
            plugin.setServerCount30(Integer.parseInt(redis.hget(key, "serverCount30")));

            plugin.setModified(false);

            return plugin;
        } else {
            return null;
        }
    }

}
