package org.mcstats;

import it.sauronsoftware.cron4j.Scheduler;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.cron.PluginGraphGenerator;
import org.mcstats.cron.PluginRanking;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.ModelCache;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.db.MySQLDatabase;
import org.mcstats.db.RedisCache;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.ExponentialMovingAverage;
import org.mcstats.util.ServerBuildIdentifier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class MCStats {

    private Logger logger = Logger.getLogger("MCStats");

    /**
     * The MCStats instance
     */
    private static MCStats instance;

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
     * The cache used to store models
     */
    @Inject // TODO identify for removal if it's getter-only
    private ModelCache modelCache;

    /**
     * The storage for graph data
     */
    private GraphStore graphStore;

    /**
     * The database save queue
     */
    @Inject // TODO identify for removal if it's getter-only
    private DatabaseQueue databaseQueue;

    /**
     * The report handler for requests
     */
    @Inject // TODO identify for removal if it's getter-only
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
     * A map of all countries, keyed by the 2 letter country code
     */
    private final Map<String, String> countries = new ConcurrentHashMap<>();

    /**
     * Scheduler thread pool
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MCStats() {
        instance = this;

        // requests count when this.requests was last polled
        final AtomicLong requestsAtLastPoll = new AtomicLong(0);

        scheduler.scheduleAtFixedRate(() -> {
            requestsAverage.update(requests.get() - requestsAtLastPoll.get());
            requestsAtLastPoll.set(requests.get());
        }, 1, 1, TimeUnit.SECONDS);

        init();
    }

    /**
     * Starts the MCStats backend
     */
    private void init() {
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

        // Connect to the database
        connectToDatabase();

        countries.putAll(loadCountries());
        logger.info("Loaded " + countries.size() + " countries");

        graphStore = new MongoDBGraphStore(this);

        GenericObjectPoolConfig redisConfig = new GenericObjectPoolConfig();
        redisConfig.setMaxTotal(64);

        redisPool = new JedisPool(redisConfig, config.getProperty("redis.host"), Integer.parseInt(config.getProperty("redis.port")));
        modelCache = new RedisCache(this);
    }

    /**
     * Gets the cache used to store models
     *
     * @return
     */
    public ModelCache getModelCache() {
        return modelCache;
    }

    /**
     * Loads a redis script from the given resource (in the jar file).
     *
     * @param resource
     * @return SHA hash of the script that can be used to execute the script.
     */
    public String loadRedisScript(String resource) {
        String script = "";

        try {
            Path path = Paths.get(getClass().getResource(resource).toURI());

            for (String line : Files.readAllLines(path)) {
                line = line.replaceAll("--.*", "").trim();

                if (!line.isEmpty()) {
                    script += line + " ";
                }
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

        try (Jedis redis = redisPool.getResource()) {
            String sha = redis.scriptLoad(script);

            logger.info("Loaded redis script " + resource + " -> " + sha);

            return sha;
        }
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
        Plugin plugin = modelCache.getPlugin(id);

        if (plugin == null) {
            plugin = database.loadPlugin(id);

            if (plugin != null) {
                modelCache.cachePlugin(plugin);
            }
        }

        if (plugin == null) {
            return null;
        }

        // Check if the plugin is just a child -- if so the parent is returned instead.
        if (plugin.getParent() != -1) {
            return loadPlugin(plugin.getParent());
        }

        return plugin;
    }

    /**
     * Load a plugin and if it does not exist it will be created
     *
     * @param name
     * @return
     */
    public Plugin loadPlugin(String name) {
        Plugin plugin = modelCache.getPlugin(name);

        if (plugin == null) {
            plugin = database.loadPlugin(name);

            if (plugin == null) {
                plugin = database.createPlugin(name);
            }

            if (plugin != null) {
                modelCache.cachePlugin(plugin);
            }
        }

        if (plugin == null) {
            logger.error("Failed to create plugin for \"" + name + "\"");
            return null;
        }

        // Check if the plugin is just a child -- if so the parent is returned instead.
        if (plugin.getParent() != -1) {
            return loadPlugin(plugin.getParent());
        }

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
        webServer = new org.eclipse.jetty.server.Server(new QueuedThreadPool(4));

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
        webServer.addConnector(connector);

        if (Boolean.parseBoolean(config.getProperty("graphs.generate"))) {
            Scheduler scheduler = new Scheduler();
            scheduler.schedule("*/30 * * * *", new PluginGraphGenerator(this));
            scheduler.schedule("45 * * * *", new PluginRanking(this));
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
            webServer.start();
            logger.info("Created web server on port " + listenPort);

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
     * Gets the report handler.
     * TODO only used for executor queue size right now. Something else?
     *
     * @return
     */
    public ReportHandler getReportHandler() {
        return handler;
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
    @Deprecated
    public static MCStats getInstance() {
        return instance;
    }

    public ServerBuildIdentifier getServerBuildIdentifier() {
        return serverBuildIdentifier;
    }
}
