package org.mcstats;

import it.sauronsoftware.cron4j.Scheduler;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.webapp.WebAppContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.cron.CronGraphGenerator;
import org.mcstats.cron.CronRanking;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.db.MySQLDatabase;
import org.mcstats.handler.BlackholeHandler;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.ExponentialMovingAverage;
import org.mcstats.util.ServerBuildIdentifier;
import redis.clients.jedis.JedisPool;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
     * The time the instance was started
     */
    private long startTime = System.currentTimeMillis();

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
     * Redis connection
     */
    private JedisPool redisPool;

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
    private ReportHandler handler;

    /**
     * The server build identifier
     */
    private final ServerBuildIdentifier serverBuildIdentifier = new ServerBuildIdentifier();

    /**
     * The moving average of requests
     */
    private final ExponentialMovingAverage requestsAverage = new ExponentialMovingAverage(0.25);

    /**
     * Request processing time
     */
    private final ExponentialMovingAverage requestProcessingTimeAverage = new ExponentialMovingAverage(1d / 2000 / 5);

    /**
     * Debug mode
     */
    private boolean debug = false;

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
     * Scheduler thread pool
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private MCStats() {
        // requests count when this.requests was last polled
        final AtomicLong requestsAtLastPoll = new AtomicLong(0);

        scheduler.scheduleAtFixedRate(() -> {
            requestsAverage.update(requests.get() - requestsAtLastPoll.get());
            requestsAtLastPoll.set(requests.get());
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Starts the MCStats backend
     */
    public void init() {
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

        countries.putAll(loadCountries());
        logger.info("Loaded " + countries.size() + " countries");

        graphStore = new MongoDBGraphStore(this);

        GenericObjectPoolConfig redisConfig = new GenericObjectPoolConfig();
        redisConfig.setMaxTotal(32);

        redisPool = new JedisPool(redisConfig, config.getProperty("redis.host"), Integer.parseInt(config.getProperty("redis.port")));

        // Load all of the pluginsByName
        for (Plugin plugin : database.loadPlugins()) {
            if (plugin.getId() >= 0) {
                addPlugin(plugin);
            }
        }

        logger.info("Loaded " + pluginsByName.size() + " plugins");
    }

    /**
     * Gets the time the instance started at
     *
     * @return
     */
    public long getStartTime() {
        return startTime;
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
     * Get an unmodifiable list of the cached plugins
     *
     * @return
     */
    public List<Plugin> getCachedPlugins() {
        return Collections.unmodifiableList(new ArrayList<>(pluginsById.values()));
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

        // Cache it
        addPlugin(plugin);

        // and go !
        return plugin;
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
    public void createWebServer() {
        handler = new ReportHandler(this);

        int listenPort = Integer.parseInt(config.getProperty("listen.port"));
        int blackholePort = Integer.parseInt(config.getProperty("blackhole.port"));
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

        org.eclipse.jetty.server.Server blackholeServer = new org.eclipse.jetty.server.Server();
        blackholeServer.setHandler(new BlackholeHandler());
        ServerConnector connector2 = new ServerConnector(blackholeServer, 1, 1);
        connector2.setPort(blackholePort);
        connector2.setSoLingerTime(0);
        blackholeServer.addConnector(connector2);

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
            blackholeServer.start();
            logger.info("Created web server on port " + listenPort);
            logger.info("Created blackhole server on port " + blackholePort);

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
        database = new MySQLDatabase(this, config.getProperty("mysql.hostname"), config.getProperty("mysql.database"),
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
     * Returns the redis database instance
     *
     * @return
     */
    public JedisPool getRedisPool() {
        return redisPool;
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
     * Get the requests/sec moving average
     *
     * @return
     */
    public ExponentialMovingAverage getRequestsAverage() {
        return requestsAverage;
    }

    /**
     * Gets the request processing time average
     *
     * @return
     */
    public ExponentialMovingAverage getRequestProcessingTimeAverage() {
        return requestProcessingTimeAverage;
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
     * Loads all countries.
     */
    private Map<String, String> loadCountries() {
        Map<String, String> result = new HashMap<>();

        try (InputStream stream = getClass().getResourceAsStream("/countries.json")) {
            JSONArray root = (JSONArray) JSONValue.parse(new InputStreamReader(stream));

            for (Object value : root) {
                JSONObject countryData = (JSONObject) value;

                String shortCode = countryData.get("short").toString();
                String fullName = countryData.get("name").toString();

                // TODO Country object?
                result.put(shortCode, fullName);
            }
        } catch (IOException e) {
            logger.error("Failed to load countries.json", e);
        }

        return result;
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
