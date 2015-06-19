package org.mcstats.db;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginGraph;
import org.mcstats.model.PluginGraphColumn;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Singleton
public class RedisCache implements ModelCache {

    public static final String PLUGINS_KEY = "plugins"; // set: id
    public static final String PLUGINS_INDEX_KEY = "plugins-index"; // key: name.toLowerCase() -> id
    public static final String PLUGIN_KEY = "plugin:%d"; // hash: data

    public static final String PLUGIN_GRAPH_KEY = "plugin-graphs:%d"; // hash: id -> name
    public static final String PLUGIN_GRAPH_INDEX_KEY = "plugin-graphs-index:%d"; // hash: name.toLowerCase() -> id
    public static final String PLUGIN_GRAPH_COLUMNS_KEY = "plugin-graph-columns:%d"; // hash: id -> name

    public static final String SERVER_LAST_SENT_KEY = "server-last-sent:%s"; // hash: plugin-id -> last-sent

    private final Database database;
    private final JedisPool pool;

    /**
     * A cache mapping plugin names to ids.
     */
    private LoadingCache<String, Integer> pluginNameToId = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .build(new CacheLoader<String, Integer>() {
                @Override
                public Integer load(String pluginName) throws Exception {
                    try (Jedis redis = pool.getResource()) {
                        String id = redis.hget(PLUGINS_INDEX_KEY, pluginName.toLowerCase());

                        if (id != null) {
                            return Integer.parseInt(id);
                        } else {
                            throw new Exception("Plugin not found: " + pluginName);
                        }
                    }
                }
            });

    @Inject
    public RedisCache(Database database, JedisPool pool) {
        this.database = database;
        this.pool = pool;
    }

    @Override
    public int getPluginId(String name) {
        try {
            return pluginNameToId.get(name);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return Integer.MIN_VALUE;
        }
    }

    @Override
    public Plugin getPlugin(String name) {
        int id;

        try {
            id = pluginNameToId.get(name);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        }

        try (Jedis redis = pool.getResource()) {
            return internalGetPlugin(redis, id);
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
        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            cachePlugin(plugin, pipeline);

            pipeline.sync();
        }
    }

    @Override
    public PluginGraph getPluginGraph(Plugin plugin, String name) {
        String key = String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId());

        try (Jedis redis = pool.getResource()) {
            String id = redis.hget(key, name.toLowerCase());

            if (id != null) {
                return new PluginGraph(plugin, Integer.parseInt(id), name);
            } else {
                return null;
            }
        }
    }

    @Override
    public PluginGraph getPluginGraph(Plugin plugin, int id) {
        String key = String.format(PLUGIN_GRAPH_KEY, plugin.getId());

        try (Jedis redis = pool.getResource()) {
            String name = redis.hget(key, Integer.toString(id));

            if (name != null) {
                return new PluginGraph(plugin, id, name);
            } else {
                return null;
            }
        }
    }

    @Override
    public void cachePluginGraph(Plugin plugin, PluginGraph graph) {
        if (!graph.isFromDatabase()) {
            throw new UnsupportedOperationException("Graph to be cached cannot be virtual.");
        }

        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            cachePluginGraph(plugin, graph, pipeline);

            pipeline.sync();
        }
    }

    @Override
    public List<PluginGraphColumn> getPluginGraphColumns(PluginGraph graph) {
        List<PluginGraphColumn> columns = new ArrayList<>();

        String key = String.format(PLUGIN_GRAPH_COLUMNS_KEY, graph.getId());

        try (Jedis redis = pool.getResource()) {
            Map<String, String> data = redis.hgetAll(key);

            data.forEach((columnId, columnName) -> {
                PluginGraphColumn column = new PluginGraphColumn(graph, columnName);
                column.initFromDatabase(Integer.parseInt(columnId));

                columns.add(column);
            });
        }

        return columns;
    }

    @Override
    public void cachePluginGraphColumns(PluginGraph graph, List<PluginGraphColumn> columns) {
        String key = String.format(PLUGIN_GRAPH_COLUMNS_KEY, graph.getId());

        Map<String, String> data = new HashMap<>();
        columns.forEach(column -> data.put(Integer.toString(column.getId()), column.getName()));

        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            pipeline.hmset(key, data);

            pipeline.sync();
        }
    }

    /**
     * Caches a plugin the the given pipeline
     *
     * @param plugin
     * @param pipeline
     */
    public void cachePlugin(Plugin plugin, Pipeline pipeline) {
        String key = String.format(PLUGIN_KEY, plugin.getId());

        // TODO hmset
        pipeline.hset(key, "name", plugin.getName());
        pipeline.hset(key, "type", plugin.getType());
        pipeline.hset(key, "rank", Integer.toString(plugin.getRank()));
        pipeline.hset(key, "last_rank", Integer.toString(plugin.getLastRank()));
        pipeline.hset(key, "last_rank_change", Integer.toString(plugin.getLastRankChange()));
        pipeline.hset(key, "created_at", Long.toString(plugin.getCreatedAt().getTime()));

        if (plugin.getUpdatedAt() != null) {
            pipeline.hset(key, "updated_at", Long.toString(plugin.getUpdatedAt().getTime()));
        } else {
            pipeline.hset(key, "updated_at", "0");
        }

        pipeline.sadd(PLUGINS_KEY, Integer.toString(plugin.getId()));
        pipeline.hset(PLUGINS_INDEX_KEY, plugin.getName().toLowerCase(), Integer.toString(plugin.getId()));
    }

    /**
     * Caches a plugin's graph with the given pipeline
     *
     * @param plugin
     * @param graph
     * @param pipeline
     */
    public void cachePluginGraph(Plugin plugin, PluginGraph graph, Pipeline pipeline) {
        if (!graph.isFromDatabase()) {
            throw new UnsupportedOperationException("Graph to be cached cannot be virtual.");
        }

        pipeline.hset(String.format(PLUGIN_GRAPH_KEY, plugin.getId()), Integer.toString(graph.getId()), graph.getName());
        pipeline.hset(String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId()), graph.getName().toLowerCase(), Integer.toString(graph.getId()));
    }

    /**
     * Gets a plugin for the given id.
     *
     * @param redis Redis connection. It will NOT be closed by this method.
     * @param id
     */
    private Plugin internalGetPlugin(Jedis redis, int id) {
        String key = String.format(PLUGIN_KEY, id);

        Map<String, String> data = redis.hgetAll(key);

        if (data != null) {
            Plugin plugin = new Plugin(database, this);

            plugin.setId(id);
            plugin.setName(data.get("name"));
            plugin.setType(data.get("type"));
            plugin.setHidden(Integer.parseInt(data.get("hidden")) == 1);
            plugin.setRank(Integer.parseInt(data.get("rank")));
            plugin.setLastRank(Integer.parseInt(data.get("last_rank")));
            plugin.setLastRankChange(Integer.parseInt(data.get("last_rank_changed")));
            plugin.setCreatedAt(new Date(Long.parseLong(data.get("created_at"))));
            plugin.setUpdatedAt(new Date(Long.parseLong(data.get("updated_at"))));

            plugin.setModified(false);

            return plugin;
        } else {
            return null;
        }
    }

}
