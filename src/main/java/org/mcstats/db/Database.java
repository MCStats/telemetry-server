package org.mcstats.db;

import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginVersion;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface Database {

    /**
     * Execute a raw query
     */
    public void executeUpdate(String query) throws SQLException;

    /**
     * Load all countries from the database
     *
     * @return
     */
    public Map<String, String> loadCountries();

    /**
     * Create a bare plugin with the given name
     *
     * @param name
     * @return
     */
    public Plugin createPlugin(String name);

    /**
     * Load all of the plugins from the database
     *
     * @return
     */
    public List<Plugin> loadPlugins();

    /**
     * Load the plugin from the database with the given ID
     *
     * @param id
     * @return
     */
    public Plugin loadPlugin(int id);

    /**
     * Load the plugin from the database with the given name
     *
     * @param name
     * @return
     */
    public Plugin loadPlugin(String name);

    /**
     * Save the given plugin to the database
     *
     * @param plugin
     */
    public void savePlugin(Plugin plugin);

    /**
     * Create a version for the given plugin and then reload the versions it has
     *
     * @param plugin
     * @param version
     */
    public PluginVersion createPluginVersion(Plugin plugin, String version);

    /**
     * Load a plugin's versions and add them to it
     *
     * @param plugin
     */
    public List<PluginVersion> loadPluginVersions(Plugin plugin);

    /**
     * Load a plugin version
     *
     * @param plugin
     * @param version
     * @return
     */
    public PluginVersion loadPluginVersion(Plugin plugin, String version);

    /**
     * Create a ServerPlugin entry in the database for the server/plugin pair
     *
     * @param server
     * @param plugin
     * @return
     */
    public ServerPlugin createServerPlugin(Server server, Plugin plugin, String version);

    /**
     * Load the ServerPlugin object for the server/plugin pair
     *
     * @param server
     * @param plugin
     * @return
     */
    public ServerPlugin loadServerPlugin(Server server, Plugin plugin);

    /**
     * Load all of the server plugin objects for the given server
     *
     * @param server
     * @return
     */
    public List<ServerPlugin> loadServerPlugins(Server server);

    /**
     * Save the ServerPlugin to the database
     *
     * @param serverPlugin
     */
    public void saveServerPlugin(ServerPlugin serverPlugin);

    /**
     * Add version history for the given server/plugin combo to the database
     *
     * @param server
     * @param version
     */
    public void addPluginVersionHistory(Server server, PluginVersion version);

    /**
     * Create a server in the database using the given serverId
     *
     * @param guid
     * @return
     */
    public Server createServer(String guid);

    /**
     * Load a server from the database with the given serverId
     *
     * @param guid
     * @return
     */
    public Server loadServer(String guid);

    /**
     * Save the given server to the database
     *
     * @param server
     */
    public void saveServer(Server server);

    /**
     * Create a graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    public Graph createGraph(Plugin plugin, String name);

    /**
     * Load the graph for the given plugin
     *
     * @param plugin
     * @param name
     * @return
     */
    public Graph loadGraph(Plugin plugin, String name);

    /**
     * Load all of the graphs for a given plugin
     *
     * @param plugin
     * @return
     */
    public List<Graph> loadGraphs(Plugin plugin);

    /**
     * Create a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    public Column createColumn(Graph graph, String name);

    /**
     * Load a column for the given graph
     *
     * @param graph
     * @param name
     * @return
     */
    public Column loadColumn(Graph graph, String name);

    /**
     * Load all columns for the given graph
     *
     * @param graph
     * @return
     */
    public List<Column> loadColumns(Graph graph);

    /**
     * Blacklist a server
     *
     * @param server
     */
    public void blacklistServer(Server server);

    /**
     * Check if a server is blacklisted.
     *
     * @param server
     * @return
     */
    public boolean isServerBlacklisted(Server server);

}
