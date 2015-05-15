package org.mcstats;

import it.sauronsoftware.cron4j.Scheduler;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.cron.PluginGraphGenerator;
import org.mcstats.cron.PluginRanking;
import org.mcstats.db.Database;
import org.mcstats.db.ModelCache;
import org.mcstats.handler.ReportHandler;
import org.mcstats.handler.StatusHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.ExponentialMovingAverage;
import org.mcstats.util.ServerBuildIdentifier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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
     * The database we are connected to
     */
    @Inject
    private Database database;

    /**
     * The cache used to store models
     */
    @Inject // TODO identify for removal if it's getter-only
    private ModelCache modelCache;

    /**
     * Redis pool
     * TODO replace #getResource(String) with an annotation maybe?
     */
    @Inject
    private JedisPool redisPool;

    /**
     * The database save queue
     */
    @Inject // TODO identify for removal if it's getter-only
    private DatabaseQueue databaseQueue;

    /**
     * The report handler for requests
     */
    @Inject // TODO identify for removal if it's getter-only
    private ReportHandler reportHandler;

    /**
     * The status handler
     */
    @Inject // TODO identify for removal
    private StatusHandler statusHandler;

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
     * A map of all countries, keyed by the 2 letter country code
     */
    private final Map<String, String> countries = new ConcurrentHashMap<>();

    /**
     * Scheduler thread pool
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Server config. TODO - will be moved to dedicated server class.
     */
    private final int listenPort;
    private final boolean generateGraphs;

    @Inject
    public MCStats(@Named("listen.port") int listenPort,
                   @Named("graphs.generate") boolean generateGraphs) {
        instance = this;
        this.listenPort = listenPort;
        this.generateGraphs = generateGraphs;

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
        countries.putAll(loadCountries());
        logger.info("Loaded " + countries.size() + " countries");
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
     * Get the shortcode for a country
     *
     * @param shortCode
     * @return
     */
    public String getCountryName(String shortCode) {
        return countries.get(shortCode);
    }

    /**
     * Increment and return the amount of requests served on the server
     *
     * @return
     */
    public long incrementAndGetRequests() {
        return requests.incrementAndGet();
    }

    /**
     * Retusn the number of requests served on the server
     *
     * @return
     */
    public long getRequestsMade() {
        return requests.get();
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
        webServer = new org.eclipse.jetty.server.Server(new QueuedThreadPool(4));

        // Create the handler list
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{statusHandler, reportHandler});

        webServer.setHandler(handlers);

        ServerConnector connector = new ServerConnector(webServer, 1, 1);
        connector.setPort(listenPort);
        webServer.addConnector(connector);

        if (generateGraphs) {
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
     * Get the database mcstats is connected to
     *
     * @return
     */
    @Deprecated
    public Database getDatabase() {
        return database;
    }

    /**
     * Get the redis pool.
     *
     * @return
     */
    @Deprecated
    public JedisPool getRedisPool() {
        return redisPool;
    }

    /**
     * Get the database queue
     *
     * @return
     */
    @Deprecated
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
