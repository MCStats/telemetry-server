package org.mcstats.db;

import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class RedisCache implements ModelCache {

    public static final String PLUGINS_KEY = "plugins";
    public static final String PLUGIN_KEY = "plugin:%d";
    public static final String PLUGIN_NAME_INDEX_KEY = "plugin-index:%s";

    public static final String PLUGIN_GRAPHS_KEY = "plugin-graphs:%d";
    public static final String PLUGIN_GRAPH_KEY = "plugin-graph:%d";
    public static final String PLUGIN_GRAPH_INDEX_KEY = "plugin-graph-index:%d:%s";

    public static final String SERVERS_KEY = "servers";
    public static final String SERVER_KEY = "server:%s";
    public static final String SERVER_LAST_SENT_KEY = "server-last-sent:%s:%d";

    public static final String SERVER_PLUGINS_KEY = "server-plugins:%s";
    public static final String SERVER_PLUGIN_KEY = "server-plugin:%s:%d"; // server-uuid, plugin-id

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

                return server;
            } else {
                return null;
            }
        }
    }

    @Override
    public void cacheServer(Server server) {
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

        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            pipeline.hmset(key, data);
            pipeline.sadd(SERVERS_KEY, server.getUUID());

            pipeline.sync();
        }
    }

    @Override
    public ServerPlugin getServerPlugin(Server server, Plugin plugin) {
        String key = String.format(SERVER_PLUGIN_KEY, server.getUUID(), plugin.getId());

        try (Jedis redis = pool.getResource()) {
            Map<String, String> data = redis.hgetAll(key);

            if (data != null) {
                ServerPlugin serverPlugin = new ServerPlugin(server, plugin);

                serverPlugin.setVersion(data.get("version"));
                serverPlugin.setRevision(Integer.parseInt(data.get("revision")));

                return serverPlugin;
            } else {
                return null;
            }
        }
    }

    @Override
    public void cacheServerPlugin(ServerPlugin serverPlugin) {
        String key = String.format(SERVER_PLUGIN_KEY, serverPlugin.getServer().getUUID(), serverPlugin.getPlugin().getId());

        Map<String, String> data = new HashMap<>();
        data.put("version", serverPlugin.getVersion());
        data.put("revision", Integer.toString(serverPlugin.getRevision()));

        try (Jedis redis = pool.getResource()) {
            Pipeline pipeline = redis.pipelined();

            pipeline.hmset(key, data);
            pipeline.sadd(String.format(SERVER_PLUGINS_KEY, serverPlugin.getServer().getUUID()), Integer.toString(serverPlugin.getPlugin().getId()));
            pipeline.set(String.format(SERVER_LAST_SENT_KEY, serverPlugin.getServer().getUUID(), serverPlugin.getPlugin().getId()), Integer.toString(serverPlugin.getServer().getLastSentData()));

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

            pipeline.sadd(String.format(PLUGIN_GRAPHS_KEY, plugin.getId()), Integer.toString(graph.getId()));

            String key = String.format(PLUGIN_GRAPH_KEY, graph.getId());
            pipeline.hset(key, "plugin", Integer.toString(plugin.getId()));
            pipeline.hset(key, "name", graph.getName());

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
