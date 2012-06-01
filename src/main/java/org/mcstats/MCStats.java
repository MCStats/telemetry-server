package org.mcstats;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginVersion;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.sql.Database;
import org.mcstats.sql.MySQLDatabase;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class MCStats {

    private Logger logger = Logger.getLogger("MCStats");

    /**
     * The MCStats instance
     */
    private static final MCStats instance = new MCStats();

    /**
     * MCStats configuration
     */
    private Properties config;

    /**
     * The database we are connected to
     */
    private Database database;

    /**
     * The database save queue
     */
    private DatabaseQueue databaseQueue = new DatabaseQueue();

    /**
     * A map of all of the currently loaded servers
     */
    private final Map<String, Server> servers = new ConcurrentHashMap<String, Server>();

    /**
     * A map of all of the currently loaded pluginsByName, by the plugin's name
     */
    private final Map<String, Plugin> pluginsByName = new ConcurrentHashMap<String, Plugin>();

    /**
     * A map of all of the currently loaded pluginsByName, by the plugin's internal id
     */
    private final Map<Integer, Plugin> pluginsById = new ConcurrentHashMap<Integer, Plugin>();

    private MCStats() {
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

        // Connect to the database
        connectToDatabase();

        // Load all of the pluginsByName
        for (Plugin plugin : database.loadPlugins()) {
            addPlugin(plugin);
        }
        logger.info("Loaded " + pluginsByName.size() + " plugins");

        // Create & open the webserver
        createWebServer();
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

        // try to load it from the database
        graph = database.loadGraph(plugin, name);

        // create if it is isn't created yet
        if (graph == null) {
            graph = database.createGraph(plugin, name);
        }

        // none yet ????
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

        // we just need to create it
        serverPlugin = database.createServerPlugin(server, plugin, version);

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
        if (pluginsByName.containsKey(name)) {
            return pluginsByName.get(name);
        }

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
        if (servers.containsKey(guid)) {
            return servers.get(guid);
        }

        // Load from the database
        Server server = database.loadServer(guid);

        if (server == null) {
            server = database.createServer(guid);
        }

        if (server == null) {
            logger.error("Failed to create server for \"" + guid + "\"");
            return null;
        }

        // Now load the plugins
        for (ServerPlugin serverPlugin : database.loadServerPlugins(server)) {
            server.addPlugin(serverPlugin);
        }

        servers.put(guid, server);
        return server;
    }

    /**
     * Create and open the web server
     */
    private void createWebServer() {
        int listenPort = Integer.parseInt(config.getProperty("listen.port"));
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();

        // TODO put these somewhere else :p
        String WEB_APP = "org/mcstats/webapp";
        String CONTEXT_PATH = "/webapp";
        URL warURL = getClass().getClassLoader().getResource(WEB_APP);
        WebAppContext webAppContext = new WebAppContext(warURL.toExternalForm(), CONTEXT_PATH);

        // Create the handler list
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { new ReportHandler(this) , webAppContext });

        server.setHandler(handlers);

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(listenPort);
        connector.setThreadPool(new QueuedThreadPool(50));
        connector.setAcceptors(2);

        // add the connector to the server
        server.addConnector(connector);

        try {
            // Start the server
            server.start();
            logger.info("Created web server on port " + listenPort);

            // and now join it
            server.join();
        } catch (Exception e) {
            logger.error("Failed to create web server");
            e.printStackTrace();
        }
    }

    /**
     * Connect to the database
     */
    private void connectToDatabase() {
        // First load the mysql.properties file
        Properties properties = new Properties();

        try {
            properties.load(new FileInputStream("mysql.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the database
        database = new MySQLDatabase(this, properties.getProperty("mysql.hostname"), properties.getProperty("mysql.database")
                , properties.getProperty("mysql.username"), properties.getProperty("mysql.password"));

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
     * Get the database queue
     *
     * @return
     */
    public DatabaseQueue getDatabaseQueue() {
        return databaseQueue;
    }

    /**
     * Add a plugin to the cache
     *
     * @param plugin
     */
    private void addPlugin(Plugin plugin) {
        pluginsById.put(plugin.getId(), plugin);
        pluginsByName.put(plugin.getName(), plugin);
    }

    /**
     * Get the MCStats instance
     *
     * @return
     */
    public static MCStats getInstance() {
        return instance;
    }

}
