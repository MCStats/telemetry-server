package org.mcstats;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import it.sauronsoftware.cron4j.Scheduler;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.mcstats.cron.CronGraphGenerator;
import org.mcstats.cron.CronRanking;
import org.mcstats.db.Database;
import org.mcstats.db.DatabaseQueue;
import org.mcstats.db.GraphStore;
import org.mcstats.db.PostgresDatabase;
import org.mcstats.generator.PluginGenerator;
import org.mcstats.generator.aggregator.DecoderReflectionAggregator;
import org.mcstats.generator.aggregator.IncrementAggregator;
import org.mcstats.generator.aggregator.ReflectionAggregator;
import org.mcstats.generator.aggregator.ReflectionDonutAggregator;
import org.mcstats.generator.aggregator.plugin.CountryAggregator;
import org.mcstats.generator.aggregator.plugin.CustomDataPluginAggregator;
import org.mcstats.generator.aggregator.plugin.RankPluginAggregator;
import org.mcstats.generator.aggregator.plugin.RevisionPluginAggregator;
import org.mcstats.generator.aggregator.plugin.VersionDemographicsPluginAggregator;
import org.mcstats.generator.aggregator.plugin.VersionTrendsPluginAggregator;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.RequestCalculator;
import org.mcstats.util.ServerBuildIdentifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class MCStats {

    private Logger logger = Logger.getLogger("MCStats");

    /**
     * The MCStats instance
     */
    private static final MCStats instance = new MCStats();

    /**
     * The amount of requests that have been served
     */
    private AtomicLong requests = new AtomicLong(0);

    /**
     * MCStats configuration
     */
    private Properties config;

    /**
     * The pg database
     */
    private PostgresDatabase database;

    /**
     * The database save queue
     */
    private DatabaseQueue databaseQueue;

    /**
     * The report handler for requests
     */
    private PluginTelemetryHandler handler = new PluginTelemetryHandler(this);

    /**
     * The server build identifier
     */
    private final ServerBuildIdentifier serverBuildIdentifier = new ServerBuildIdentifier();

    /**
     * The request calculator for requests per second since the server started
     */
    private final RequestCalculator requestsAllTime;

    /**
     * The request calculator for requests per second for the last 5 seconds
     */
    private final RequestCalculator requestsFiveSeconds;

    /**
     * Debug mode
     */
    private boolean debug = false;

    /**
     * A map of all of the currently loaded servers
     */
    private final LoadingCache<String, Server> servers = CacheBuilder.newBuilder()
            .maximumSize(400000) // 100k
            .build(new CacheLoader<String, Server>() {

                public Server load(String key) {
                    return new Server(key);
                }

            });

    /**
     * A map of all of the currently loaded pluginsByName, by the plugin's name
     */
    private final Map<String, Plugin> pluginsByName = new ConcurrentHashMap<>();

    /**
     * A map of all of the currently loaded pluginsByName, by the plugin's internal id
     */
    private final Map<Integer, Plugin> pluginsById = new ConcurrentHashMap<>();

    /**
     * Cache of server plugins mapped by their plugins
     */
    private final Map<Plugin, Set<ServerPlugin>> serverPluginsByPlugin = new ConcurrentHashMap<>();

    private MCStats() {
        Callable<Long> requestsCallable = requests::get;

        requestsAllTime = new RequestCalculator(RequestCalculator.CalculationMethod.ALL_TIME, requestsCallable);
        requestsFiveSeconds = new RequestCalculator(RequestCalculator.CalculationMethod.FIVE_SECONDS, requestsCallable);
    }

    /**
     * Reset data used for each interval
     */
    public void resetIntervalData() {
        servers.invalidateAll();
        resetInternalCaches();
        serverPluginsByPlugin.clear();
    }

    /**
     * Count the number of servers that recently sent data
     *
     * @return
     */
    public int countRecentServers() {
        int count = 0;

        for (Server server : getCachedServers()) {
            if (server.recentlySentData()) {
                count ++;
            }
        }

        return count;
    }

    /**
     * Reset any internal caches
     */
    public void resetInternalCaches() {
        databaseQueue.clear();
    }

    /**
     * Starts the MCStats backend
     */
    public void start() {
        config = new Properties();

        try {
            config.load(new FileInputStream("mcstats.properties"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        debug = config.getProperty("debug").equalsIgnoreCase("true");

        logger.info("Starting MCStats");
        logger.info("Debug mode is " + (debug ? "ON" : "OFF"));

        databaseQueue = new DatabaseQueue(this);

        // Connect to the database
        connectToDatabase();

        // Load all of the pluginsByName
        for (Plugin plugin : database.loadPlugins()) {
            if (plugin.getId() >= 0) {
                addPlugin(plugin);
                serverPluginsByPlugin.put(plugin, Sets.newSetFromMap(new ConcurrentHashMap<>()));
            }
        }

        logger.info("Loaded " + pluginsByName.size() + " plugins");

        int numGraphs = 0;
        for (Plugin plugin : pluginsByName.values()) {
            for (Graph graph : database.loadGraphs(plugin)) {
                plugin.addGraph(graph);
                numGraphs ++;
            }
        }

        logger.info("Loaded " + numGraphs + " graphs");

        if (Boolean.parseBoolean(config.getProperty("graphs.generate"))) {
            Scheduler scheduler = new Scheduler();
            scheduler.schedule("*/30 * * * *", new CronGraphGenerator(this, createPluginGenerator()));
            scheduler.schedule("45 * * * *", new CronRanking(this));
            scheduler.start();
            logger.info("Graph & rank generator is active");
        } else {
            logger.info("Graph & rank generator is NOT active");
        }

        new Scheduler().schedule("*/5 * * * *", () -> {
            System.gc();
            System.runFinalization();
            System.gc();
        });
    }

    /**
     * Get the config
     *
     * @return
     */
    public Properties getConfig() {
        return config;
    }

    /**
     * Check if the service is in debug mode
     *
     * @return
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Get an unmodifiable list of the cached servers
     *
     * @return
     */
    public List<Server> getCachedServers() {
        return Collections.unmodifiableList(new ArrayList<>(servers.asMap().values()));
    }

    /**
     * Get an unmodifiable list of the cached plugins
     *
     * @return
     */
    public List<Plugin> getCachedPlugins() {
        return Collections.unmodifiableList(new ArrayList<>(pluginsById.values()));
    }

    /**
     * Get the server plugins for a given plugin
     *
     * @param plugin
     * @return
     */
    public Set<ServerPlugin> getServerPlugins(Plugin plugin) {
        return serverPluginsByPlugin.containsKey(plugin) ? serverPluginsByPlugin.get(plugin) : new HashSet<>();
    }

    /**
     * Notify an addition of server plugin
     *
     * @param serverPlugin
     */
    public void notifyServerPlugin(ServerPlugin serverPlugin) {
        if (serverPlugin == null) {
            return;
        }

        Set<ServerPlugin> serverPlugins = serverPluginsByPlugin.get(serverPlugin.getPlugin());

        if (serverPlugins == null) {
            serverPlugins = Sets.newSetFromMap(new ConcurrentHashMap<>());
            serverPluginsByPlugin.put(serverPlugin.getPlugin(), serverPlugins);
        }

        serverPlugins.add(serverPlugin);
    }

    /**
     * Increment and return the amount of requests server on the server
     *
     * @return
     */
    public long incrementAndGetRequests() {
        return requests.incrementAndGet();
    }

    /**
     * Load a plugin using its id
     *
     * @param id
     * @return
     */
    public Plugin loadPlugin(int id) {
        if (pluginsById.containsKey(id)) {
            return pluginsById.get(id);
        }

        // Attempt to load from the database
        Plugin plugin = database.loadPlugin(id);

        // Did we not find it ?
        if (plugin == null) {
            return null;
        }

        // Cache it
        addPlugin(plugin);

        // and go !
        return plugin;
    }

    /**
     * Load a plugin and if it does not exist it will be created
     *
     * @param name
     * @return
     */
    public Plugin loadPlugin(String name) {
        String cacheKey = name.toLowerCase();

        if (pluginsByName.containsKey(cacheKey)) {
            return pluginsByName.get(cacheKey);
        }

        logger.info("Plugin not cached: " + name);

        // Attempt to load from the database
        Plugin plugin = database.loadPlugin(name);

        // Did we not find it ?
        if (plugin == null) {
            plugin = database.createPlugin(name);
        }

        // is it _still_ null?
        // if it is, we have some problems :(
        if (plugin == null) {
            logger.error("Failed to create plugin for \"" + name + "\"");
            return null;
        }

        // Cache it
        addPlugin(plugin);

        // and go !
        return plugin;
    }

    /**
     * Load a server and if it does not exist it will be created
     *
     * @param guid
     * @return
     */
    public Server loadServer(String guid) {
        try {
            return servers.get(guid); /* automatically loaded by CacheLoader if needed */
        } catch (ExecutionException e) {
            logger.error("Exception occurred while loading server (loadServer(" + guid + "))",  e);
            return null;
        }
    }

    /**
     * Creates a new plugin generator
     *
     * @return
     */
    private PluginGenerator createPluginGenerator() {
        PluginGenerator generator = new PluginGenerator(this::getCachedServers);

        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Server Software", "serverSoftware"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Game Version", "minecraftVersion"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "System Arch", "osarch"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "System Cores", "cores"));

        generator.addAggregator(new ReflectionDonutAggregator<>(Server.class, "Operating System", "osname", "osversion"));
        generator.addAggregator(new ReflectionDonutAggregator<>(Server.class, "Java Version", "java_name", "java_version"));

        generator.addAggregator(new IncrementAggregator<>("Global Statistics", "Servers"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Global Statistics", "Players", "players"));

        generator.addAggregator(new DecoderReflectionAggregator<Server, Integer>(Server.class, "Auth Mode", "online_mode", value -> {
            switch (value) {
                case 1:
                    return "Online";
                case 0:
                    return "Offline";
                default:
                    return "Unknown";
            }
        }));

        generator.addAggregator(new CountryAggregator());
        generator.addAggregator(new RankPluginAggregator());
        generator.addAggregator(new RevisionPluginAggregator());
        generator.addAggregator(new CustomDataPluginAggregator());
        generator.addAggregator(new VersionDemographicsPluginAggregator());
        generator.addAggregator(new VersionTrendsPluginAggregator());

        return generator;
    }

    /**
     * Connect to the database
     */
    private void connectToDatabase() {
        // Create the database
        database = new PostgresDatabase(this, config.getProperty("postgres.hostname"), config.getProperty("postgres.database"),
                config.getProperty("postgres.username"), config.getProperty("postgres.password"));

        logger.info("Connected to PostgreSQL");
    }

    /**
     * Get the database mcstats is connected to
     *
     * @return
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Get the storage for graphs
     *
     * @return
     */
    public GraphStore getGraphStore() {
        return database;
    }

    /**
     * Get the database queue
     *
     * @return
     */
    public DatabaseQueue getDatabaseQueue() {
        return databaseQueue;
    }

    /**
     * Get the request calculator for all-time requests
     *
     * @return
     */
    public RequestCalculator getRequestCalculatorAllTime() {
        return requestsAllTime;
    }

    /**
     * Get the request calculator for requests in the last 5 seconds
     *
     * @return
     */
    public RequestCalculator getRequestCalculatorFiveSeconds() {
        return requestsFiveSeconds;
    }

    /**
     * Get the {@link PluginTelemetryHandler}
     * @return
     */
    public PluginTelemetryHandler getReportHandler() {
        return handler;
    }

    /**
     * Add a plugin to the cache
     *
     * @param plugin
     */
    private void addPlugin(Plugin plugin) {
        pluginsById.put(plugin.getId(), plugin);
        pluginsByName.put(plugin.getName().toLowerCase(), plugin);
    }

    /**
     * Add a plugin to the cache with the given name
     *
     * @param name
     * @param plugin
     */
    private void addPlugin(String name, Plugin plugin) {
        pluginsById.put(plugin.getId(), plugin);
        pluginsByName.put(name.toLowerCase().toLowerCase(), plugin);
    }

    /**
     * Get the MCStats instance
     *
     * @return
     */
    public static MCStats getInstance() {
        return instance;
    }

    public ServerBuildIdentifier getServerBuildIdentifier() {
        return serverBuildIdentifier;
    }
}
