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
import org.eclipse.jetty.webapp.WebAppContext;
import org.mcstats.cron.CronGraphGenerator;
import org.mcstats.cron.CronRanking;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.db.PluginOnlyMySQLDatabase;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.jetty.ServerTelemetryHandler;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginVersion;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.RequestCalculator;
import org.mcstats.util.ServerBuildIdentifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
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
     * The web server object
     */
    private org.eclipse.jetty.server.Server webServer;

    /**
     * The amount of requests that have been served
     */
    private AtomicLong requests = new AtomicLong(0);

    /**
     * MCStats configuration
     */
    private Properties config;

    /**
     * The database we are connected to
     */
    private Database database;

    /**
     * The storage for graph data
     */
    private GraphStore graphStore;

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
                    Server server = database.loadServer(key);

                    if (server == null) {
                        server = database.createServer(key);
                    }

                    if (server == null) {
                        logger.error("Failed to create server for \"" + key + "\"");
                        return null;
                    }

                    // Now load the plugins
                    for (ServerPlugin serverPlugin : database.loadServerPlugins(server)) {
                        server.addPlugin(serverPlugin);
                    }

                    if (database.isServerBlacklisted(server)) {
                        server.setBlacklisted(true);
                    }

                    return server;
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
     * A map of all countries, keyed by the 2 letter country code
     */
    private final Map<String, String> countries = new ConcurrentHashMap<>();

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
        if (database instanceof PluginOnlyMySQLDatabase) {
            ((PluginOnlyMySQLDatabase) database).resetIntervalData();
            servers.invalidateAll();
            resetInternalCaches();
            serverPluginsByPlugin.clear();
        }
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
        handler.clearQueue();
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

        countries.putAll(database.loadCountries());
        logger.info("Loaded " + countries.size() + " countries");

        graphStore = new MongoDBGraphStore(this);

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

        // Create & open the webserver
        createWebServer();
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
     * Get the shortcode for a country
     *
     * @param shortCode
     * @return
     */
    public String getCountryName(String shortCode) {
        return countries.get(shortCode);
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
     * Load a version for the given plugin
     *
     * @param plugin
     * @param version
     * @return
     */
    public PluginVersion loadPluginVersion(Plugin plugin, String version) {
        PluginVersion pluginVersion = plugin.getVersionByName(version);

        if (pluginVersion != null) {
            return pluginVersion;
        }

        // attempt to load it
        pluginVersion = database.loadPluginVersion(plugin, version);

        if (pluginVersion == null) {
            // Create it
            pluginVersion = database.createPluginVersion(plugin, version);
        }

        if (pluginVersion == null) {
            // ????
            return null;
        }

        plugin.addVersion(pluginVersion);
        return pluginVersion;
    }

    /**
     * Load the graph for the given plugin or create if it is does not already exist
     *
     * @param plugin
     * @param name
     * @return
     */
    public Graph loadGraph(Plugin plugin, String name) {
        Graph graph = plugin.getGraph(name);

        if (graph != null) {
            return graph;
        }

        graph = database.loadGraph(plugin, name);

        if (graph == null) {
            graph = database.createGraph(plugin, name);
        }

        if (graph == null) {
            logger.error("Failed to create graph for " + plugin.getName() + ", \"" + name + "\"");
            return null;
        }

        plugin.addGraph(graph);
        return graph;
    }

    /**
     * Load the server plugin for the given server/plugin combo
     *
     * @param server
     * @param plugin
     * @param version If the server plugin needs to be created, this is the version that will be used initially
     * @return
     */
    public ServerPlugin loadServerPlugin(Server server, Plugin plugin, String version) {
        ServerPlugin serverPlugin = server.getPlugin(plugin);

        // it is already loaded !
        if (serverPlugin != null) {
            return serverPlugin;
        }

        // attempt to load the plugin
        serverPlugin = database.loadServerPlugin(server, plugin);

        if (serverPlugin == null) {
            // we just need to create it
            serverPlugin = database.createServerPlugin(server, plugin, version);
        }

        // now cache it
        server.addPlugin(serverPlugin);
        return serverPlugin;
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

        // Check if the plugin is just a child
        if (plugin.getParent() != -1) {
            // Load the parent
            Plugin parent = loadPlugin(plugin.getParent());

            if (parent != null) {
                // cache the child's plugin name via the parent
                addPlugin(plugin.getName(), parent);
            }

            return parent;
        }

        // Load the versions
        for (PluginVersion version : database.loadPluginVersions(plugin)) {
            plugin.addVersion(version);
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

        // Check if the plugin is just a child
        if (plugin.getParent() != -1) {
            // Load the parent
            Plugin parent = loadPlugin(plugin.getParent());

            if (parent != null) {
                // cache the child's plugin name via the parent
                addPlugin(plugin.getName(), parent);
            }

            return parent;
        }

        // Load the versions
        for (PluginVersion version : database.loadPluginVersions(plugin)) {
            plugin.addVersion(version);
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
     * Get the number of currently open connections
     *
     * @return
     */
    public int countOpenConnections() {
        int conn = 0;

        for (Connector connector : webServer.getConnectors()) {
            conn += connector.getConnectedEndPoints().size();
        }

        return conn;
    }

    /**
     * Create and open the web server
     */
    private void createWebServer() {
        int listenPort = Integer.parseInt(config.getProperty("listen.port"));
        int serverTelemetryPort = Integer.parseInt(config.getProperty("server-telemetry.port"));

        webServer = new org.eclipse.jetty.server.Server();

        String webApp = config.getProperty("webapp.path");
        String contextPath = config.getProperty("webapp.context");

        if (debug) {
            logger.debug("Loading webapp from " + webApp + " at url " + contextPath);
        }

        URL warURL = getClass().getClassLoader().getResource(webApp);
        WebAppContext webAppContext = new WebAppContext(warURL.toExternalForm(), contextPath);

        // Create the handler list
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { handler , webAppContext });

        webServer.setHandler(handlers);

        ServerConnector connector = new ServerConnector(webServer, 1, 1);
        connector.setPort(listenPort);
        connector.setAcceptQueueSize(2048);
        connector.setSoLingerTime(0);
        webServer.addConnector(connector);

        org.eclipse.jetty.server.Server serverTelemetryServer = new org.eclipse.jetty.server.Server();
        serverTelemetryServer.setHandler(new ServerTelemetryHandler());
        ServerConnector connector2 = new ServerConnector(serverTelemetryServer, 1, 1);
        connector2.setPort(serverTelemetryPort);
        connector2.setSoLingerTime(0);
        serverTelemetryServer.addConnector(connector2);

        if (Boolean.parseBoolean(config.getProperty("graphs.generate"))) {
            Scheduler scheduler = new Scheduler();
            scheduler.schedule("*/30 * * * *", new CronGraphGenerator(this));
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

        try {
            // Start the server
            webServer.start();
            serverTelemetryServer.start();
            logger.info("Created plugin telemetry server on port " + listenPort);
            logger.info("Created server telemetry server on port " + serverTelemetryPort);

            // and now join it
            webServer.join();
        } catch (Exception e) {
            logger.error("Failed to create web server");
            e.printStackTrace();
        }
    }

    /**
     * Connect to the database
     */
    private void connectToDatabase() {
        // Create the database
        database = new PluginOnlyMySQLDatabase(this, config.getProperty("mysql.hostname"), config.getProperty("mysql.database"),
                config.getProperty("mysql.username"), config.getProperty("mysql.password"));

        logger.info("Connected to MySQL");
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
        return graphStore;
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
