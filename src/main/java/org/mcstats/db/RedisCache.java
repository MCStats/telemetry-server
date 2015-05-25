package org.mcstats.db;

import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class RedisCache implements ModelCache {

    public static final String PLUGINS_KEY = "plugins";
    public static final String PLUGIN_KEY = "plugin:%d";
    public static final String PLUGIN_NAME_INDEX_KEY = "plugin-index:%s";

    public static final String PLUGIN_GRAPHS_KEY = "plugin-graphs:%d";
    public static final String PLUGIN_GRAPH_KEY = "plugin-graph:%d";
    public static final String PLUGIN_GRAPH_INDEX_KEY = "plugin-graph-index:%d:%s";

    public static final String PLUGIN_GRAPH_COLUMNS_KEY = "plugin-graph-columns:%d"; // hash: id -> name

    public static final String SERVERS_KEY = "servers";
    public static final String SERVER_KEY = "server:%s";
    public static final String SERVER_LAST_SENT_KEY = "server-last-sent:%s:%d";

    private final Database database;
    private final JedisPool pool;

    @Inject
    public RedisCache(Database database, JedisPool pool) {
        this.database = database;
        this.pool = pool;
    }

    @Override
    public Plugin getPlugin(String name) {
        String key = String.format(PLUGIN_NAME_INDEX_KEY, name);

        try (Jedis redis = pool.getResource()) {
            String id = redis.get(key);

            if (id != null) {
                return internalGetPlugin(redis, Integer.parseInt(id));
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
        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            cachePlugin(plugin, pipeline);

            pipeline.sync();
        }
    }

    @Override
    public Server getServer(String uuid) {
        String key = String.format(SERVER_KEY, uuid);

        try (Jedis redis = pool.getResource()) {
            Map<String, String> data = redis.hgetAll(key);

            if (data != null) {
                Server server = new Server(uuid);

                try {
                    server.setJavaName(data.get("java.name"));
                    server.setJavaVersion(data.get("java.version"));
                    server.setOSName(data.get("os.name"));
                    server.setOSVersion(data.get("os.version"));
                    server.setOSArch(data.get("os.arch"));
                    server.setOnlineMode(Integer.parseInt(data.get("authMode")));
                    server.setCountry(data.get("country"));
                    server.setServerSoftware(data.get("serverSoftware"));
                    server.setMinecraftVersion(data.get("minecraftVersion"));
                    server.setPlayers(Integer.parseInt(data.get("players.online")));
                    server.setCores(Integer.parseInt(data.get("cores")));

                    server.setViolationCount(Integer.parseInt(data.get("violations")));
                    server.setLastSentData(Integer.parseInt(data.get("lastSent")));
                } catch (Exception e) { // Ignores formatting errors and the like
                    return null;
                }

                return server;
            } else {
                return null;
            }
        }
    }

    @Override
    public void cacheServer(Server server) {
        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            cacheServer(server, pipeline);

            pipeline.sync();
        }
    }

    @Override
    public Graph getPluginGraph(Plugin plugin, String name) {
        String key = String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId(), name);

        try (Jedis redis = pool.getResource()) {
            String id = redis.get(key);

            if (id != null) {
                return new Graph(plugin, Integer.parseInt(id), name);
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

            cachePluginGraph(plugin, graph, pipeline);

            pipeline.sync();
        }
    }

    @Override
    public List<Column> getPluginGraphColumns(Graph graph) {
        List<Column> columns = new ArrayList<>();

        String key = String.format(PLUGIN_GRAPH_COLUMNS_KEY, graph.getId());

        try (Jedis redis = pool.getResource()) {
            Map<String, String> data = redis.hgetAll(key);

            data.forEach((columnId, columnName) -> {
                Column column = new Column(graph, columnName);
                column.initFromDatabase(Integer.parseInt(columnId));

                columns.add(column);
            });
        }

        return columns;
    }

    @Override
    public void cachePluginGraphColumns(Graph graph, List<Column> columns) {
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
     * @param plugin
     * @param pipeline
     */
    public void cachePlugin(Plugin plugin, Pipeline pipeline) {
        String key = String.format(PLUGIN_KEY, plugin.getId());

        pipeline.sadd(PLUGINS_KEY, Integer.toString(plugin.getId()));

        // TODO hmset
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
    }

    /**
     * Caches a server with the given pipeline
     *
     * @param server
     * @param pipeline
     */
    public void cacheServer(Server server, Pipeline pipeline) {
        String key = String.format(SERVER_KEY, server.getUUID());

        Map<String, String> data = new HashMap<>();

        data.put("country", server.getCountry());
        data.put("serverSoftware", server.getServerVersion());
        data.put("minecraftVersion", server.getMinecraftVersion());
        data.put("players.online", Integer.toString(server.getPlayers()));
        data.put("os.name", server.getOSName());
        data.put("os.version", server.getOSVersion());
        data.put("os.arch", server.getOSArch());
        data.put("java.name", server.getJavaName());
        data.put("java.version", server.getJavaVersion());
        data.put("cores", Integer.toString(server.getCores()));
        data.put("authMode", Integer.toString(server.getOnlineMode()));

        data.put("violations", Integer.toString(server.getViolationCount()));
        data.put("lastSent", Integer.toString(server.getLastSentData()));

        pipeline.hmset(key, data);
        pipeline.sadd(SERVERS_KEY, server.getUUID());
    }

    /**
     * Caches a plugin's graph with the given pipeline
     *
     * @param plugin
     * @param graph
     * @param pipeline
     */
    public void cachePluginGraph(Plugin plugin, Graph graph, Pipeline pipeline) {
        if (!graph.isFromDatabase()) {
            throw new UnsupportedOperationException("Graph to be cached cannot be virtual.");
        }

        pipeline.sadd(String.format(PLUGIN_GRAPHS_KEY, plugin.getId()), Integer.toString(graph.getId()));

        String key = String.format(PLUGIN_GRAPH_KEY, graph.getId());
        pipeline.hset(key, "plugin", Integer.toString(plugin.getId()));
        pipeline.hset(key, "name", graph.getName());

        pipeline.set(String.format(PLUGIN_GRAPH_INDEX_KEY, plugin.getId(), graph.getName()), Integer.toString(graph.getId()));
        // TODO other data
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
            plugin.setParent(Integer.parseInt(data.get("parent")));
            plugin.setName(data.get("name"));
            plugin.setAuthors(data.get("authors"));
            plugin.setHidden(Integer.parseInt(data.get("hidden")));
            plugin.setGlobalHits(Integer.parseInt(data.get("globalHits")));
            plugin.setRank(Integer.parseInt(data.get("rank")));
            plugin.setLastRank(Integer.parseInt(data.get("lastRank")));
            plugin.setLastRankChange(Integer.parseInt(data.get("lastRankChange")));
            plugin.setCreated(Integer.parseInt(data.get("created")));
            plugin.setLastUpdated(Integer.parseInt(data.get("lastUpdated")));
            plugin.setServerCount30(Integer.parseInt(data.get("serverCount30")));

            plugin.setModified(false);

            return plugin;
        } else {
            return null;
        }
    }

}
